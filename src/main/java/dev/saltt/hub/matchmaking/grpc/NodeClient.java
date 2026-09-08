package dev.saltt.hub.matchmaking.grpc;

import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Outgoing calls to an instancer node. An interface so the matchmaker can be tested without one. */
public interface NodeClient extends AutoCloseable {

    /** Completes with the accepted match id, or exceptionally on refusal, deadline or transport failure. */
    CompletableFuture<String> setupMatch(InstancerNode node, String matchId, GameType gameType,
                                         List<QueuedPlayer> players, String mapId,
                                         int minimumStartingPlayers, String region);

    CompletableFuture<Boolean> spectatorReserve(InstancerNode node, UUID spectator, String matchId);

    CompletableFuture<Boolean> cancelMatch(InstancerNode node, String matchId, String reason);

    CompletableFuture<Boolean> forfeitPlayer(InstancerNode node, String matchId, UUID player);

    @Override
    void close();
}
