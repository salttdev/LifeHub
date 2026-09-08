package dev.saltt.hub.matchmaking;

import com.hypixel.hytale.server.core.HytaleServer;
import dev.saltt.hub.HubConfig;
import dev.saltt.hub.matchmaking.grpc.NodeClient;
import dev.saltt.hub.matchmaking.node.NodePool;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.hub.matchmaking.queue.GameQueue;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.hub.matchmaking.region.LatencyModel;
import dev.saltt.hub.matchmaking.region.RegionSelector;
import dev.saltt.life.protocol.GameType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pass that turns queues into matches.
 *
 * <p>One pass per interval: drop matches that stopped beating, follow up on players who were sent
 * to a node, prune anyone who cannot play, then for each queue that is ready claim a lobby, pick a
 * region for it and place it on a node there.
 */
public final class Matchmaker {

    private static final Logger LOG = Logger.getLogger(Matchmaker.class.getName());

    private record Dispatch(String matchId, String nodeId, long atMillis) {}

    private final QueueManager queues;
    private final MatchCache matches;
    private final NodePool nodes;
    private final NodeClient client;
    private final HubPlayers players;
    private final LatencyModel latency;
    private final RegionSelector regions;
    private final Tickets tickets;
    private final Supplier<HubConfig> config;

    /** Players referred to a node whose arrival no heartbeat has confirmed yet. */
    private final Map<UUID, Dispatch> dispatches = new ConcurrentHashMap<>();

    private ScheduledFuture<?> task;

    public Matchmaker(QueueManager queues, MatchCache matches, NodePool nodes, NodeClient client,
                      HubPlayers players, LatencyModel latency, RegionSelector regions,
                      Tickets tickets, Supplier<HubConfig> config) {
        this.queues = queues;
        this.matches = matches;
        this.nodes = nodes;
        this.client = client;
        this.players = players;
        this.latency = latency;
        this.regions = regions;
        this.tickets = tickets;
        this.config = config;
    }

