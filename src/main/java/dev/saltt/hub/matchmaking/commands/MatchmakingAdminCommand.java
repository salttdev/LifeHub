package dev.saltt.hub.matchmaking.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.LiveMatch;
import dev.saltt.hub.matchmaking.region.RegionConfig;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/** /mm nodes, /mm matches, /mm player: what the matchmaker currently believes. Admin only. */
public final class MatchmakingAdminCommand extends AbstractCommandCollection {

    public MatchmakingAdminCommand(@Nullable MatchmakingService matchmaking) {
        super("mm", "Inspect the matchmaker");
        addSubCommand(new Nodes(matchmaking));
        addSubCommand(new Matches(matchmaking));
        addSubCommand(new Player(matchmaking));
    }

    private abstract static class Inspect extends CommandBase {

        @Nullable
        protected final MatchmakingService matchmaking;

        Inspect(String name, String description, @Nullable MatchmakingService matchmaking) {
            super(name, description);
            this.matchmaking = matchmaking;
            setPermissionGroups(HytalePermissionsProvider.GROUP_ADMIN);
        }
    }

    private static final class Nodes extends Inspect {

        Nodes(@Nullable MatchmakingService matchmaking) {
            super("nodes", "Configured nodes and their load", matchmaking);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (Matchmaking.unavailable(context, matchmaking)) {
                return;
            }
            for (InstancerNode node : matchmaking.nodes().nodes()) {
                context.sendMessage(Message.raw(node + " load "
                        + matchmaking.nodes().load(node) + "/" + node.maxMatches()));
            }
        }
    }

    private static final class Matches extends Inspect {

        Matches(@Nullable MatchmakingService matchmaking) {
            super("matches", "Live matches and their players", matchmaking);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (Matchmaking.unavailable(context, matchmaking)) {
                return;
            }
            if (matchmaking.matches().all().isEmpty()) {
                context.sendMessage(Message.raw("No live matches."));
                return;
            }
            for (LiveMatch match : matchmaking.matches().all()) {
                context.sendMessage(Message.raw(match.matchId() + " " + match.gameType() + " on "
                        + match.nodeId() + " " + match.status() + ": " + match.connected().size()
                        + " connected, " + match.claimed().size() + " claimed, "
                        + match.aliveCount() + " alive, last beat "
                        + match.sinceLastBeatMillis() / 1000 + "s ago"));
            }
        }
    }

    private static final class Player extends Inspect {

        private final RequiredArg<String> nameArg;

        Player(@Nullable MatchmakingService matchmaking) {
            super("player", "Where a player stands with the matchmaker", matchmaking);
            this.nameArg = withRequiredArg("name", "Player name", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (Matchmaking.unavailable(context, matchmaking)) {
                return;
            }
            PlayerRef ref = Universe.get().getPlayerByUsername(nameArg.get(context),
                    NameMatching.EXACT_IGNORE_CASE);
            if (ref == null) {
                context.sendMessage(Message.raw("No online player called " + nameArg.get(context) + "."));
                return;
            }
            UUID uuid = ref.getUuid();
            context.sendMessage(Message.raw(ref.getUsername() + ": queued for "
                    + matchmaking.queues().queuedTypes(uuid).stream().map(GameType::name).toList()
                    + ", match " + matchmaking.matches().findByPlayer(uuid)
                            .map(LiveMatch::matchId).orElse("none")));
            context.sendMessage(Message.raw("  location " + matchmaking.latency().location(uuid)
                    .map(at -> at.latitude() + ", " + at.longitude()).orElse("unknown")
                    + ", measured " + measured(uuid)));
            StringBuilder regions = new StringBuilder("  regions:");
            for (RegionConfig region : matchmaking.latency().regions()) {
                OptionalInt rtt = matchmaking.latency().rtt(uuid, region.id());
                regions.append(' ').append(region.id()).append('=')
                        .append(rtt.isPresent() ? rtt.getAsInt() + "ms" : "?");
            }
            context.sendMessage(Message.raw(regions.toString()));
        }

        private String measured(UUID uuid) {
            Map<String, Integer> pings = matchmaking.latency().measured(uuid);
            return pings.isEmpty() ? "none" : pings.toString();
        }
    }
}
