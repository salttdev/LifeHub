package dev.saltt.hub.matchmaking;

import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchStatus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every match the hub believes is running, fed by dispatch and kept current by heartbeats.
 *
 * <p>Advisory rather than exact. A node that dies stops beating and its matches age out here after
 * {@code staleAfterMillis}; a match the instancer created without the hub is adopted the first time
 * it beats. Capacity decisions err towards over-admitting, and the instancer refuses what it
 * genuinely cannot host.
 */
public final class MatchCache {

    private static final Logger LOG = Logger.getLogger(MatchCache.class.getName());

    private final ConcurrentHashMap<String, LiveMatch> matches = new ConcurrentHashMap<>();

    /**
     * Players claimed for a lobby whose SetupMatch has not been answered yet. They are in no queue
     * and in no match for as long as that call is in flight, and without this they could queue
     * again and be placed in a second lobby before the first one answers.
     */
    private final Set<UUID> reserved = ConcurrentHashMap.newKeySet();

    private final long staleAfterMillis;

    public MatchCache(long staleAfterMillis) {
        this.staleAfterMillis = Math.max(5_000L, staleAfterMillis);
    }

    public void add(LiveMatch match) {
        matches.put(match.matchId(), match);
    }

    public Optional<LiveMatch> get(String matchId) {
        return matchId == null ? Optional.empty() : Optional.ofNullable(matches.get(matchId));
    }

    public Optional<LiveMatch> remove(String matchId) {
        return matchId == null ? Optional.empty() : Optional.ofNullable(matches.remove(matchId));
    }

    public Collection<LiveMatch> all() {
        return List.copyOf(matches.values());
    }

    public int size() {
        return matches.size();
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

    /** Held from the moment a lobby is claimed until the node answers, either way. */
    public void reserve(Collection<UUID> players) {
        reserved.addAll(players);
    }

    public void releaseReservation(Collection<UUID> players) {
        reserved.removeAll(players);
    }

    public boolean isReserved(UUID player) {
        return reserved.contains(player);
    }

    /** Live matches on one node, which is what the pool spends its capacity against. */
    public int countOnNode(String nodeId) {
        int count = 0;
        for (LiveMatch match : matches.values()) {
            if (nodeId.equals(match.nodeId()) && !match.isFinished()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Folds a heartbeat in. A match with no entry here is adopted rather than dropped, so a hub
     * restart relearns what is running instead of double-booking those players.
     *
     * <p>TODO: MatchHeartbeat carries no node id, so an adopted match cannot be attributed to the
     * node it is running on and does not count against that node's capacity. Adding a node/server
     * id to MatchHeartbeat would close that gap.
     */
    public LiveMatch onHeartbeat(String matchId, GameType gameType, MatchStatus status,
                                 Set<UUID> connected, Set<UUID> claimed, int aliveCount) {
        return matches.compute(matchId, (id, existing) -> {
            if (existing == null) {
                LOG.info("adopting match " + id + " (" + gameType + ", " + status
                        + "), which this hub has no record of dispatching");
                return LiveMatch.dispatched(id, gameType, null, Set.of())
                        .withHeartbeat(status, connected, claimed, aliveCount);
            }
            return existing.withHeartbeat(status, connected, claimed, aliveCount);
        });
    }

    /**
     * Takes one player out of whatever match holds them, without ending it. Called when a player
     * turns up on the hub again: whatever the instancer still thinks, they are not in that match
     * any more, and holding the association would keep them out of the queues.
     */
    public void releasePlayer(UUID player) {
        for (LiveMatch match : matches.values()) {
            if (!match.involves(player)) {
                continue;
            }
            Set<UUID> roster = new LinkedHashSet<>(match.roster());
            Set<UUID> connected = new LinkedHashSet<>(match.connected());
            Set<UUID> claimed = new LinkedHashSet<>(match.claimed());
            roster.remove(player);
            connected.remove(player);
            claimed.remove(player);

            matches.replace(match.matchId(), match, new LiveMatch(match.matchId(), match.gameType(),
                    match.nodeId(), match.status(), roster, connected, claimed, match.aliveCount(),
                    match.createdAtMillis(), match.lastBeatAtMillis()));
        }
    }

    /**
     * Drops matches that stopped beating, and finished ones the result never arrived for. Returns
     * what it removed so the caller can release those players.
     */
    public List<LiveMatch> reapStale() {
        List<LiveMatch> reaped = new ArrayList<>();
        for (LiveMatch match : matches.values()) {
            if (match.sinceLastBeatMillis() < staleAfterMillis) {
                continue;
            }
            if (matches.remove(match.matchId(), match)) {
                reaped.add(match);
                LOG.log(Level.WARNING, "match " + match.matchId() + " on node "
                        + (match.nodeId() == null ? "?" : match.nodeId()) + " went quiet for "
                        + match.sinceLastBeatMillis() + "ms, dropping it");
            }
        }
        return reaped;
    }

    @Nullable
    public LiveMatch getOrNull(String matchId) {
        return matchId == null ? null : matches.get(matchId);
    }
}
