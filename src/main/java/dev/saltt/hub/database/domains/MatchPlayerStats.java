package dev.saltt.hub.database.domains;

import java.util.UUID;

public record MatchPlayerStats(
        UUID matchId,
        UUID playerUuid,
        int kills,
        int deaths,
        int assists,
        long damageDealt,
        long damageTaken
) {}
