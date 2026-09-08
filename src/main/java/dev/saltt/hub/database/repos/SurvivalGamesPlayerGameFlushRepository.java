package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.GameFlushRepository;
import dev.saltt.hub.database.domains.SurvivalGamesPlayer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SurvivalGamesPlayerGameFlushRepository extends GameFlushRepository<SurvivalGamesPlayer> {

    private static final RowMapper<SurvivalGamesPlayer> MAPPER = (rs, ctx) -> new SurvivalGamesPlayer(
            UUID.fromString(rs.getString("match_id")),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getInt("placement"),
            rs.getLong("time_alive"),
            uuidOrNull(rs.getString("killer_uuid")),
            uuidOrNull(rs.getString("team_uuid")),
            rs.getString("cause_of_death"));

    public SurvivalGamesPlayerGameFlushRepository(Jdbi jdbi) {
        super(jdbi, "survival_games_match_player",
                List.of("match_id", "player_uuid"),
                List.of("match_id", "player_uuid", "placement", "time_alive", "killer_uuid",
                        "team_uuid", "cause_of_death"),
                MAPPER);
    }

    @Override
    protected Map<String, Object> toRow(SurvivalGamesPlayer p) {
        Map<String, Object> row = new HashMap<>();   // nullable columns -> HashMap required
        row.put("match_id", p.matchId().toString());
        row.put("player_uuid", p.playerUuid().toString());
        row.put("placement", p.placement());
        row.put("time_alive", p.timeAlive());
        row.put("killer_uuid", p.killerUuid() == null ? null : p.killerUuid().toString());
        row.put("team_uuid", p.teamId() == null ? null : p.teamId().toString());
        row.put("cause_of_death", p.causeOfDeath());
        return row;
    }

    public List<SurvivalGamesPlayer> findByMatch(UUID matchId) {
        return find("match_id = :match_id", Map.of("match_id", matchId.toString()));
    }

    private static UUID uuidOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }
}
