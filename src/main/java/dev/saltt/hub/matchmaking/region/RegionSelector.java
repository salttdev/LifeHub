package dev.saltt.hub.matchmaking.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Orders the regions for one lobby: lowest worst-case ping first, mean as the tie-break. Players
 * nothing is known about do not vote; if that is everyone, config order stands.
 */
public final class RegionSelector {

    private record Score(String region, int worst, double mean, int order) {}

    private final LatencyModel latency;

    public RegionSelector(LatencyModel latency) {
        this.latency = latency;
    }

    public List<String> rank(Collection<UUID> lobby) {
        List<Score> scores = new ArrayList<>();
        int order = 0;
        for (RegionConfig region : latency.regions()) {
            if (!region.isUsable()) {
                continue;
            }
            int worst = 0;
            long total = 0;
            int voters = 0;
            for (UUID player : lobby) {
                OptionalInt rtt = latency.rtt(player, region.id());
                if (rtt.isPresent()) {
                    worst = Math.max(worst, rtt.getAsInt());
                    total += rtt.getAsInt();
                    voters++;
                }
            }
            double mean = voters == 0 ? Double.MAX_VALUE : (double) total / voters;
            scores.add(new Score(region.id(), voters == 0 ? Integer.MAX_VALUE : worst, mean, order++));
        }
        scores.sort(Comparator.comparingInt(Score::worst)
                .thenComparingDouble(Score::mean)
                .thenComparingInt(Score::order));
        return scores.stream().map(Score::region).toList();
    }
}
