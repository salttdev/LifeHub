package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.objects.LiveMatch;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Sends you to watch whichever match a player is in. */
public final class SpectateCommand extends AbstractPlayerCommand {

    private final RequiredArg<UUID> targetArg;

    @Nullable
    private final MatchmakingService matchmaking;

    public SpectateCommand(@Nullable MatchmakingService matchmaking) {
        super("spectate", "Travel to the match a player is in");
        this.matchmaking = matchmaking;

        // By uuid, not PLAYER_REF: the player being watched is on an instancer, not on this hub.
        this.targetArg = withRequiredArg("target", "UUID of the player to watch",
                ArgTypes.UUID);
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

        UUID target = targetArg.get(context);
        if (target == null) {
            context.sendMessage(Message.raw("Give the UUID of the player you want to watch."));
            return;
        }
        if (target.equals(sender.getUuid())) {
            context.sendMessage(Message.raw("You cannot spectate yourself."));
            return;
        }
        if (matchmaking.matches().isInMatch(sender.getUuid())) {
            context.sendMessage(Message.raw("You can't spectate while you're in a match."));
            return;
        }

        LiveMatch match = matchmaking.matches().findByPlayer(target).orElse(null);
        if (match == null) {
            context.sendMessage(Message.raw("That player isn't in a match."));
            return;
        }

        // spectate() reports its own failures, so there is nothing to say on the false path.
        context.sendMessage(Message.raw("Finding you a spectator slot..."));
        matchmaking.spectate(sender.getUuid(), match.matchId());
    }
}
