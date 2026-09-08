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
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QueueLeaveCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> gameTypeArg;

    @Nullable
    private final MatchmakingService matchmaking;

    public QueueLeaveCommand(@Nullable MatchmakingService matchmaking) {
        super("leave", "Leave a matchmaking queue");
        this.matchmaking = matchmaking;
        this.gameTypeArg = withOptionalArg("game", "Game to leave; all of them if left off",
                ArgTypes.STRING);
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

        // No game named means every queue, so someone in several gets out with one command.
        if (raw == null || raw.isBlank()) {
            context.sendMessage(Message.raw(matchmaking.leaveAll(sender.getUuid())
                    ? "Left the queue."
                    : "You aren't in a queue."));
            return;
        }

        GameType gameType = Matchmaking.parseGameType(raw);
        if (gameType == null) {
            context.sendMessage(Message.raw("Unknown game. Pick one of: "
                    + Matchmaking.describePlayable()));
            return;
        }

        context.sendMessage(Message.raw(matchmaking.leave(sender.getUuid(), gameType)
                ? "Left the " + Matchmaking.label(gameType) + " queue."
                : "You aren't in the " + Matchmaking.label(gameType) + " queue."));
    }
}
