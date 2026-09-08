package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.GameFlushRepository;
import dev.saltt.hub.database.domains.MatchPlayerStats;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatchPlayerStatsGameFlushRepository extends GameFlushRepository<MatchPlayerStats> {

    private static final RowMapper<MatchPlayerStats> MAPPER = (rs, ctx) -> new MatchPlayerStats(
            UUID.fromString(rs.getString("match_id")),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getInt("kills"),
            rs.getInt("deaths"),
            rs.getInt("assists"),
            rs.getLong("damage_dealt"),
            rs.getLong("damage_taken"));

    public MatchPlayerStatsGameFlushRepository(Jdbi jdbi) {
        super(jdbi, "match_player_stats",
                List.of("match_id", "player_uuid"),
                List.of("match_id", "player_uuid", "kills", "deaths", "assists",
                        "damage_dealt", "damage_taken"),
                MAPPER);
    }

    @Override
    protected Map<String, Object> toRow(MatchPlayerStats s) {
        Map<String, Object> row = new HashMap<>();   // no nulls here, but HashMap is harmless
        row.put("match_id", s.matchId().toString());
        row.put("player_uuid", s.playerUuid().toString());
        row.put("kills", s.kills());
        row.put("deaths", s.deaths());
        row.put("assists", s.assists());
        row.put("damage_dealt", s.damageDealt());
        row.put("damage_taken", s.damageTaken());
        return row;
    }

    public List<MatchPlayerStats> findByMatch(UUID matchId) {
        return find("match_id = :match_id", Map.of("match_id", matchId.toString()));
    }
}
