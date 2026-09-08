package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.saltt.hub.matchmaking.MatchmakingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Sends a player back to the match that still holds them. */
public final class RejoinCommand extends AbstractPlayerCommand {

    @Nullable
    private final MatchmakingService matchmaking;

    public RejoinCommand(@Nullable MatchmakingService matchmaking) {
        super("rejoin", "Go back to the match you left");
        this.matchmaking = matchmaking;
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
        context.sendMessage(Message.raw(switch (matchmaking.rejoin(sender.getUuid())) {
            case SENT -> "Sending you back to your match...";
            case NOT_IN_MATCH -> "You aren't in a match.";
            case MATCH_OVER -> "That match is over.";
            case NODE_UNKNOWN -> "Your match can't be reached right now.";
        }));
    }
}
