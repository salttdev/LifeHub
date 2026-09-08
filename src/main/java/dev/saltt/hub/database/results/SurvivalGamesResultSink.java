package dev.saltt.hub.database.results;

import dev.saltt.hub.database.domains.SurvivalGamesPlayer;
import dev.saltt.hub.database.repos.SurvivalGamesPlayerGameFlushRepository;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.MatchResult;
import dev.saltt.life.protocol.PlayerResult;
import org.jdbi.v3.core.Handle;

import java.util.UUID;

/**
 * Placement, survival time and who did them in, per participant.
 *
 * <p>The team and cause-of-death columns stay null: MatchResult carries neither yet.
 */
public final class SurvivalGamesResultSink implements MatchResultSink {

    private final SurvivalGamesPlayerGameFlushRepository players;

    public SurvivalGamesResultSink(SurvivalGamesPlayerGameFlushRepository players) {
        this.players = players;
    }

    @Override
    public GameType gameType() {
        return GameType.SURVIVAL_GAMES;
    }

    @Override
    public void write(Handle handle, MatchResult result) {
        UUID matchId = UUID.fromString(result.getMatchId());

        for (PlayerResult player : result.getPlayersList()) {
            players.save(handle, new SurvivalGamesPlayer(
                    matchId,
                    UUID.fromString(player.getPlayerUuid()),
                    player.getPlacement(),
                    player.getTimeAliveMillis(),
                    MatchResultWriter.uuidOrNull(player.getKillerUuid()),
                    null,
                    null));
        }
    }
}
