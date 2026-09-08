package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.saltt.hub.matchmaking.MatchmakingService;

import javax.annotation.Nullable;

/** /queue join, /queue leave and /queue status. */
public final class QueueCommand extends AbstractCommandCollection {

    public QueueCommand(@Nullable MatchmakingService matchmaking) {
        super("queue", "Join or leave a matchmaking queue");

        addSubCommand(new QueueJoinCommand(matchmaking));
        addSubCommand(new QueueLeaveCommand(matchmaking));
        addSubCommand(new QueueStatusCommand(matchmaking));
    }
}
