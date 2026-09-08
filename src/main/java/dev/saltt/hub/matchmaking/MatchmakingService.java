package dev.saltt.hub.matchmaking;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import dev.saltt.hub.HubConfig;
import dev.saltt.hub.matchmaking.grpc.InstancerClient;
import dev.saltt.hub.matchmaking.node.NodePool;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.life.protocol.GameType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What the rest of the hub talks to: the commands, the player events and the MatchmakerService
 * rpcs all go through here rather than reaching into the queues or the cache directly.
 */
public final class MatchmakingService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MatchmakingService.class.getName());

    private final QueueManager queues;
    private final MatchCache matches;
    private final NodePool nodes;
    private final InstancerClient client;
    private final Matchmaker matchmaker;

    public MatchmakingService(Config<HubConfig> config) {
        HubConfig cfg = config.get();
        this.queues = new QueueManager(config);
        this.matches = new MatchCache(cfg.getMatchStaleAfterSeconds() * 1_000L);
        this.nodes = new NodePool(config, matches);
        this.client = new InstancerClient(cfg.getSetupMatchDeadlineSeconds());
        this.matchmaker = new Matchmaker(queues, matches, nodes, client,
                cfg.getMatchmakerIntervalMillis());
    }

    public void start() {
        matchmaker.start();
        LOG.info("[Matchmaking] " + nodes.nodes().size() + " instancer node(s) configured");
    }

    public QueueManager queues() {
        return queues;
    }

    public MatchCache matches() {
        return matches;
    }

    public NodePool nodes() {
        return nodes;
    }

    // ----- Queueing -----

    /** Why a join was refused, or that it was taken. */
    public enum JoinResult {
        QUEUED,
        ALREADY_QUEUED,
        ALREADY_IN_MATCH,
        UNSUPPORTED_GAME_TYPE
    }

    public JoinResult join(PlayerRef player, GameType gameType) {
        if (!QueueManager.playable().contains(gameType)) {
            return JoinResult.UNSUPPORTED_GAME_TYPE;
        }
        if (matches.isInMatch(player.getUuid())) {
            return JoinResult.ALREADY_IN_MATCH;
        }
        if (!queues.add(gameType, player.getUuid(), player.getUsername())) {
            return JoinResult.ALREADY_QUEUED;
        }
        return JoinResult.QUEUED;
    }

    /** True when they were actually in that queue. */
    public boolean leave(UUID player, GameType gameType) {
        return queues.remove(gameType, player);
    }

    public boolean leaveAll(UUID player) {
        return queues.removeFromAll(player);
    }

    // ----- Spectating -----

    /**
     * Reserves a slot on the node running the match and sends the player there. The node comes
     * from the match, not from the spectator.
     */
    public CompletableFuture<Boolean> spectate(UUID spectator, String matchId) {
        LiveMatch match = matches.getOrNull(matchId);
        if (match == null || !match.isWatchable()) {
            message(spectator, "That match has already ended.");
            return CompletableFuture.completedFuture(false);
        }
        if (match.nodeId() == null) {
            // Adopted from a heartbeat, so the hub does not know where it is running.
            message(spectator, "That match cannot be spectated right now.");
            return CompletableFuture.completedFuture(false);
        }

        Optional<InstancerNode> node = nodes.byId(match.nodeId());
        if (node.isEmpty()) {
            LOG.warning("match " + matchId + " names node " + match.nodeId()
                    + ", which is no longer in the config");
            message(spectator, "That match cannot be spectated right now.");
            return CompletableFuture.completedFuture(false);
        }

        return client.spectatorReserve(node.get(), spectator, matchId)
                .thenApply(reserved -> {
                    // The match can end while the reserve is in flight, which would drop them onto
                    // a server with nothing to watch.
                    LiveMatch current = matches.getOrNull(matchId);
                    if (current == null || !current.isWatchable()) {
                        message(spectator, "That match ended before you got there.");
                        return false;
                    }
                    if (!Boolean.TRUE.equals(reserved)) {
                        message(spectator, "Couldn't get you a spectator slot.");
                        return false;
                    }

                    queues.removeFromAll(spectator);
                    PlayerRef ref = Universe.get().getPlayer(spectator);
                    if (ref == null) {
                        return false;
                    }
                    ref.referToServer(node.get().playerHost(), node.get().playerPort());
                    return true;
                })
                .exceptionally(error -> {
                    LOG.log(Level.WARNING, "SpectatorReserve failed for match " + matchId, error);
                    message(spectator, "Couldn't get you a spectator slot.");
                    return false;
                });
    }

    // ----- Hub player events -----

    /**
     * A player is on the hub, so whatever match the cache still holds them in is over for them.
     * The last safety net: MatchFinished normally clears the match first.
     */
    public void onPlayerReady(UUID player) {
        matches.releasePlayer(player);
    }

    public void onPlayerDisconnect(UUID player) {
        queues.removeFromAll(player);
    }

    private static void message(UUID uuid, String text) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null) {
            ref.sendMessage(Message.raw(text));
        }
    }

    @Override
    public void close() {
        matchmaker.stop();
        client.close();
        queues.clearAll();
    }
}
