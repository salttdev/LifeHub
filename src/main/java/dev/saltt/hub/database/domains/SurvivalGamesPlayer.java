package dev.saltt.hub.database.domains;

import java.util.UUID;

/**
 * @param placement 0 when the match was abandoned and nobody placed
 * @param teamId    always null until MatchResult carries teams
 */
public record SurvivalGamesPlayer(
        UUID matchId,
        UUID playerUuid,
        int placement,
        long timeAlive,
        UUID killerUuid,
        UUID teamId,
        String causeOfDeath
) {}
