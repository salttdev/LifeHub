package dev.saltt.hub.matchmaking;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.saltt.hub.matchmaking.grpc.InstancerClient;
import dev.saltt.hub.matchmaking.node.NodePool;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.hub.matchmaking.queue.GameQueue;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.life.protocol.GameType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pass that turns queues into matches.
 *
 * <p>One pass per interval: prune anyone who cannot play, drop matches that stopped beating, then
 * for each queue that {@link GameQueue#readiness() says so}, claim a lobby and place it on a node.
 */
public final class Matchmaker {

    private static final Logger LOG = Logger.getLogger(Matchmaker.class.getName());

    private static final long DEFAULT_INTERVAL_MILLIS = 1_000L;

    private final QueueManager queues;
    private final MatchCache matches;
    private final NodePool nodes;
    private final InstancerClient client;
    private final long intervalMillis;

    private ScheduledFuture<?> task;

    public Matchmaker(QueueManager queues, MatchCache matches, NodePool nodes,
                      InstancerClient client) {
        this(queues, matches, nodes, client, DEFAULT_INTERVAL_MILLIS);
    }

    public Matchmaker(QueueManager queues, MatchCache matches, NodePool nodes,
                      InstancerClient client, long intervalMillis) {
        this.queues = queues;
        this.matches = matches;
        this.nodes = nodes;
        this.client = client;
        this.intervalMillis = Math.max(250L, intervalMillis);
    }

    public void start() {
        if (task != null) {
            return;
        }
        // Fixed delay, not fixed rate: two overlapping passes would both read a queue before
        // either claimed out of it, and the same player would end up in two lobbies.
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(
                this::safeTick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        LOG.info("[Matchmaker] running every " + intervalMillis + "ms");
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
            // An escaping exception cancels a scheduled task permanently, so the queues would
            // silently stop working with nothing further in the log.
            LOG.log(Level.SEVERE, "[Matchmaker] pass failed", t);
        }
    }

    private void tick() {
        for (LiveMatch reaped : matches.reapStale()) {
            LOG.info("[Matchmaker] released " + reaped.roster().size() + " player(s) from stale "
                    + "match " + reaped.matchId());
        }

        for (GameQueue queue : queues.all()) {
            prune(queue);

            if (queue.readiness() != GameQueue.Readiness.READY) {
                continue;
            }
            dispatch(queue);
        }
    }

    /**
     * Takes out anyone who left the hub or is already in a match. Both would otherwise be pulled
     * into a lobby they will never turn up for, which the instancer then waits out.
     */
    private void prune(GameQueue queue) {
        for (QueuedPlayer player : queue.snapshot()) {
            if (Universe.get().getPlayer(player.uuid()) == null) {
                queues.removeFromAll(player.uuid());
                continue;
            }
            if (matches.isInMatch(player.uuid())) {
                queues.removeFromAll(player.uuid());
                message(player.uuid(), "Taken out of the queue: you are already in a match.");
            }
        }
    }

    /**
     * Claims a lobby and walks the nodes with capacity until one takes it. The claim is what stops
     * a second pass reusing these players, so every path out of here either hands them to a node
     * or puts them back.
     */
    private void dispatch(GameQueue queue) {
        QueueRules rules = queue.rules();
        List<QueuedPlayer> lobby = queue.claim(rules.maxPlayers());

        if (lobby.size() < rules.minPlayers()) {
            // Players left between the readiness check and the claim.
            lobby.forEach(queue::add);
            return;
        }

        List<UUID> claimed = lobby.stream().map(QueuedPlayer::uuid).toList();
        matches.reserve(claimed);

        List<InstancerNode> candidates = nodes.withCapacity();
        if (candidates.isEmpty()) {
            LOG.warning("[Matchmaker] cannot place a " + queue.gameType() + " lobby: "
                    + nodes.describeExhaustion());
            restore(queue, lobby, "All game servers are full right now. You're still in the queue.");
            matches.releaseReservation(claimed);
            return;
        }

        // The instancer parses match_id as a UUID, so the hub picks one it will accept and can
        // track the match by even if the answer is lost in flight.
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
        client.setupMatch(node, matchId, gameType, lobby, "", rules.minPlayers())
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
        // The match itself now holds them, so the reservation has done its job.
        releaseReservation(lobby);

        LOG.info("[Matchmaker] " + gameType + " match " + matchId + " placed on node " + node.id()
                + " with " + lobby.size() + " player(s), " + matches.countOnNode(node.id())
                + "/" + node.maxMatches() + " there");

        for (QueuedPlayer player : lobby) {
            // They are leaving the hub, and a player queued elsewhere who is not here can still be
            // pulled into a lobby they will never turn up for.
            queues.removeFromAll(player.uuid());
            refer(player.uuid(), node);
        }
    }

    /** Puts a lobby back at its original queue times and tells them why. */
    private void restore(GameQueue queue, List<QueuedPlayer> lobby, String text) {
        for (QueuedPlayer player : lobby) {
            if (Universe.get().getPlayer(player.uuid()) == null) {
                continue;
            }
            queue.add(player);
            message(player.uuid(), text);
        }
    }

    private void releaseReservation(List<QueuedPlayer> lobby) {
        matches.releaseReservation(lobby.stream().map(QueuedPlayer::uuid).toList());
    }

    private static void refer(UUID uuid, InstancerNode node) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref == null) {
            LOG.warning("[Matchmaker] " + uuid + " went offline before being sent to " + node.id());
            return;
        }
        ref.sendMessage(Message.raw("Match found. Sending you to the game..."));
        ref.referToServer(node.playerHost(), node.playerPort());
    }

    private static Throwable rootOf(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
    }

    private static void message(UUID uuid, String text) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null) {
            ref.sendMessage(Message.raw(text));
        }
    }
}
