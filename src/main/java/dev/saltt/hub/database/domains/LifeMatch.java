package dev.saltt.hub.database.domains;

import java.time.Instant;
import java.util.UUID;

/**
 * @param nodeId     which instancer ran it. Null for a match adopted from a heartbeat, where the
 *                   hub never knew.
 * @param winnerUuid null when the match was abandoned or ended with nobody standing.
 */
public record LifeMatch(
        UUID matchId,
        String gameType,
        String mapName,
        String nodeId,
        Instant startedAt,
        Instant endedAt,
        boolean abandoned,
        UUID winnerUuid
) {}
