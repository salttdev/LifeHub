package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.UpsertRepository;
import dev.saltt.hub.database.domains.MatchPlayerStats;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Map;

public final class MatchPlayerStatsRepository extends UpsertRepository<MatchPlayerStats> {

    public MatchPlayerStatsRepository(Jdbi jdbi) {
        super(jdbi, "match_player_stats",
                List.of("match_id", "player_uuid"),
                List.of("match_id", "player_uuid", "kills", "deaths", "assists",
                        "damage_dealt", "damage_taken"));
    }

    @Override
    protected Map<String, Object> toRow(MatchPlayerStats s) {
        return Map.of(
                "match_id", s.matchId().toString(),
                "player_uuid", s.playerUuid().toString(),
                "kills", s.kills(),
                "deaths", s.deaths(),
                "assists", s.assists(),
                "damage_dealt", s.damageDealt(),
                "damage_taken", s.damageTaken());
    }
}
