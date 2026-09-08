package dev.saltt.hub.database.domains;

import java.util.UUID;

public record SurvivalGamesPlayer(
        UUID matchId,
        UUID playerUuid,
        int placement,        // 0 when the match was abandoned and nobody placed
        long timeAlive,
        UUID killerUuid,      // nullable
        UUID teamId,          // nullable (team_uuid)
        String causeOfDeath   // nullable
) {}
