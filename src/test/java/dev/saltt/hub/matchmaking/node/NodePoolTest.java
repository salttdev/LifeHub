package dev.saltt.hub.matchmaking.node;

import dev.saltt.hub.matchmaking.MatchCache;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.life.protocol.GameType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodePoolTest {

    private static InstancerNode node(String id, String region, int max) {
        return new InstancerNode(id, "h", 1, "p", 2, max, region);
    }

    private final MatchCache matches = new MatchCache(30_000);
    private final NodePool pool = new NodePool(() -> List.of(
            node("na-1", "na", 2), node("na-2", "na", 2), node("eu-1", "eu", 1)), matches);

    private List<String> ids(List<InstancerNode> nodes) {
        return nodes.stream().map(InstancerNode::id).toList();
    }

    @Test
    void bestRegionFirstThenTheRest() {
        assertEquals(List.of("eu-1", "na-1", "na-2"), ids(pool.withCapacity(List.of("eu", "na"), true)));
    }

    @Test
    void withoutFallbackOnlyTheBestRegion() {
        assertEquals(List.of("eu-1"), ids(pool.withCapacity(List.of("eu", "na"), false)));
    }

    @Test
    void fullNodesDropOutAndEmptierNodesComeFirst() {
        matches.add(LiveMatch.dispatched("a", GameType.SURVIVAL_GAMES, "eu-1", Set.of(UUID.randomUUID())));
        matches.add(LiveMatch.dispatched("b", GameType.SURVIVAL_GAMES, "na-1", Set.of(UUID.randomUUID())));

        assertEquals(List.of("na-2", "na-1"), ids(pool.withCapacity(List.of("eu", "na"), true)));
    }
}
