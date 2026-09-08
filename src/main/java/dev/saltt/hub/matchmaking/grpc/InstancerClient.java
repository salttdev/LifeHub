package dev.saltt.hub.matchmaking.grpc;

import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.CancelMatchRequest;
import dev.saltt.life.protocol.CancelMatchResponse;
import dev.saltt.life.protocol.ForfeitPlayerRequest;
import dev.saltt.life.protocol.ForfeitPlayerResponse;
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
public final class InstancerClient implements NodeClient {

    private static final Logger LOG = Logger.getLogger(InstancerClient.class.getName());

    private static final long SHORT_DEADLINE_SECONDS = 5L;

    /** The node answered but said no. Distinguishes a refusal from an unreachable host. */
    public static final class RefusedException extends RuntimeException {
        public RefusedException(String message) {
            super(message);
        }
    }

    private record Endpoint(String host, int port, ManagedChannel channel) { }

    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();
    private final long setupDeadlineSeconds;

    /** SetupMatch builds a world before it answers, so its deadline covers a world load. */
    public InstancerClient(long setupDeadlineSeconds) {
        this.setupDeadlineSeconds = Math.max(1L, setupDeadlineSeconds);
    }

    @Override
    public CompletableFuture<String> setupMatch(InstancerNode node, String matchId, GameType gameType,
                                                List<QueuedPlayer> players, String mapId,
                                                int minimumStartingPlayers, String region) {
        SetupMatchRequest.Builder request = SetupMatchRequest.newBuilder()
                .setMatchId(matchId)
                .setGameType(gameType)
                .setMapId(mapId == null ? "" : mapId)
                .setMinimumStartingPlayers(minimumStartingPlayers)
                .setRegion(region == null ? "" : region);
        for (QueuedPlayer player : players) {
            request.addPlayers(MatchPlayer.newBuilder()
                    .setPlayerUuid(player.uuid().toString())
                    .setPlayerName(player.userName() == null ? "" : player.userName()));
        }

        String label = "SetupMatch " + gameType + " " + matchId + " (" + players.size()
                + " players) on " + node.id();
        return this.<SetupMatchResponse>call(node, label, setupDeadlineSeconds,
                (stub, observer) -> stub.setupMatch(request.build(), observer))
                .thenApply(response -> {
                    if (response.getSuccess() && !response.getMatchId().isEmpty()) {
                        return response.getMatchId();
                    }
                    throw new RefusedException(response.getError().isEmpty()
                            ? node.id() + " declined the match"
                            : node.id() + ": " + response.getError());
                });
    }

    @Override
    public CompletableFuture<Boolean> spectatorReserve(InstancerNode node, UUID spectator, String matchId) {
        SpectatorReserveRequest request = SpectatorReserveRequest.newBuilder()
                .setPlayerUuid(spectator.toString()).setMatchId(matchId).build();
        return this.<SpectatorReserveResponse>call(node, "SpectatorReserve " + spectator + " on " + matchId, SHORT_DEADLINE_SECONDS,
                (stub, observer) -> stub.spectatorReserve(request, observer))
                .thenApply(SpectatorReserveResponse::getSuccess);
    }

    @Override
    public CompletableFuture<Boolean> cancelMatch(InstancerNode node, String matchId, String reason) {
        CancelMatchRequest request = CancelMatchRequest.newBuilder()
                .setMatchId(matchId).setReason(reason == null ? "" : reason).build();
        return this.<CancelMatchResponse>call(node, "CancelMatch " + matchId, SHORT_DEADLINE_SECONDS,
                (stub, observer) -> stub.cancelMatch(request, observer))
                .thenApply(CancelMatchResponse::getCancelled);
    }

    @Override
    public CompletableFuture<Boolean> forfeitPlayer(InstancerNode node, String matchId, UUID player) {
        ForfeitPlayerRequest request = ForfeitPlayerRequest.newBuilder()
                .setMatchId(matchId).setPlayerUuid(player.toString()).build();
        return this.<ForfeitPlayerResponse>call(node, "ForfeitPlayer " + player + " on " + matchId, SHORT_DEADLINE_SECONDS,
                (stub, observer) -> stub.forfeitPlayer(request, observer))
                .thenApply(ForfeitPlayerResponse::getForfeited);
    }

    private interface Invocation<R> {
        void run(InstancerServiceGrpc.InstancerServiceStub stub, StreamObserver<R> observer);
    }

    private <R> CompletableFuture<R> call(InstancerNode node, String label, long deadlineSeconds,
                                          Invocation<R> invocation) {
        CompletableFuture<R> result = new CompletableFuture<>();
        long startedAt = System.currentTimeMillis();
        try {
            InstancerServiceGrpc.InstancerServiceStub stub = InstancerServiceGrpc.newStub(channel(node))
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            invocation.run(stub, new StreamObserver<>() {
                @Override
                public void onNext(R response) {
                    LOG.info(label + " answered in " + (System.currentTimeMillis() - startedAt) + "ms");
                    result.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    Status status = Status.fromThrowable(error);
                    LOG.log(Level.WARNING, label + " failed after "
                            + (System.currentTimeMillis() - startedAt) + "ms -> " + node.grpcHost()
                            + ":" + node.grpcPort() + " [" + status.getCode() + "] "
                            + status.getDescription());
                    result.completeExceptionally(error);
                }

                @Override
                public void onCompleted() {
                    if (!result.isDone()) {
                        result.completeExceptionally(
                                new RefusedException(node.id() + " closed without a response"));
                    }
                }
            });
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, label + " could not be dispatched", t);
            result.completeExceptionally(t);
        }
        return result;
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
