package dev.saltt.hub.database.results;

import dev.saltt.hub.database.domains.LifeMatch;
import dev.saltt.hub.database.domains.MatchPlayerStats;
import dev.saltt.hub.database.repos.LifeMatchRepository;
import dev.saltt.hub.database.repos.MatchPlayerStatsGameFlushRepository;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchResult;
import dev.saltt.life.protocol.PlayerResult;
import org.jdbi.v3.core.Jdbi;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Turns one MatchResult into rows, in one transaction.
 *
 * <p>Everything common to every game type is written here; anything a single game type adds goes
 * in a {@link MatchResultSink} registered against that type. Adding a game mode is then a new sink
 * and a new table, with nothing to change in this class or in the rpc that calls it.
 */
public final class MatchResultWriter {

    private static final Logger LOG = Logger.getLogger(MatchResultWriter.class.getName());

    private final Jdbi jdbi;
    private final LifeMatchRepository matches;
    private final MatchPlayerStatsGameFlushRepository stats;
    private final Map<GameType, MatchResultSink> sinks = new EnumMap<>(GameType.class);

    public MatchResultWriter(Jdbi jdbi, LifeMatchRepository matches,
                             MatchPlayerStatsGameFlushRepository stats) {
        this.jdbi = jdbi;
        this.matches = matches;
        this.stats = stats;
    }

    /** Later registrations for the same game type replace earlier ones. */
    public MatchResultWriter register(MatchResultSink sink) {
        sinks.put(sink.gameType(), sink);
        return this;
    }

    /**
     * @param nodeId which instancer ran it, from the hub's own record. Not on the wire, so null
     *               when the hub never dispatched this match itself.
     */
    public void write(MatchResult result, @Nullable String nodeId) {
        UUID matchId = UUID.fromString(result.getMatchId());
        MatchResultSink sink = sinks.get(result.getGameType());

        if (sink == null) {
            LOG.warning("no result sink for " + result.getGameType() + ", writing only the "
                    + "common rows for match " + matchId);
        }

        jdbi.useTransaction(handle -> {
            matches.save(handle, toMatch(result, matchId, nodeId));

            for (PlayerResult player : result.getPlayersList()) {
                stats.save(handle, toStats(matchId, player));
            }
            if (sink != null) {
                sink.write(handle, result);
            }
        });
    }

    private static LifeMatch toMatch(MatchResult result, UUID matchId, @Nullable String nodeId) {
        return new LifeMatch(
                matchId,
                result.getGameType().name(),
                emptyToNull(result.getMapName()),
                nodeId,
                Instant.ofEpochMilli(result.getTimeStartMillis()),
                result.getTimeEndMillis() > 0
                        ? Instant.ofEpochMilli(result.getTimeEndMillis()) : Instant.now(),
                result.getAbandoned(),
                uuidOrNull(result.getWinnerUuid()));
    }

    /** MatchResult has no assists field, so that column keeps its default. */
    private static MatchPlayerStats toStats(UUID matchId, PlayerResult player) {
        return new MatchPlayerStats(
                matchId,
                UUID.fromString(player.getPlayerUuid()),
                player.getKills(),
                player.getDeaths(),
                0,
                player.getDamageDealt(),
                player.getDamageTaken());
    }

    @Nullable
    static UUID uuidOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    @Nullable
    private static String emptyToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }
}
