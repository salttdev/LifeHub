package dev.saltt.hub.matchmaking.objects;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * How one game type fills a lobby.
 *
 * <p>Nothing is dispatched below MinPlayers. Once the queue reaches it, a countdown of
 * FillWindowSeconds runs; every further player who joins restarts that countdown, so a filling
 * lobby keeps waiting for the next arrival. MaxWaitAfterMinSeconds caps the total, measured from
 * the moment the minimum was first reached, so a steady trickle of joiners cannot hold a lobby
 * open forever. Reaching MaxPlayers dispatches at once and ignores both clocks.
 */
public class QueueRules {

    private static final String DEFAULT_GAME_TYPE = "SURVIVAL_GAMES";
    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 24;
    private static final int DEFAULT_FILL_WINDOW_SECONDS = 20;
    private static final int DEFAULT_MAX_WAIT_AFTER_MIN_SECONDS = 120;

    public static final BuilderCodec<QueueRules> CODEC = BuilderCodec
            .builder(QueueRules.class, QueueRules::new)

            .append(new KeyedCodec<String>("GameType", Codec.STRING),
                    (rules, value, info) -> rules.gameType =
                            value == null || value.isBlank() ? DEFAULT_GAME_TYPE : value.strip(),
                    (rules, info) -> rules.gameType)
            .add()

            .append(new KeyedCodec<Integer>("MinPlayers", Codec.INTEGER),
                    (rules, value, info) -> rules.minPlayers =
                            value == null ? DEFAULT_MIN_PLAYERS : value,
                    (rules, info) -> rules.minPlayers)
            .add()

            .append(new KeyedCodec<Integer>("MaxPlayers", Codec.INTEGER),
                    (rules, value, info) -> rules.maxPlayers =
                            value == null ? DEFAULT_MAX_PLAYERS : value,
                    (rules, info) -> rules.maxPlayers)
            .add()

            .append(new KeyedCodec<Integer>("FillWindowSeconds", Codec.INTEGER),
                    (rules, value, info) -> rules.fillWindowSeconds =
                            value == null ? DEFAULT_FILL_WINDOW_SECONDS : value,
                    (rules, info) -> rules.fillWindowSeconds)
            .add()

            .append(new KeyedCodec<Integer>("MaxWaitAfterMinSeconds", Codec.INTEGER),
                    (rules, value, info) -> rules.maxWaitAfterMinSeconds =
                            value == null ? DEFAULT_MAX_WAIT_AFTER_MIN_SECONDS : value,
                    (rules, info) -> rules.maxWaitAfterMinSeconds)
            .add()

            .build();

    private String gameType = DEFAULT_GAME_TYPE;
    private int minPlayers = DEFAULT_MIN_PLAYERS;
    private int maxPlayers = DEFAULT_MAX_PLAYERS;
    private int fillWindowSeconds = DEFAULT_FILL_WINDOW_SECONDS;
    private int maxWaitAfterMinSeconds = DEFAULT_MAX_WAIT_AFTER_MIN_SECONDS;

    public QueueRules() {
    }

    public QueueRules(GameType gameType, int minPlayers, int maxPlayers,
                      int fillWindowSeconds, int maxWaitAfterMinSeconds) {
        this.gameType = gameType.name();
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.fillWindowSeconds = fillWindowSeconds;
        this.maxWaitAfterMinSeconds = maxWaitAfterMinSeconds;
    }

    /** Null when the config names a game type this build of the protocol does not have. */
    @Nullable
    public GameType gameType() {
        try {
            GameType parsed = GameType.valueOf(gameType.strip().toUpperCase(Locale.ROOT));
            return parsed == GameType.GAME_TYPE_UNSPECIFIED || parsed == GameType.UNRECOGNIZED
                    ? null : parsed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The name as written in config, for reporting a game type that did not parse. */
    public String rawGameType() {
        return gameType;
    }

    /** Never below one: a lobby of nobody has nothing to start. */
    public int minPlayers() {
        return Math.max(1, minPlayers);
    }

    /** Never below the minimum, which would make a lobby impossible to dispatch. */
    public int maxPlayers() {
        return Math.max(minPlayers(), maxPlayers);
    }

    /** Restarted by every join once the minimum is met. Zero dispatches as soon as it is. */
    public long fillWindowMillis() {
        return Math.max(0, fillWindowSeconds) * 1_000L;
    }

    /** Total tolerance from the minimum being reached, whatever the fill window is doing. */
    public long maxWaitAfterMinMillis() {
        return Math.max(0, maxWaitAfterMinSeconds) * 1_000L;
    }
}
