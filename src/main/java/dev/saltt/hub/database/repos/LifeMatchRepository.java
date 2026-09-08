package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.GameFlushRepository;
import dev.saltt.hub.database.domains.LifeMatch;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The row every game type hangs its per-player stats off. */
public final class LifeMatchRepository extends GameFlushRepository<LifeMatch> {

    private static final RowMapper<LifeMatch> MAPPER = (rs, ctx) -> new LifeMatch(
            UUID.fromString(rs.getString("match_id")),
            rs.getString("game_type"),
            rs.getString("map_name"),
            rs.getString("node_id"),
            instant(rs.getObject("started_at", LocalDateTime.class)),
            instant(rs.getObject("ended_at", LocalDateTime.class)),
            rs.getBoolean("abandoned"),
            uuidOrNull(rs.getString("winner_uuid")));

    public LifeMatchRepository(Jdbi jdbi) {
        super(jdbi, "life_match",
                List.of("match_id"),
                List.of("match_id", "game_type", "map_name", "node_id", "started_at", "ended_at",
                        "abandoned", "winner_uuid"),
                MAPPER);
    }

    @Override
    protected Map<String, Object> toRow(LifeMatch match) {
        Map<String, Object> row = new HashMap<>();   // nullable columns -> HashMap required
        row.put("match_id", match.matchId().toString());
        row.put("game_type", match.gameType());
        row.put("map_name", match.mapName());
        row.put("node_id", match.nodeId());
        row.put("started_at", ldt(match.startedAt()));
        row.put("ended_at", ldt(match.endedAt()));
        row.put("abandoned", match.abandoned());
        row.put("winner_uuid", match.winnerUuid() == null ? null : match.winnerUuid().toString());
        return row;
    }

    private static LocalDateTime ldt(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static UUID uuidOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }
}
