package dev.saltt.hub.matchmaking.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionSelectorTest {

    private static final RegionConfig NA = new RegionConfig("na", 40.7, -74.0);
    private static final RegionConfig EU = new RegionConfig("eu", 50.1, 8.7);

    private final LatencyModel latency =
            new LatencyModel(() -> new LatencyModel.Settings(List.of(NA, EU), 10, 0.02));
    private final RegionSelector selector = new RegionSelector(latency);

    @Test
    void picksTheRegionWithTheLowestWorstPing() {
        UUID london = UUID.randomUUID();
        UUID newYork = UUID.randomUUID();
        latency.setMeasured(london, "eu", 20);
        latency.setMeasured(london, "na", 90);
        latency.setMeasured(newYork, "eu", 95);
        latency.setMeasured(newYork, "na", 15);

        // Worst case is 90 in na and 95 in eu, so na wins even though eu's mean is close.
        assertEquals(List.of("na", "eu"), selector.rank(List.of(london, newYork)));
    }

    @Test
    void playersWithNoDataDoNotVote() {
        UUID london = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        latency.setLocation(london, new GeoIpService.Coordinates(51.5, -0.1));

        assertEquals(List.of("eu", "na"), selector.rank(List.of(london, unknown)));
    }

    @Test
    void configOrderWhenNobodyIsKnown() {
        assertEquals(List.of("na", "eu"), selector.rank(List.of(UUID.randomUUID())));
    }
}
