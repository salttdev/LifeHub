package dev.saltt.hub.grpc;

import com.google.protobuf.Timestamp;
import dev.saltt.life.protocol.GetPlayerByNameRequest;
import dev.saltt.life.protocol.GetPlayerRequest;
import dev.saltt.life.protocol.LifePlayerMessage;
import dev.saltt.life.protocol.LifePlayerServiceGrpc;
import dev.saltt.life.protocol.PostPlayerAck;
import dev.saltt.hub.database.domains.LifePlayer;
import dev.saltt.hub.database.repos.PlayerRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class LifePlayerServiceImpl extends LifePlayerServiceGrpc.LifePlayerServiceImplBase {

    private final PlayerRepository players;

    public LifePlayerServiceImpl(PlayerRepository players) {
        this.players = players;
    }

    @Override
    public void postPlayer(LifePlayerMessage req, StreamObserver<PostPlayerAck> obs) {
        try {
            UUID uuid = parseUuid(req.getUuid());
            players.save(new LifePlayer(
                    uuid,
                    emptyToNull(req.getDisplayName()),
                    req.hasFirstJoin()  ? toInstant(req.getFirstJoin())  : Instant.now(),
                    req.hasLastJoined() ? toInstant(req.getLastJoined()) : Instant.now(),
                    req.getPastNamesList(),
                    req.getUsedIpsList()));
            obs.onNext(PostPlayerAck.newBuilder().setUuid(uuid.toString()).setPersisted(true).build());
            obs.onCompleted();
        } catch (IllegalArgumentException e) {
            obs.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        } catch (Exception e) {
            obs.onError(Status.INTERNAL.withDescription("post failed").withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getPlayer(GetPlayerRequest req, StreamObserver<LifePlayerMessage> obs) {
        guard(obs, () -> respond(players.findByUuid(parseUuid(req.getUuid())), obs));
    }

    @Override
    public void getPlayerByName(GetPlayerByNameRequest req, StreamObserver<LifePlayerMessage> obs) {
        guard(obs, () -> {
            if (req.getUsername().isBlank()) throw new IllegalArgumentException("username required");
            respond(players.findByName(req.getUsername()), obs);
        });
    }

    private static void guard(StreamObserver<?> obs, Runnable body) {
        try { body.run(); }
        catch (IllegalArgumentException e) {
            obs.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        } catch (Exception e) {
            obs.onError(Status.INTERNAL.withDescription("lookup failed").withCause(e).asRuntimeException());
        }
    }

    private static void respond(Optional<LifePlayer> found, StreamObserver<LifePlayerMessage> obs) {
        if (found.isEmpty()) {
            obs.onError(Status.NOT_FOUND.withDescription("player not found").asRuntimeException());
            return;
        }
        obs.onNext(toProto(found.get()));
        obs.onCompleted();
    }

    private static LifePlayerMessage toProto(LifePlayer p) {
        LifePlayerMessage.Builder b = LifePlayerMessage.newBuilder()
                .setUuid(p.uuid().toString())
                .setFirstJoin(toTs(p.firstJoin()))
                .setLastJoined(toTs(p.lastJoined()))
                .addAllPastNames(p.pastNames())
                .addAllUsedIps(p.usedIps());
        if (p.displayName() != null) b.setDisplayName(p.displayName());
        return b.build();
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("uuid required");
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("uuid is not a valid UUID"); }
    }

    private static String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }
    private static Instant toInstant(Timestamp t) { return Instant.ofEpochSecond(t.getSeconds(), t.getNanos()); }
    private static Timestamp toTs(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }
}