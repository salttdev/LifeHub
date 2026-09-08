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

/** Gives up the player's place in the match that still holds them. */
public final class ForfeitCommand extends AbstractPlayerCommand {

    @Nullable
    private final MatchmakingService matchmaking;

    public ForfeitCommand(@Nullable MatchmakingService matchmaking) {
        super("forfeit", "Give up your place in the match you left");
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
        if (!matchmaking.matches().isInMatch(sender.getUuid())) {
            context.sendMessage(Message.raw("You aren't in a match."));
            return;
        }
        matchmaking.forfeit(sender.getUuid()).thenAccept(ok -> sender.sendMessage(Message.raw(ok
                ? "You forfeited your match. You can queue again."
                : "Your match didn't confirm the forfeit, but you're free to queue again.")));
    }
}
