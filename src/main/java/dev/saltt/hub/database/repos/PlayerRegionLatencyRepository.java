package dev.saltt.hub.database.repos;

import org.jdbi.v3.core.Jdbi;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerRegionLatencyRepository {

    private static final String UPSERT = """
            INSERT INTO player_region_latency (player_uuid, region, rtt_millis, samples, updated_at)
            VALUES (:uuid, :region, :rtt, 1, :now)
            ON DUPLICATE KEY UPDATE
                rtt_millis = (rtt_millis * 3 + VALUES(rtt_millis)) DIV 4,
                samples = samples + 1,
                updated_at = VALUES(updated_at)
            """;

    private final Jdbi jdbi;

    public PlayerRegionLatencyRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Region id -> smoothed rtt in millis. */
    public Map<String, Integer> load(UUID player) {
        return jdbi.withHandle(h -> {
            Map<String, Integer> out = new HashMap<>();
            h.createQuery("SELECT region, rtt_millis FROM player_region_latency WHERE player_uuid = :uuid")
                    .bind("uuid", player.toString())
                    .map((rs, ctx) -> Map.entry(rs.getString("region"), rs.getInt("rtt_millis")))
                    .forEach(e -> out.put(e.getKey(), e.getValue()));
            return out;
        });
    }

    /** Folds one sample in with a 3:1 weight on what was already there. */
    public void record(UUID player, String region, int rttMillis) {
        jdbi.useHandle(h -> h.createUpdate(UPSERT)
                .bind("uuid", player.toString())
                .bind("region", region)
                .bind("rtt", rttMillis)
                .bind("now", LocalDateTime.now(ZoneOffset.UTC))
                .execute());
    }
}
