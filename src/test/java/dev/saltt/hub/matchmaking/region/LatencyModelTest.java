package dev.saltt.hub.matchmaking.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyModelTest {

    private static final RegionConfig NA = new RegionConfig("na", 40.7, -74.0);
    private static final RegionConfig EU = new RegionConfig("eu", 50.1, 8.7);

    private static LatencyModel model() {
        return new LatencyModel(() -> new LatencyModel.Settings(List.of(NA, EU), 10, 0.02));
    }

    @Test
    void estimatesFromDistanceWhenNothingIsMeasured() {
        LatencyModel model = model();
        UUID player = UUID.randomUUID();
        model.setLocation(player, new GeoIpService.Coordinates(51.5, -0.1)); // London

        int toEu = model.rtt(player, "eu").orElseThrow();
        int toNa = model.rtt(player, "na").orElseThrow();

        assertTrue(toEu < toNa);
        assertTrue(toEu > 10 && toEu < 40, "London to Frankfurt should be a few tens of ms, was " + toEu);
        assertEquals("eu", model.bestRegion(player).orElseThrow());
    }

    @Test
    void measuredBeatsTheEstimate() {
        LatencyModel model = model();
        UUID player = UUID.randomUUID();
        model.setLocation(player, new GeoIpService.Coordinates(51.5, -0.1));
        model.setMeasured(player, "na", 15);

        assertEquals(15, model.rtt(player, "na").orElseThrow());
        assertEquals("na", model.bestRegion(player).orElseThrow());
    }

    @Test
    void unknownPlayersHaveNoOpinion() {
        LatencyModel model = model();
        UUID player = UUID.randomUUID();

        assertTrue(model.rtt(player, "na").isEmpty());
        assertTrue(model.bestRegion(player).isEmpty());
    }

    @Test
    void haversineIsRoughlyRight() {
        double km = LatencyModel.haversineKm(40.7, -74.0, 50.1, 8.7);
        assertTrue(km > 6100 && km < 6300, "NYC to Frankfurt is about 6200km, was " + km);
    }
}
