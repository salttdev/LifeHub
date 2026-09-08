package dev.saltt.hub.matchmaking.region;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Location;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Coordinates for an IP from a GeoLite2 City database. Reopens the file when it is replaced. */
public final class GeoIpService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GeoIpService.class.getName());

    public record Coordinates(double latitude, double longitude) {}

    private final Path path;
    private DatabaseReader reader;
    private long loadedModifiedMillis = -1L;
    private boolean warnedMissing;

    public GeoIpService(Path path) {
        this.path = path;
    }

    public synchronized Optional<Coordinates> locate(String ip) {
        DatabaseReader current = open();
        if (current == null || ip == null || ip.isBlank()) {
            return Optional.empty();
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return Optional.empty();
            }
            Optional<CityResponse> city = current.tryCity(address);
            if (city.isEmpty()) {
                return Optional.empty();
            }
            Location location = city.get().location();
            if (location == null || location.latitude() == null || location.longitude() == null) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(location.latitude(), location.longitude()));
        } catch (Exception e) {
            LOG.log(Level.FINE, "geoip lookup failed for " + ip, e);
            return Optional.empty();
        }
    }

    private DatabaseReader open() {
        long modified;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOG.warning("no GeoLite2 database at " + path
                        + "; region choice falls back to measured pings and config order");
            }
            return reader;
        }
        if (reader != null && modified == loadedModifiedMillis) {
            return reader;
        }
        try {
            DatabaseReader fresh = new DatabaseReader.Builder(path.toFile()).build();
            closeQuietly();
            reader = fresh;
            loadedModifiedMillis = modified;
            warnedMissing = false;
            LOG.info("loaded GeoLite2 database " + path);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not open GeoLite2 database " + path, e);
        }
        return reader;
    }

    private void closeQuietly() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
        reader = null;
    }
}
