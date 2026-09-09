package dev.saltt.hub.database.domains;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LifePlayer(
        UUID uuid,
        String displayName,
        Instant firstJoin,
        Instant lastJoined,
        List<String> pastNames,
        List<String> usedIps
) {}
