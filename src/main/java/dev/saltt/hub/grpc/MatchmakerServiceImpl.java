package dev.saltt.hub.grpc;

import dev.saltt.hub.database.results.MatchResultWriter;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.HeartbeatAck;
import dev.saltt.life.protocol.MatchHeartbeat;
import dev.saltt.life.protocol.MatchResult;
import dev.saltt.life.protocol.MatchResultAck;
import dev.saltt.life.protocol.MatchStatus;
import dev.saltt.life.protocol.MatchmakerServiceGrpc;
import dev.saltt.life.protocol.PlayerResult;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Everything the instancers report: what is running, and what finished. */
public final class MatchmakerServiceImpl extends MatchmakerServiceGrpc.MatchmakerServiceImplBase {

    private static final Logger LOG = Logger.getLogger(MatchmakerServiceImpl.class.getName());

    private final MatchmakingService matchmaking;
    private final MatchResultWriter results;

    public MatchmakerServiceImpl(MatchmakingService matchmaking, MatchResultWriter results) {
        this.matchmaking = matchmaking;
        this.results = results;
    }

    @Override
    public void heartbeat(MatchHeartbeat request, StreamObserver<HeartbeatAck> observer) {
        try {
            requireMatchId(request.getMatchId());
            requireGameType(request.getGameType());
            if (request.getStatus() == MatchStatus.UNRECOGNIZED) {
                throw new IllegalArgumentException("unknown status");
            }

            LiveMatch match = matchmaking.matches().onHeartbeat(
                    request.getMatchId(),
                    request.getGameType(),
                    request.getStatus(),
                    uuids(request.getConnectedPlayerIdsList(), "connected_player_ids"),
                    uuids(request.getClaimedPlayerIdsList(), "claimed_player_ids"),
                    request.getAliveCount());

            // A player the instancer has, or is still expecting, must not be pulled into a second
            // lobby by the next pass.
            match.roster().forEach(player -> matchmaking.queues().removeFromAll(player));

            observer.onNext(HeartbeatAck.newBuilder().setAcknowledged(true).build());
            observer.onCompleted();

        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).withCause(e).asRuntimeException());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "heartbeat failed for match " + request.getMatchId(), e);
            observer.onError(Status.INTERNAL
                    .withDescription("heartbeat failed").withCause(e).asRuntimeException());
        }
    }

    @Override
    public void matchFinished(MatchResult request, StreamObserver<MatchResultAck> observer) {
        try {
            requireMatchId(request.getMatchId());
            requireGameType(request.getGameType());
            for (PlayerResult player : request.getPlayersList()) {
                parseUuid(player.getPlayerUuid(), "player_uuid");
                if (!player.getKillerUuid().isBlank()) {
                    parseUuid(player.getKillerUuid(), "killer_uuid");
                }
            }
            if (!request.getWinnerUuid().isBlank()) {
                parseUuid(request.getWinnerUuid(), "winner_uuid");
            }

            // Removed first: the players are free to queue again as soon as they are back on the
            // hub, whatever the write does. node_id is only known from the hub's own record.
            LiveMatch finished = matchmaking.matches().remove(request.getMatchId()).orElse(null);
            if (finished != null) {
                finished.roster().forEach(matchmaking.queues()::removeFromAll);
            }

            results.write(request, finished == null ? null : finished.nodeId());

            LOG.info("match " + request.getMatchId() + " (" + request.getGameType() + ") finished"
                    + (request.getAbandoned() ? " abandoned" : "") + " with "
                    + request.getPlayersCount() + " player result(s)");

            observer.onNext(MatchResultAck.newBuilder().setAcknowledged(true).build());
            observer.onCompleted();

        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).withCause(e).asRuntimeException());
        } catch (Exception e) {
            // Not acknowledged, so the instancer's own retry (if any) can bring it back rather
            // than the result being lost silently.
            LOG.log(Level.SEVERE, "could not persist result for match " + request.getMatchId(), e);
            observer.onError(Status.INTERNAL
                    .withDescription("result not persisted").withCause(e).asRuntimeException());
        }
    }

    private static Set<UUID> uuids(List<String> raw, String field) {
        Set<UUID> parsed = new LinkedHashSet<>(raw.size());
        for (String value : raw) {
            parsed.add(parseUuid(value, field));
        }
        return parsed;
    }

    /** The instancer generates match ids as UUIDs, and the tables store CHAR(36). */
    private static void requireMatchId(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("match_id required");
        }
        parseUuid(matchId, "match_id");
    }

    private static void requireGameType(GameType gameType) {
        if (gameType == GameType.UNRECOGNIZED || gameType == GameType.GAME_TYPE_UNSPECIFIED) {
            throw new IllegalArgumentException("unknown game_type");
        }
    }

    /** Parsed here so malformed input is INVALID_ARGUMENT rather than an INTERNAL further in. */
    private static UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " is not a valid UUID: " + raw);
        }
    }
}