    public void start() {
        if (task != null) {
            return;
        }
        long interval = Math.max(250L, config.get().getMatchmakerIntervalMillis());
        // Fixed delay, not fixed rate: two overlapping passes would both read a queue before
        // either claimed out of it, and the same player would end up in two lobbies.
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::safeTick, interval, interval, TimeUnit.MILLISECONDS);
        LOG.info("[Matchmaker] running every " + interval + "ms");
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            // An escaping exception cancels a scheduled task permanently.
            LOG.log(Level.SEVERE, "[Matchmaker] pass failed", t);
        }
    }

    void tick() {
        for (LiveMatch reaped : matches.reapStale()) {
            reaped.roster().forEach(dispatches::remove);
            LOG.info("[Matchmaker] released " + reaped.roster().size() + " player(s) from stale "
                    + "match " + reaped.matchId());
        }

        followUpDispatches();

        for (GameQueue queue : queues.all()) {
            prune(queue);
            if (queue.readiness() == GameQueue.Readiness.READY) {
                dispatch(queue);
            }
        }
    }

    /** A heartbeat listed these players as connected, so they made it. */
    public void markArrived(Collection<UUID> connected) {
        connected.forEach(dispatches::remove);
    }

    private void trackDispatch(UUID player, String matchId, String nodeId) {
        dispatches.put(player, new Dispatch(matchId, nodeId, System.currentTimeMillis()));
    }

    /**
     * Anyone still on the hub well after being referred never left. If that is the whole lobby the
     * match is cancelled; otherwise they are told how to get there or give the match up.
     */
    private void followUpDispatches() {
        long cutoff = System.currentTimeMillis() - config.get().getReferTimeoutSeconds() * 1_000L;
        Map<String, Set<UUID>> stuckByMatch = new LinkedHashMap<>();
        Map<String, String> nodeByMatch = new LinkedHashMap<>();

        for (Map.Entry<UUID, Dispatch> entry : dispatches.entrySet()) {
            Dispatch dispatch = entry.getValue();
            if (dispatch.atMillis() > cutoff) {
                continue;
            }
            dispatches.remove(entry.getKey(), dispatch);
            if (players.isOnline(entry.getKey())) {
                stuckByMatch.computeIfAbsent(dispatch.matchId(), id -> new LinkedHashSet<>())
                        .add(entry.getKey());
                nodeByMatch.put(dispatch.matchId(), dispatch.nodeId());
            }
        }

        for (Map.Entry<String, Set<UUID>> entry : stuckByMatch.entrySet()) {
            LiveMatch match = matches.getOrNull(entry.getKey());
            if (match == null) {
                continue;
            }
            Set<UUID> stuck = entry.getValue();
            if (stuck.containsAll(match.roster())) {
                cancel(match, nodeByMatch.get(entry.getKey()), "nobody left the hub");
                continue;
            }
            for (UUID player : stuck) {
                players.message(player, "You didn't make it to your match. /rejoin to try again "
                        + "or /forfeit to give it up.");
            }
        }
    }

    private void cancel(LiveMatch match, String nodeId, String reason) {
        matches.remove(match.matchId());
        match.roster().forEach(player -> players.message(player,
                "Couldn't get you into the game. You're free to queue again."));
        nodes.byId(nodeId).ifPresentOrElse(
                node -> client.cancelMatch(node, match.matchId(), reason).whenComplete((ok, error) -> {
                    if (error != null || !Boolean.TRUE.equals(ok)) {
                        LOG.warning("[Matchmaker] cancel of match " + match.matchId() + " on "
                                + nodeId + " was not confirmed" + (error == null ? "" : ": " + error));
                    }
                }),
                () -> LOG.warning("[Matchmaker] cannot cancel match " + match.matchId()
                        + ": node " + nodeId + " is not in the config"));
        LOG.info("[Matchmaker] cancelled match " + match.matchId() + ": " + reason);
    }

    /** Takes out anyone who left the hub or is already in a match. */
    private void prune(GameQueue queue) {
        for (QueuedPlayer player : queue.snapshot()) {
            if (!players.isOnline(player.uuid())) {
                queues.removeFromAll(player.uuid());
            } else if (matches.isInMatch(player.uuid())) {
                queues.removeFromAll(player.uuid());
                players.message(player.uuid(), "Taken out of the queue: you are already in a match.");
            }
        }
    }

    /**
     * Claims a lobby, ranks the regions for it and walks the nodes with capacity until one takes
     * it. The claim is what stops a second pass reusing these players, so every path out of here
     * either hands them to a node or puts them back.
     */
    private void dispatch(GameQueue queue) {
        QueueRules rules = queue.rules();
        HubConfig cfg = config.get();

        int depth = cfg.getGroupingDepth();
        List<QueuedPlayer> lobby = depth > 0 && queue.size() >= depth
                ? queue.claimGrouped(rules.maxPlayers(), rules.minPlayers(),
                        player -> latency.bestRegion(player).orElse(null))
                : queue.claim(rules.maxPlayers());

        if (lobby.size() < rules.minPlayers()) {
            lobby.forEach(queue::add);
            return;
        }

        List<UUID> claimed = lobby.stream().map(QueuedPlayer::uuid).toList();
        matches.reserve(claimed);

        List<String> ranked = regions.rank(claimed);
        List<InstancerNode> candidates = nodes.withCapacity(ranked, cfg.isAllowCrossRegionFallback());
        if (candidates.isEmpty()) {
            LOG.warning("[Matchmaker] cannot place a " + queue.gameType() + " lobby (regions "
                    + ranked + "): " + nodes.describeExhaustion());
            restore(queue, lobby, "All game servers are full right now. You're still in the queue.");
            matches.releaseReservation(claimed);
            return;
        }

        String matchId = UUID.randomUUID().toString();
        attempt(queue, lobby, candidates, 0, matchId, rules);
    }

    private void attempt(GameQueue queue, List<QueuedPlayer> lobby, List<InstancerNode> candidates,
                         int index, String matchId, QueueRules rules) {
        if (index >= candidates.size()) {
            restore(queue, lobby, "Couldn't start the match. You're back in the queue.");
            releaseReservation(lobby);
            return;
        }
        InstancerNode node = candidates.get(index);
        GameType gameType = queue.gameType();

        // Blank map id: the instancer picks a map that seats this lobby from its own map configs.
        client.setupMatch(node, matchId, gameType, lobby, "", rules.minPlayers(), node.region())
                .whenComplete((accepted, error) -> {
                    if (error != null) {
                        LOG.log(Level.WARNING, "[Matchmaker] node " + node.id() + " could not take "
                                + gameType + " match " + matchId + ", trying the next node",
                                rootOf(error));
                        attempt(queue, lobby, candidates, index + 1, matchId, rules);
                        return;
                    }
                    onAccepted(node, accepted, gameType, lobby);
                });
    }

    private void onAccepted(InstancerNode node, String matchId, GameType gameType,
                            List<QueuedPlayer> lobby) {
        Set<UUID> roster = new LinkedHashSet<>();
        lobby.forEach(player -> roster.add(player.uuid()));

        matches.add(LiveMatch.dispatched(matchId, gameType, node.id(), roster));
        releaseReservation(lobby);

        LOG.info("[Matchmaker] " + gameType + " match " + matchId + " placed on node " + node.id()
                + " (" + node.region() + ") with " + lobby.size() + " player(s), "
                + nodes.load(node) + "/" + node.maxMatches() + " there");

        for (QueuedPlayer player : lobby) {
            queues.removeFromAll(player.uuid());
            send(player.uuid(), matchId, node);
        }
    }

    /** Refers a player to the node that holds their match and starts the arrival clock. */
    public boolean send(UUID player, String matchId, InstancerNode node) {
        players.message(player, "Sending you to the game (" + node.region() + ")...");
        byte[] ticket = tickets.issue(matchId, player, node.id(), false);
        if (!players.refer(player, node, ticket)) {
            LOG.warning("[Matchmaker] " + player + " went offline before being sent to " + node.id());
            return false;
        }
        trackDispatch(player, matchId, node.id());
        return true;
    }

    private void restore(GameQueue queue, List<QueuedPlayer> lobby, String text) {
        for (QueuedPlayer player : lobby) {
            if (players.isOnline(player.uuid())) {
                queue.add(player);
                players.message(player.uuid(), text);
            }
        }
    }

    private void releaseReservation(List<QueuedPlayer> lobby) {
        matches.releaseReservation(lobby.stream().map(QueuedPlayer::uuid).toList());
    }

    private static Throwable rootOf(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
    }

    List<UUID> pendingDispatches() {
        return new ArrayList<>(dispatches.keySet());
    }
}
