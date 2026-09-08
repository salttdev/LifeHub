package dev.saltt.hub.matchmaking;

import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;
import dev.saltt.hub.HubConfig;
import dev.saltt.hub.database.repos.PlayerRegionLatencyRepository;
import dev.saltt.hub.matchmaking.grpc.InstancerClient;
import dev.saltt.hub.matchmaking.node.NodePool;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.hub.matchmaking.region.GeoIpService;
import dev.saltt.hub.matchmaking.region.LatencyModel;
import dev.saltt.hub.matchmaking.region.RegionSelector;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchResult;
import dev.saltt.life.protocol.MatchStatus;
import dev.saltt.life.protocol.PlayerResult;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What the rest of the hub talks to: the commands, the player events and the MatchmakerService
 * rpcs all go through here rather than reaching into the queues or the cache directly.
 */
public final class MatchmakingService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MatchmakingService.class.getName());

    public enum JoinResult { QUEUED, ALREADY_QUEUED, ALREADY_IN_MATCH, UNSUPPORTED_GAME_TYPE }

    public enum RejoinResult { SENT, NOT_IN_MATCH, MATCH_OVER, NODE_UNKNOWN }

    private final Config<HubConfig> config;
    private final QueueManager queues;
    private final MatchCache matches;
    private final NodePool nodes;
    private final InstancerClient client;
    private final HubPlayers players;
    private final LatencyModel latency;
    private final Tickets tickets;
    private final Matchmaker matchmaker;
    @Nullable
    private final GeoIpService geoIp;
    @Nullable
    private final PlayerRegionLatencyRepository latencyRepo;
    private final ExecutorService dbExecutor;

    public MatchmakingService(Config<HubConfig> config, @Nullable GeoIpService geoIp,
                              @Nullable PlayerRegionLatencyRepository latencyRepo) {
        HubConfig cfg = config.get();
        this.config = config;
        this.geoIp = geoIp;
        this.latencyRepo = latencyRepo;
        this.queues = new QueueManager(config::get);
        this.matches = new MatchCache(cfg.getMatchStaleAfterSeconds() * 1_000L);
        this.nodes = new NodePool(() -> config.get().getInstancerNodes(), matches);
        this.client = new InstancerClient(cfg.getSetupMatchDeadlineSeconds());
        this.players = new EnginePlayers();
        this.latency = new LatencyModel(() -> new LatencyModel.Settings(
                config.get().getRegions(), config.get().getLatencyBaseMillis(),
                config.get().getLatencyMillisPerKm()));
        this.tickets = new Tickets(() -> config.get().getApiToken());
        this.matchmaker = new Matchmaker(queues, matches, nodes, client, players, latency,
                new RegionSelector(latency), tickets, config::get);
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LifeHub-LatencyDB");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        matchmaker.start();
        LOG.info("[Matchmaking] " + nodes.nodes().size() + " instancer node(s), regions "
                + config.get().getRegions());
    }

    public QueueManager queues() { return queues; }
    public MatchCache matches() { return matches; }
    public NodePool nodes() { return nodes; }
    public LatencyModel latency() { return latency; }

    // ----- Queueing -----

    public JoinResult join(PlayerRef player, GameType gameType) {
        if (!QueueManager.playable().contains(gameType)) {
            return JoinResult.UNSUPPORTED_GAME_TYPE;
        }
        if (matches.isInMatch(player.getUuid())) {
            return JoinResult.ALREADY_IN_MATCH;
        }
        samplePing(player).ifPresent(rtt -> recordPing(player.getUuid(), config.get().getHubRegion(), rtt));
        if (!queues.add(gameType, player.getUuid(), player.getUsername())) {
            return JoinResult.ALREADY_QUEUED;
        }
        return JoinResult.QUEUED;
    }

    public boolean leave(UUID player, GameType gameType) {
        return queues.remove(gameType, player);
    }

    public boolean leaveAll(UUID player) {
        return queues.removeFromAll(player);
    }

    // ----- Getting to and from a match -----

    /** Sends a player back to the match that still holds them. */
    public RejoinResult rejoin(UUID player) {
        LiveMatch match = matches.findByPlayer(player).orElse(null);
        if (match == null) {
            return RejoinResult.NOT_IN_MATCH;
        }
        if (!match.isJoinable()) {
            return RejoinResult.MATCH_OVER;
        }
        Optional<InstancerNode> node = nodeOf(match);
        if (node.isEmpty()) {
            return RejoinResult.NODE_UNKNOWN;
        }
        queues.removeFromAll(player);
        return matchmaker.send(player, match.matchId(), node.get())
                ? RejoinResult.SENT : RejoinResult.NOT_IN_MATCH;
    }

    /** Gives up the player's place in their match, so they can queue again at once. */
    public CompletableFuture<Boolean> forfeit(UUID player) {
        LiveMatch match = matches.findByPlayer(player).orElse(null);
        if (match == null) {
            return CompletableFuture.completedFuture(false);
        }
        Optional<InstancerNode> node = nodeOf(match);
        if (node.isEmpty()) {
            matches.removePlayer(player);
            return CompletableFuture.completedFuture(true);
        }
        return client.forfeitPlayer(node.get(), match.matchId(), player)
                .exceptionally(error -> {
                    LOG.log(Level.WARNING, "ForfeitPlayer failed for " + player + " on match "
                            + match.matchId(), error);
                    return false;
                })
                .thenApply(ok -> {
                    // The node has let them go, or it is not answering; either way they are not
                    // playing, and the next heartbeat corrects the cache if the node disagrees.
                    matches.removePlayer(player);
                    return ok;
                });
    }

    public CompletableFuture<Boolean> spectate(UUID spectator, String matchId) {
        LiveMatch match = matches.getOrNull(matchId);
        if (match == null || !match.isJoinable()) {
            players.message(spectator, "That match has already ended.");
            return CompletableFuture.completedFuture(false);
        }
        Optional<InstancerNode> node = nodeOf(match);
        if (node.isEmpty()) {
            players.message(spectator, "That match cannot be spectated right now.");
            return CompletableFuture.completedFuture(false);
        }

        return client.spectatorReserve(node.get(), spectator, matchId)
                .thenApply(reserved -> {
                    LiveMatch current = matches.getOrNull(matchId);
                    if (current == null || !current.isJoinable()) {
                        players.message(spectator, "That match ended before you got there.");
                        return false;
                    }
                    if (!Boolean.TRUE.equals(reserved)) {
                        players.message(spectator, "Couldn't get you a spectator slot.");
                        return false;
                    }
                    queues.removeFromAll(spectator);
                    byte[] ticket = tickets.issue(matchId, spectator, node.get().id(), true);
                    return players.refer(spectator, node.get(), ticket);
                })
                .exceptionally(error -> {
                    LOG.log(Level.WARNING, "SpectatorReserve failed for match " + matchId, error);
                    players.message(spectator, "Couldn't get you a spectator slot.");
                    return false;
                });
    }

    private Optional<InstancerNode> nodeOf(LiveMatch match) {
        if (match.nodeId() == null) {
            return Optional.empty();
        }
        Optional<InstancerNode> node = nodes.byId(match.nodeId());
        if (node.isEmpty()) {
            LOG.warning("match " + match.matchId() + " names node " + match.nodeId()
                    + ", which is not in the config");
        }
        return node;
    }

    // ----- Hub player events -----

    public void onPlayerReady(PlayerRef player, String ip) {
        UUID uuid = player.getUuid();
        if (geoIp != null) {
            geoIp.locate(ip).ifPresent(at -> latency.setLocation(uuid, at));
        }
        if (latencyRepo != null) {
            dbExecutor.execute(() -> {
                try {
                    latency.setMeasured(uuid, latencyRepo.load(uuid));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "could not load latency samples for " + uuid, e);
                }
            });
        }
        if (matches.isInMatch(uuid)) {
            players.message(uuid, "You're still in a match. /rejoin to get back in, "
                    + "or /forfeit to give it up.");
        }
    }

    public void onPlayerDisconnect(UUID player) {
        queues.removeFromAll(player);
        latency.forget(player);
    }

    // ----- Instancer reports -----

    public LiveMatch onHeartbeat(String matchId, GameType gameType, MatchStatus status,
                                 Set<UUID> connected, Set<UUID> claimed, int aliveCount, String nodeId) {
        LiveMatch match = matches.onHeartbeat(matchId, gameType, status, connected, claimed,
                aliveCount, nodeId);
        matchmaker.markArrived(connected);
        // A player the instancer holds must not be pulled into a second lobby by the next pass.
        match.roster().forEach(queues::removeFromAll);
        return match;
    }

    /** Drops the match and keeps the pings the node measured. Null if the hub never knew it. */
    @Nullable
    public LiveMatch onMatchFinished(MatchResult result) {
        LiveMatch finished = matches.remove(result.getMatchId()).orElse(null);
        String nodeId = !result.getNodeId().isBlank() ? result.getNodeId()
                : finished == null ? null : finished.nodeId();
        String region = nodeId == null ? null
                : nodes.byId(nodeId).map(InstancerNode::region).orElse(null);
        if (region != null && !region.isBlank()) {
            for (PlayerResult player : result.getPlayersList()) {
                if (player.getPingMillis() > 0) {
                    recordPing(UUID.fromString(player.getPlayerUuid()), region, player.getPingMillis());
                }
            }
        }
        return finished;
    }

    private void recordPing(UUID player, String region, int rttMillis) {
        latency.setMeasured(player, region, rttMillis);
        if (latencyRepo != null) {
            dbExecutor.execute(() -> {
                try {
                    latencyRepo.record(player, region, rttMillis);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "could not store a latency sample for " + player, e);
                }
            });
        }
    }

    /** The player's round trip to this hub in millis, from the engine's own ping history. */
    static Optional<Integer> samplePing(PlayerRef player) {
        try {
            PacketHandler.PingInfo info = player.getPacketHandler().getPingInfo(PongType.Direct);
            var metric = info.getPingMetricSet();
            double micros = metric.getAverage(PacketHandler.PingInfo.ONE_MINUTE_INDEX);
            if (micros <= 0) {
                micros = metric.getLastValue();
            }
            return micros <= 0 ? Optional.empty() : Optional.of((int) Math.round(micros / 1_000.0));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        matchmaker.stop();
        client.close();
        queues.clearAll();
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
