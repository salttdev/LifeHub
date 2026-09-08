package dev.saltt.hub.database.results;

import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchResult;
import org.jdbi.v3.core.Handle;

/**
 * Writes the part of a match result that only one game type has.
 *
 * <p>The common rows (life_match, match_player_stats) are already written by
 * {@link MatchResultWriter} when this runs, inside the same transaction, so a sink can rely on the
 * parent rows existing and should only write its own table.
 */
public interface MatchResultSink {

    /** The game type whose results this sink handles. One sink per type. */
    GameType gameType();

    /** Runs inside the writer's transaction: throw to roll the whole result back. */
    void write(Handle handle, MatchResult result);
}
