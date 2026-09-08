package dev.saltt.hub.matchmaking.objects;

import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchStatus;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A match the hub believes is live on an instancer.
 *
 * @param roster           everyone the match holds. Until the first heartbeat this is who the hub
 *                         sent; from then on it is what the node reports as claimed or connected,
 *                         so a player the node has let go is free here too.
 * @param nodeId           null only for a match adopted from a heartbeat that named no node.
 * @param lastBeatAtMillis arrival time of the newest heartbeat, which the reaper ages out.
 */
public record LiveMatch(String matchId, GameType gameType, @Nullable String nodeId, MatchStatus status,
                        Set<UUID> roster, Set<UUID> connected, Set<UUID> claimed,
                        int aliveCount, long createdAtMillis, long lastBeatAtMillis) {

    public LiveMatch {
        roster = Collections.unmodifiableSet(new LinkedHashSet<>(roster));
        connected = Collections.unmodifiableSet(new LinkedHashSet<>(connected));
        claimed = Collections.unmodifiableSet(new LinkedHashSet<>(claimed));
    }

    public static LiveMatch dispatched(String matchId, GameType gameType, @Nullable String nodeId,
                                       Set<UUID> roster) {
        long now = System.currentTimeMillis();
        return new LiveMatch(matchId, gameType, nodeId, MatchStatus.SERVER_LOADING, roster,
                Set.of(), Set.of(), 0, now, now);
    }

    public LiveMatch withHeartbeat(MatchStatus status, Set<UUID> connected, Set<UUID> claimed,
                                   int aliveCount, @Nullable String reportedNodeId) {
        Set<UUID> held = new LinkedHashSet<>(claimed);
        held.addAll(connected);
        String node = nodeId != null ? nodeId
                : reportedNodeId == null || reportedNodeId.isBlank() ? null : reportedNodeId;
        return new LiveMatch(matchId, gameType, node, status, held, connected, claimed,
                Math.max(0, aliveCount), createdAtMillis, System.currentTimeMillis());
    }

    public LiveMatch without(UUID player) {
        Set<UUID> roster = new LinkedHashSet<>(this.roster);
        Set<UUID> connected = new LinkedHashSet<>(this.connected);
        Set<UUID> claimed = new LinkedHashSet<>(this.claimed);
        roster.remove(player);
        connected.remove(player);
        claimed.remove(player);
        return new LiveMatch(matchId, gameType, nodeId, status, roster, connected, claimed,
                aliveCount, createdAtMillis, lastBeatAtMillis);
    }

    public boolean involves(UUID player) {
        return roster.contains(player);
    }

    public boolean isFinished() {
        return status == MatchStatus.ENDED;
    }

    /** Whether a player can still be sent here, to play or to watch. */
    public boolean isJoinable() {
        return status == MatchStatus.SERVER_LOADING || status == MatchStatus.WAITING_FOR_PLAYERS
                || status == MatchStatus.STARTING || status == MatchStatus.IN_PROGRESS;
    }

    public long sinceLastBeatMillis() {
        return Math.max(0L, System.currentTimeMillis() - lastBeatAtMillis);
    }
}
