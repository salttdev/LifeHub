package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.queue.GameQueue;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QueueJoinCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> gameTypeArg;

    @Nullable
    private final MatchmakingService matchmaking;

    public QueueJoinCommand(@Nullable MatchmakingService matchmaking) {
        super("join", "Join a matchmaking queue");
        this.matchmaking = matchmaking;
        this.gameTypeArg = withOptionalArg("game", "Game to queue for", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef sender,
                           @Nonnull World world) {

        if (Matchmaking.unavailable(context, matchmaking)) {
            return;
        }

        String raw = gameTypeArg.get(context);
        GameType gameType = Matchmaking.parseGameType(raw);

        if (gameType == null) {
            context.sendMessage(Message.raw((raw == null || raw.isBlank()
                    ? "Name a game to queue for: "
                    : "Unknown game. Pick one of: ") + Matchmaking.describePlayable()));
            return;
        }

        switch (matchmaking.join(sender, gameType)) {
            case QUEUED -> confirm(context, gameType);
            case ALREADY_QUEUED -> context.sendMessage(Message.raw(
                    "You're already queued for " + Matchmaking.label(gameType) + "."));
            case ALREADY_IN_MATCH -> context.sendMessage(Message.raw(
                    "You're still in a match. /rejoin to get back in, or /forfeit to give it up."));
            case UNSUPPORTED_GAME_TYPE -> context.sendMessage(Message.raw(
                    "That game can't be queued for right now."));
        }
    }

    /** Says where the lobby stands, since the wait depends on who else is queued. */
    private void confirm(CommandContext context, GameType gameType) {
        GameQueue queue = matchmaking.queues().queue(gameType);
        QueueRules rules = matchmaking.queues().rulesFor(gameType);

        int size = queue == null ? 1 : queue.size();
        StringBuilder text = new StringBuilder("Queued for ")
                .append(Matchmaking.label(gameType))
                .append(" - ").append(size).append('/').append(rules.maxPlayers())
                .append(" players");

        if (size < rules.minPlayers()) {
            text.append(", waiting for ").append(rules.minPlayers() - size).append(" more");
        } else if (queue != null) {
            long seconds = queue.secondsUntilDispatch();
            if (seconds >= 0) {
                text.append(", starting in ~").append(seconds).append('s');
            }
        }
        context.sendMessage(Message.raw(text.append('.').toString()));
    }
}
