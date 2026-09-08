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
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.queue.GameQueue;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/** Where the player stands, and what every queue looks like. */
public final class QueueStatusCommand extends AbstractPlayerCommand {

    @Nullable
    private final MatchmakingService matchmaking;

    public QueueStatusCommand(@Nullable MatchmakingService matchmaking) {
        super("status", "Show your queue and how full each one is");
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

        Set<GameType> mine = matchmaking.queues().queuedTypes(sender.getUuid());
        if (mine.isEmpty()) {
            context.sendMessage(Message.raw("You aren't in a queue. /queue join "
                    + Matchmaking.describePlayable()));
        }

        for (GameQueue queue : matchmaking.queues().all()) {
            QueueRules rules = queue.rules();
            StringBuilder line = new StringBuilder(mine.contains(queue.gameType()) ? "> " : "  ")
                    .append(Matchmaking.label(queue.gameType()))
                    .append(": ").append(queue.size()).append('/').append(rules.maxPlayers())
                    .append(" (min ").append(rules.minPlayers()).append(')');

            long seconds = queue.secondsUntilDispatch();
            if (seconds >= 0) {
                line.append(", starting in ~").append(seconds).append('s');
            }
            if (mine.contains(queue.gameType())) {
                long waited = matchmaking.queues().waitedMillis(queue.gameType(), sender.getUuid());
                if (waited >= 0) {
                    line.append(", waited ").append(waited / 1000L).append('s');
                }
            }
            context.sendMessage(Message.raw(line.toString()));
        }
    }
}
