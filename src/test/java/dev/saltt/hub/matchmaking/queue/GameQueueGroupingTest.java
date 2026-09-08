package dev.saltt.hub.matchmaking.queue;

import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameQueueGroupingTest {

    private final GameQueue queue = new GameQueue(GameType.SURVIVAL_GAMES,
            () -> new QueueRules(GameType.SURVIVAL_GAMES, 2, 4, 0, 0));
    private final Map<UUID, String> regions = new HashMap<>();

    private UUID join(String region, long at) {
        UUID uuid = UUID.randomUUID();
        queue.add(new QueuedPlayer(uuid, "p", at));
        if (region != null) {
            regions.put(uuid, region);
        }
        return uuid;
    }

    private List<UUID> claim(int limit, int minimum) {
        return queue.claimGrouped(limit, minimum, regions::get).stream().map(QueuedPlayer::uuid).toList();
    }

    @Test
    void buildsAroundTheLargestRegionAndFillsWithUnknowns() {
        UUID eu1 = join("eu", 1);
        UUID na1 = join("na", 2);
        UUID eu2 = join("eu", 3);
        UUID unknown = join(null, 4);
        UUID eu3 = join("eu", 5);
        UUID na2 = join("na", 6);

        List<UUID> lobby = claim(4, 2);

        assertEquals(List.of(eu1, eu2, eu3, unknown), lobby);
        assertEquals(2, queue.size());
        assertTrue(queue.contains(na1) && queue.contains(na2));
    }

    @Test
    void topsUpFromOtherRegionsOnlyToReachTheMinimum() {
        UUID na1 = join("na", 1);
        UUID eu1 = join("eu", 2);
        UUID eu2 = join("eu", 3);
        UUID eu3 = join("eu", 4);
        join("na", 5);

        // The eu group is largest and already past the minimum, so the na players stay queued.
        assertEquals(List.of(eu1, eu2, eu3), claim(3, 2));

        // Now only na players remain but one of them has to be enough: nothing to top up from.
        assertEquals(List.of(na1), claim(1, 1));
    }

    @Test
    void reachesTheMinimumAcrossRegionsWhenAGroupIsTooSmall() {
        UUID na1 = join("na", 1);
        UUID eu1 = join("eu", 2);

        assertEquals(List.of(na1, eu1), claim(4, 2));
        assertEquals(0, queue.size());
    }
}
