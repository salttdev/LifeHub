package dev.saltt.hub.matchmaking.region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What the hub knows about each online player's ping to every region: a measured value where one
 * has been recorded, otherwise an estimate from their GeoIP coordinates and the region's.
 */
public final class LatencyModel {

    public record Settings(List<RegionConfig> regions, double baseMillis, double millisPerKm) {}

    private static final class Profile {
        volatile GeoIpService.Coordinates coordinates;
        final Map<String, Integer> measured = new ConcurrentHashMap<>();
    }

    private final Supplier<Settings> settings;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public LatencyModel(Supplier<Settings> settings) {
        this.settings = settings;
    }

    public List<RegionConfig> regions() {
        return settings.get().regions();
    }

    public void setLocation(UUID player, GeoIpService.Coordinates coordinates) {
        profile(player).coordinates = coordinates;
    }

    public void setMeasured(UUID player, String region, int rttMillis) {
        if (region != null && !region.isBlank() && rttMillis > 0) {
            profile(player).measured.put(region, rttMillis);
        }
    }

    public void setMeasured(UUID player, Map<String, Integer> byRegion) {
        byRegion.forEach((region, rtt) -> setMeasured(player, region, rtt));
    }

    public void forget(UUID player) {
        profiles.remove(player);
    }

    public Optional<GeoIpService.Coordinates> location(UUID player) {
        Profile profile = profiles.get(player);
        return Optional.ofNullable(profile == null ? null : profile.coordinates);
    }

    public Map<String, Integer> measured(UUID player) {
        Profile profile = profiles.get(player);
        return profile == null ? Map.of() : Map.copyOf(profile.measured);
    }

    /** Empty when nothing is known about this player for this region. */
    public OptionalInt rtt(UUID player, String region) {
        Profile profile = profiles.get(player);
        if (profile == null) {
            return OptionalInt.empty();
        }
        Integer measured = profile.measured.get(region);
        if (measured != null) {
            return OptionalInt.of(measured);
        }
        GeoIpService.Coordinates at = profile.coordinates;
        if (at == null) {
            return OptionalInt.empty();
        }
        Settings current = settings.get();
        for (RegionConfig candidate : current.regions()) {
            if (candidate.id().equals(region)) {
                double km = haversineKm(at.latitude(), at.longitude(),
                        candidate.latitude(), candidate.longitude());
                return OptionalInt.of((int) Math.round(current.baseMillis() + km * current.millisPerKm()));
            }
        }
        return OptionalInt.empty();
    }

    /** The configured region with the lowest rtt for this player, if anything is known. */
    public Optional<String> bestRegion(UUID player) {
        String best = null;
        int bestRtt = Integer.MAX_VALUE;
        for (RegionConfig region : settings.get().regions()) {
            OptionalInt rtt = rtt(player, region.id());
            if (rtt.isPresent() && rtt.getAsInt() < bestRtt) {
                bestRtt = rtt.getAsInt();
                best = region.id();
            }
        }
        return Optional.ofNullable(best);
    }

    private Profile profile(UUID player) {
        return profiles.computeIfAbsent(player, ignored -> new Profile());
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
