package dev.saltt.hub.matchmaking.objects;

import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchStatus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A match the hub believes is live on an instancer.
 *
 * @param roster        who the hub sent to it. Kept as well as the heartbeat's own lists, because
 *                      a player who has not arrived yet appears in neither of those and still must
 *                      not be queued into a second lobby.
 * @param nodeId        the node the hub dispatched to, or null for a match adopted from a
 *                      heartbeat the hub has no record of (see MatchCache#onHeartbeat).
 * @param lastBeatAtMillis arrival time of the newest heartbeat, which the reaper ages out.
 */
public record LiveMatch(String matchId, GameType gameType, String nodeId, MatchStatus status,
                        Set<UUID> roster, Set<UUID> connected, Set<UUID> claimed,
                        int aliveCount, long createdAtMillis, long lastBeatAtMillis) {

    public LiveMatch {
        roster = Collections.unmodifiableSet(new LinkedHashSet<>(roster));
        connected = Collections.unmodifiableSet(new LinkedHashSet<>(connected));
        claimed = Collections.unmodifiableSet(new LinkedHashSet<>(claimed));
    }

    public static LiveMatch dispatched(String matchId, GameType gameType, String nodeId,
                                       Set<UUID> roster) {
        long now = System.currentTimeMillis();
        return new LiveMatch(matchId, gameType, nodeId, MatchStatus.SERVER_LOADING, roster,
                Set.of(), Set.of(), 0, now, now);
    }

    /**
     * Folds in a heartbeat. The roster only grows: a player the instancer reports that the hub did
     * not send is still someone who must not be queued elsewhere.
     */
    public LiveMatch withHeartbeat(MatchStatus status, Set<UUID> connected, Set<UUID> claimed,
                                   int aliveCount) {
        Set<UUID> merged = new LinkedHashSet<>(roster);
        merged.addAll(connected);
        merged.addAll(claimed);
        return new LiveMatch(matchId, gameType, nodeId, status, merged, connected, claimed,
                Math.max(0, aliveCount), createdAtMillis, System.currentTimeMillis());
    }

    public boolean involves(UUID player) {
        return roster.contains(player) || connected.contains(player) || claimed.contains(player);
    }

    /** Over, as far as the hub is concerned: it holds no players and takes no spectators. */
    public boolean isFinished() {
        return status == MatchStatus.ENDED;
    }

    /** Whether a spectator can still be sent here. */
    public boolean isWatchable() {
        return status == MatchStatus.WAITING_FOR_PLAYERS || status == MatchStatus.STARTING
                || status == MatchStatus.IN_PROGRESS;
    }

    public long ageMillis() {
        return Math.max(0L, System.currentTimeMillis() - createdAtMillis);
    }

    public long sinceLastBeatMillis() {
        return Math.max(0L, System.currentTimeMillis() - lastBeatAtMillis);
    }
}
