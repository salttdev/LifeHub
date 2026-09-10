package dev.saltt.hub.matchmaking.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Orders the regions for one lobby the way most matchmakers do it: every player votes for the
 * region they ping best, and the region with the most votes hosts. Ties go to the region that
 * leaves the fewest players over the acceptable ping cap, then to the lower mean, then to config
 * order. Players nothing is known about do not vote; if that is everyone, config order stands.
 *
 * <p>Votes are compared within one player, so a measured ping to one region and an estimated ping
 * to another only have to get the order right, not the exact number. That is what makes this
 * robust where a worst-case or mean rule is not: those compare a measured number for one player
 * against an estimated number for another, and whichever region the estimates run high for loses
 * every mixed lobby.
 */
public final class RegionSelector {

    private record Score(String region, int votes, int overCap, double mean, int order) {}

    private final LatencyModel latency;

    public RegionSelector(LatencyModel latency) {
        this.latency = latency;
    }

    public List<String> rank(Collection<UUID> lobby) {
        List<RegionConfig> usable = latency.regions().stream().filter(RegionConfig::isUsable).toList();
        int cap = latency.acceptablePingMillis();

        int[] votes = new int[usable.size()];
        int[] overCap = new int[usable.size()];
        long[] total = new long[usable.size()];
        int[] known = new int[usable.size()];

        for (UUID player : lobby) {
            int best = -1;
            int bestRtt = Integer.MAX_VALUE;
            for (int i = 0; i < usable.size(); i++) {
                OptionalInt rtt = latency.rtt(player, usable.get(i).id());
                if (rtt.isEmpty()) {
                    continue;
                }
                known[i]++;
                total[i] += rtt.getAsInt();
                if (rtt.getAsInt() > cap) {
                    overCap[i]++;
                }
                if (rtt.getAsInt() < bestRtt) {
                    bestRtt = rtt.getAsInt();
                    best = i;
                }
            }
            if (best >= 0) {
                votes[best]++;
            }
        }

        List<Score> scores = new ArrayList<>();
        for (int i = 0; i < usable.size(); i++) {
            double mean = known[i] == 0 ? Double.MAX_VALUE : (double) total[i] / known[i];
            scores.add(new Score(usable.get(i).id(), votes[i], overCap[i], mean, i));
        }
        scores.sort(Comparator.comparingInt(Score::votes).reversed()
                .thenComparingInt(Score::overCap)
                .thenComparingDouble(Score::mean)
                .thenComparingInt(Score::order));
        return scores.stream().map(Score::region).toList();
    }
}
