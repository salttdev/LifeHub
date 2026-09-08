package dev.saltt.hub.matchmaking;

import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchCacheTest {

    private final MatchCache cache = new MatchCache(30_000);

    @Test
    void heartbeatReplacesTheDispatchedRoster() {
        UUID arrived = UUID.randomUUID();
        UUID noShow = UUID.randomUUID();
        cache.add(LiveMatch.dispatched("m", GameType.SURVIVAL_GAMES, "na-1", Set.of(arrived, noShow)));
        assertTrue(cache.isInMatch(noShow));

        cache.onHeartbeat("m", GameType.SURVIVAL_GAMES, MatchStatus.IN_PROGRESS,
                Set.of(arrived), Set.of(arrived), 1, "na-1");

        assertTrue(cache.isInMatch(arrived));
        assertFalse(cache.isInMatch(noShow), "a player the node let go is free on the hub");
        assertEquals(1, cache.countOnNode("na-1"));
    }

    @Test
    void adoptsUnknownMatchesOntoTheReportedNode() {
        UUID player = UUID.randomUUID();
        LiveMatch adopted = cache.onHeartbeat("m", GameType.SURVIVAL_GAMES, MatchStatus.WAITING_FOR_PLAYERS,
                Set.of(), Set.of(player), 0, "eu-1");

        assertEquals("eu-1", adopted.nodeId());
        assertTrue(cache.isInMatch(player));
        assertEquals(1, cache.countOnNode("eu-1"));
    }

    @Test
    void reservationsCountAsInAMatchUntilReleased() {
        UUID player = UUID.randomUUID();
        cache.reserve(Set.of(player));
        assertTrue(cache.isInMatch(player));

        cache.releaseReservation(Set.of(player));
        assertFalse(cache.isInMatch(player));
    }

    @Test
    void removePlayerFreesOnlyThatPlayer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cache.add(LiveMatch.dispatched("m", GameType.SURVIVAL_GAMES, "na-1", Set.of(a, b)));

        cache.removePlayer(a);

        assertFalse(cache.isInMatch(a));
        assertTrue(cache.isInMatch(b));
    }
}
