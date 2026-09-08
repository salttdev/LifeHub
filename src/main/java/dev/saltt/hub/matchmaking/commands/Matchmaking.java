package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared plumbing for the queue command tree. */
public final class Matchmaking {

    private Matchmaking() {
    }

    /** True when matchmaking is switched off on this node, having already told the player. */
    public static boolean unavailable(CommandContext context, @Nullable MatchmakingService service) {
        if (service == null) {
            context.sendMessage(Message.raw("Matchmaking isn't running on this server."));
            return true;
        }
        return false;
    }

    /** The only playable type when the player named none; null when there is more than one. */
    @Nullable
    public static GameType only() {
        List<GameType> playable = new ArrayList<>(QueueManager.playable());
        return playable.size() == 1 ? playable.get(0) : null;
    }

    /**
     * Matches the protocol's own names, so a game type added to LifeProtocol is typeable here
     * without a change. Null for anything unrecognised.
     */
    @Nullable
    public static GameType parseGameType(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return only();
        }
        String wanted = raw.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (GameType type : QueueManager.playable()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            if (name.equals(wanted) || alias(name).equals(wanted)) {
                return type;
            }
        }
        return null;
    }

    /** SURVIVAL_GAMES -> sg, so the common types have something short to type. */
    private static String alias(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.split("_")) {
            if (!word.isEmpty()) {
                initials.append(word.charAt(0));
            }
        }
        return initials.toString();
    }

    /** "survival_games (sg)", for listing what can be typed. */
    public static String describePlayable() {
        StringBuilder text = new StringBuilder();
        for (GameType type : QueueManager.playable()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            String name = type.name().toLowerCase(Locale.ROOT);
            text.append(name).append(" (").append(alias(name)).append(')');
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    public static String label(GameType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
