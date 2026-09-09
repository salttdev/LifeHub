package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.UpsertRepository;
import dev.saltt.hub.database.domains.SurvivalGamesPlayer;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SurvivalGamesPlayerRepository extends UpsertRepository<SurvivalGamesPlayer> {

    public SurvivalGamesPlayerRepository(Jdbi jdbi) {
        super(jdbi, "survival_games_match_player",
                List.of("match_id", "player_uuid"),
                List.of("match_id", "player_uuid", "placement", "time_alive", "killer_uuid",
                        "team_uuid", "cause_of_death"));
    }

    @Override
    protected Map<String, Object> toRow(SurvivalGamesPlayer p) {
        Map<String, Object> row = new HashMap<>();
        row.put("match_id", p.matchId().toString());
        row.put("player_uuid", p.playerUuid().toString());
        row.put("placement", p.placement());
        row.put("time_alive", p.timeAlive());
        row.put("killer_uuid", p.killerUuid() == null ? null : p.killerUuid().toString());
        row.put("team_uuid", p.teamId() == null ? null : p.teamId().toString());
        row.put("cause_of_death", p.causeOfDeath());
        return row;
    }
}
