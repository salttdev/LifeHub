package dev.saltt.hub.matchmaking;

import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchStatus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Every match the hub believes is running, fed by dispatch and kept current by heartbeats.
 *
 * <p>Advisory rather than exact. A node that dies stops beating and its matches age out here after
 * {@code staleAfterMillis}; a match the hub has no record of is adopted the first time it beats,
 * which is how a restarted hub relearns what is running.
 */
public final class MatchCache {

    private static final Logger LOG = Logger.getLogger(MatchCache.class.getName());

    private final ConcurrentHashMap<String, LiveMatch> matches = new ConcurrentHashMap<>();

    /**
     * Players claimed for a lobby whose SetupMatch has not been answered yet. They are in no queue
     * and in no match for as long as that call is in flight.
     */
    private final Set<UUID> reserved = ConcurrentHashMap.newKeySet();

    private final long staleAfterMillis;

    public MatchCache(long staleAfterMillis) {
        this.staleAfterMillis = Math.max(5_000L, staleAfterMillis);
    }

    public void add(LiveMatch match) {
        matches.put(match.matchId(), match);
    }

    public Optional<LiveMatch> remove(String matchId) {
        return matchId == null ? Optional.empty() : Optional.ofNullable(matches.remove(matchId));
    }

    public Collection<LiveMatch> all() {
        return List.copyOf(matches.values());
    }

    public Optional<LiveMatch> findByPlayer(UUID player) {
        for (LiveMatch match : matches.values()) {
            if (match.involves(player)) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    /** Reserved counts as in a match: they are on their way to one. */
    public boolean isInMatch(UUID player) {
        return reserved.contains(player) || findByPlayer(player).isPresent();
    }

    public void reserve(Collection<UUID> players) {
        reserved.addAll(players);
    }

    public void releaseReservation(Collection<UUID> players) {
        reserved.removeAll(players);
    }

    public int countOnNode(String nodeId) {
        int count = 0;
        for (LiveMatch match : matches.values()) {
            if (nodeId.equals(match.nodeId()) && !match.isFinished()) {
                count++;
            }
        }
        return count;
    }

    public LiveMatch onHeartbeat(String matchId, GameType gameType, MatchStatus status,
                                 Set<UUID> connected, Set<UUID> claimed, int aliveCount,
                                 @Nullable String nodeId) {
        return matches.compute(matchId, (id, existing) -> {
            if (existing == null) {
                LOG.info("adopting match " + id + " (" + gameType + ", " + status + ") on node "
                        + nodeId + ", which this hub has no record of dispatching");
                existing = LiveMatch.dispatched(id, gameType, null, Set.of());
            }
            return existing.withHeartbeat(status, connected, claimed, aliveCount, nodeId);
        });
    }

    /** Takes one player out of whatever match holds them, without ending it. */
    public void removePlayer(UUID player) {
        for (LiveMatch match : matches.values()) {
            if (match.involves(player)) {
                matches.replace(match.matchId(), match, match.without(player));
            }
        }
    }

    /** Drops matches that stopped beating. Returns them so the caller can release their players. */
    public List<LiveMatch> reapStale() {
        List<LiveMatch> reaped = new ArrayList<>();
        for (LiveMatch match : matches.values()) {
            if (match.sinceLastBeatMillis() < staleAfterMillis) {
                continue;
            }
            if (matches.remove(match.matchId(), match)) {
                reaped.add(match);
                LOG.warning("match " + match.matchId() + " on node " + match.nodeId()
                        + " went quiet for " + match.sinceLastBeatMillis() + "ms, dropping it");
            }
        }
        return reaped;
    }

    @Nullable
    public LiveMatch getOrNull(String matchId) {
        return matchId == null ? null : matches.get(matchId);
    }
}
