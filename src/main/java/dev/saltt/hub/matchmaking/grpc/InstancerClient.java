package dev.saltt.hub.matchmaking.grpc;

import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.InstancerServiceGrpc;
import dev.saltt.life.protocol.MatchPlayer;
import dev.saltt.life.protocol.SetupMatchRequest;
import dev.saltt.life.protocol.SetupMatchResponse;
import dev.saltt.life.protocol.SpectatorReserveRequest;
import dev.saltt.life.protocol.SpectatorReserveResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Outgoing InstancerService calls, one reused channel per node. */
public final class InstancerClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(InstancerClient.class.getName());

    private static final long DEFAULT_DEADLINE_SECONDS = 10L;

    /** The node answered but said no. Distinguishes a refusal from an unreachable host. */
    public static final class RefusedException extends RuntimeException {
        public RefusedException(String message) {
            super(message);
        }
    }

    private record Endpoint(String host, int port, ManagedChannel channel) { }

    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();
    private final long deadlineSeconds;

    public InstancerClient() {
        this(DEFAULT_DEADLINE_SECONDS);
    }

    /**
     * @param deadlineSeconds SetupMatch builds a world before it answers, so this has to cover a
     *                        world load, not just a round trip.
     */
    public InstancerClient(long deadlineSeconds) {
        this.deadlineSeconds = Math.max(1L, deadlineSeconds);
    }

    /**
     * Completes with the match id the node accepted, or exceptionally on a refusal, a deadline or
     * a transport failure. The id is generated here and sent, so the hub can track the match even
     * if the answer is lost in flight.
     */
    public CompletableFuture<String> setupMatch(InstancerNode node, String matchId,
                                                GameType gameType, List<QueuedPlayer> players,
                                                String mapId, int minimumStartingPlayers) {
        CompletableFuture<String> result = new CompletableFuture<>();
        String label = gameType + " match " + matchId + " with " + players.size()
                + " player(s) on node " + node.id();

        SetupMatchRequest.Builder request = SetupMatchRequest.newBuilder()
                .setMatchId(matchId)
                .setGameType(gameType)
                .setMapId(mapId == null ? "" : mapId)
                .setMinimumStartingPlayers(minimumStartingPlayers);

        for (QueuedPlayer player : players) {
            request.addPlayers(MatchPlayer.newBuilder()
                    .setPlayerUuid(player.uuid().toString())
                    .setPlayerName(player.userName() == null ? "" : player.userName())
                    .build());
        }

        long startedAt = System.currentTimeMillis();
        try {
            stub(node).setupMatch(request.build(), new StreamObserver<SetupMatchResponse>() {
                @Override
                public void onNext(SetupMatchResponse response) {
                    long took = System.currentTimeMillis() - startedAt;
                    if (response.getSuccess() && !response.getMatchId().isEmpty()) {
                        LOG.info("SetupMatch accepted in " + took + "ms: " + label);
                        result.complete(response.getMatchId());
                        return;
                    }
                    LOG.warning("SetupMatch declined in " + took + "ms for " + label
                            + " (error='" + response.getError() + "')");
                    result.completeExceptionally(new RefusedException(
                            response.getError().isEmpty()
                                    ? node.id() + " declined the match"
                                    : node.id() + ": " + response.getError()));
                }

                @Override
                public void onError(Throwable error) {
                    Status status = Status.fromThrowable(error);
                    LOG.log(Level.WARNING, "SetupMatch failed after "
                            + (System.currentTimeMillis() - startedAt) + "ms for " + label
                            + " -> " + node.grpcHost() + ":" + node.grpcPort()
                            + " [" + status.getCode() + "] " + status.getDescription(), error);
                    result.completeExceptionally(error);
                }

                @Override
                public void onCompleted() {
                    if (!result.isDone()) {
                        LOG.warning("SetupMatch closed with no response for " + label);
                        result.completeExceptionally(
                                new RefusedException(node.id() + " closed without a response"));
                    }
                }
            });
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "SetupMatch could not be dispatched for " + label, t);
            result.completeExceptionally(t);
        }
        return result;
    }

    public CompletableFuture<Boolean> spectatorReserve(InstancerNode node, UUID spectator,
                                                       String matchId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        SpectatorReserveRequest request = SpectatorReserveRequest.newBuilder()
                .setPlayerUuid(spectator.toString())
                .setMatchId(matchId)
                .build();

        try {
            stub(node).spectatorReserve(request, new StreamObserver<SpectatorReserveResponse>() {
                @Override
                public void onNext(SpectatorReserveResponse response) {
                    result.complete(response.getSuccess());
                }

                @Override
                public void onError(Throwable error) {
                    Status status = Status.fromThrowable(error);
                    LOG.log(Level.WARNING, "SpectatorReserve failed for " + spectator + " on match "
                            + matchId + " [" + status.getCode() + "] " + status.getDescription(),
                            error);
                    result.completeExceptionally(error);
                }

                @Override
                public void onCompleted() {
                    if (!result.isDone()) {
                        LOG.warning("SpectatorReserve closed with no response for " + spectator);
                        result.complete(false);
                    }
                }
            });
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "SpectatorReserve could not be dispatched for " + spectator, t);
            result.completeExceptionally(t);
        }
        return result;
    }

    private InstancerServiceGrpc.InstancerServiceStub stub(InstancerNode node) {
        return InstancerServiceGrpc.newStub(channel(node))
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
    }

    /** Reopens if the node's address changed in config, since HubConfig is reloadable. */
    private ManagedChannel channel(InstancerNode node) {
        Endpoint endpoint = endpoints.compute(node.id(), (id, current) -> {
            if (current != null
                    && current.host().equals(node.grpcHost())
                    && current.port() == node.grpcPort()
                    && !current.channel().isShutdown()) {
                return current;
            }
            if (current != null) {
                LOG.info("reopening the channel to node " + id + ": "
                        + current.host() + ":" + current.port()
                        + " -> " + node.grpcHost() + ":" + node.grpcPort());
                current.channel().shutdown();
            }
            return new Endpoint(node.grpcHost(), node.grpcPort(),
                    ManagedChannelBuilder.forAddress(node.grpcHost(), node.grpcPort())
                            .usePlaintext()
                            .build());
        });
        return endpoint.channel();
    }

    @Override
    public void close() {
        endpoints.values().forEach(endpoint -> endpoint.channel().shutdown());
        endpoints.clear();
    }
}
