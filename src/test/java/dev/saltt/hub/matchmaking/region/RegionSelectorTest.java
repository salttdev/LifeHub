package dev.saltt.hub.matchmaking.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionSelectorTest {

    private static final RegionConfig NA = new RegionConfig("na", 40.7, -74.0);
    private static final RegionConfig EU = new RegionConfig("eu", 50.1, 8.7);

    private final LatencyModel latency =
            new LatencyModel(() -> new LatencyModel.Settings(List.of(NA, EU), 10, 0.02, 80));
    private final RegionSelector selector = new RegionSelector(latency);

    private UUID measured(int toNa, int toEu) {
        UUID player = UUID.randomUUID();
        latency.setMeasured(player, "na", toNa);
        latency.setMeasured(player, "eu", toEu);
        return player;
    }

    @Test
    void mostPlayersBestRegionWins() {
        List<UUID> lobby = new ArrayList<>();
        lobby.add(measured(15, 95));
        lobby.add(measured(90, 20));
        lobby.add(measured(92, 25));

        // The worst case is the same either way (~95); the two EU players outvote the one NA player.
        assertEquals(List.of("eu", "na"), selector.rank(lobby));
    }

    @Test
    void majorityBeatsAWorseWorstCase() {
        List<UUID> lobby = new ArrayList<>();
        lobby.add(measured(15, 140));
        lobby.add(measured(90, 20));
        lobby.add(measured(90, 20));
        lobby.add(measured(90, 20));

        // Under a worst-case rule the lone NA player's 140 to EU would drag everyone to NA.
        assertEquals(List.of("eu", "na"), selector.rank(lobby));
    }

    @Test
    void measuredHubPingDoesNotOutweighAnEstimateForTheOtherRegion() {
        // What the hub actually knows: everyone's ping to the NA hub is measured, EU is only ever
        // estimated from GeoIP. Three Londoners and one New Yorker should still land in EU.
        UUID newYork = UUID.randomUUID();
        latency.setLocation(newYork, new GeoIpService.Coordinates(40.7, -74.0));
        latency.setMeasured(newYork, "na", 12);
        List<UUID> lobby = new ArrayList<>(List.of(newYork));
        for (int i = 0; i < 3; i++) {
            UUID london = UUID.randomUUID();
            latency.setLocation(london, new GeoIpService.Coordinates(51.5, -0.1));
            latency.setMeasured(london, "na", 95);
            lobby.add(london);
        }

        assertEquals(List.of("eu", "na"), selector.rank(lobby));
    }

    @Test
    void tiedVoteGoesToTheRegionWithFewerPlayersOverTheCap() {
        List<UUID> lobby = new ArrayList<>();
        lobby.add(measured(15, 110));   // over the cap in EU
        lobby.add(measured(95, 20));    // fine either way

        assertEquals(List.of("na", "eu"), selector.rank(lobby));
    }

    @Test
    void tiedVoteAndCapGoToTheLowerMean() {
        List<UUID> lobby = new ArrayList<>();
        lobby.add(measured(15, 60));
        lobby.add(measured(70, 20));

        // Nobody is over 80 anywhere; eu's mean (40) beats na's (42.5).
        assertEquals(List.of("eu", "na"), selector.rank(lobby));
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
