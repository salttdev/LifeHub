package dev.saltt.hub;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.saltt.hub.database.Database;
import dev.saltt.hub.database.repos.LifeMatchRepository;
import dev.saltt.hub.database.repos.MatchPlayerStatsGameFlushRepository;
import dev.saltt.hub.database.repos.PlayerRepository;
import dev.saltt.hub.database.repos.SurvivalGamesPlayerGameFlushRepository;
import dev.saltt.hub.database.results.MatchResultWriter;
import dev.saltt.hub.database.results.SurvivalGamesResultSink;
import dev.saltt.hub.grpc.LifePlayerServiceImpl;
import dev.saltt.hub.grpc.MatchmakerServiceImpl;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.commands.QueueCommand;
import dev.saltt.hub.matchmaking.commands.SpectateCommand;
import io.grpc.Server;
// The shaded transport is what grpc-netty-shaded ships, and it is the only one on the classpath.
// ServerBuilder.forPort has no address form, so binding a specific interface needs this directly.
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static Main instance;

    private final Config<HubConfig> config = this.withConfig("LifeHub", HubConfig.CODEC);

    private Database database;
    private PlayerService playerService;

    private Server grpcServer;
    private ExecutorService grpcExecutor;

    private PlayerRepository playerRepo;
    private MatchResultWriter matchResults;
    private MatchmakingService matchmaking;

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("Hello from %s version %s",
                this.getName(), this.getManifest().getVersion().toString());
    }

    public static Main getInstance() { return instance; }
    public PlayerService players() { return playerService; }
    public MatchmakingService matchmaking() { return matchmaking; }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[LifeHub] Setting up...");

        config.save();
        HubConfig cfg = config.get();

        this.database = Database.connect(cfg);

        this.playerRepo = new PlayerRepository(database.jdbi());
        this.playerService = new PlayerService(playerRepo);

        // One writer for every game type: the common rows here, the per-mode table in a sink.
        this.matchResults = new MatchResultWriter(
                database.jdbi(),
                new LifeMatchRepository(database.jdbi()),
                new MatchPlayerStatsGameFlushRepository(database.jdbi()))
                .register(new SurvivalGamesResultSink(
                        new SurvivalGamesPlayerGameFlushRepository(database.jdbi())));

        this.matchmaking = new MatchmakingService(config);

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        getCommandRegistry().registerCommand(new QueueCommand(matchmaking));
        getCommandRegistry().registerCommand(new SpectateCommand(matchmaking));

        LOGGER.at(Level.INFO).log("[LifeHub] Setup complete");
    }

    @Override
    protected void start() {
        HubConfig cfg = config.get();
        try {
            this.grpcExecutor = Executors.newFixedThreadPool(cfg.getApiThreads());

            // Bound to one interface rather than the wildcard address, so a firewall rule that is
            // mistyped or lost on a host rebuild does not silently expose an unauthenticated port.
            InetSocketAddress bind = new InetSocketAddress(cfg.getApiBind(), cfg.getApiPort());
            if (bind.isUnresolved()) {
                // Netty's own failure for this names neither the value nor where it came from.
                throw new IllegalStateException("ApiBind '" + cfg.getApiBind()
                        + "' could not be resolved; use an address on this host, or 0.0.0.0");
            }

            this.grpcServer = NettyServerBuilder.forAddress(bind)
                    .executor(grpcExecutor)
                    .addService(new LifePlayerServiceImpl(playerRepo))
                    .addService(new MatchmakerServiceImpl(matchmaking, matchResults))
                    .build()
                    .start();

            LOGGER.at(Level.INFO).log("[LifeHub] gRPC server listening on "
                    + cfg.getApiBind() + ":" + cfg.getApiPort());
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[LifeHub] Failed to start gRPC server on "
                    + cfg.getApiBind() + ":" + cfg.getApiPort() + ": " + e.getMessage());
        }

        // After the server is up: a match placed before the instancers can report back would beat
        // its way into the cache with nowhere to answer.
        matchmaking.start();
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[LifeHub] Shutting down...");

        // Reverse order: stop placing matches, stop accepting calls, drain, then close the pool.
        if (matchmaking != null) matchmaking.close();

        if (grpcServer != null) {
            try {
                grpcServer.shutdown().awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                grpcServer.shutdownNow();
            }
        }
        if (grpcExecutor != null) grpcExecutor.shutdown();
        if (playerService != null) playerService.close();
        if (database != null) database.close();

        instance = null;
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        Store<EntityStore> store = ref.getStore();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        var handler = playerRef.getPacketHandler();

        InetSocketAddress address =
                (InetSocketAddress) handler.getChannel().remoteAddress();

        String ip = address.getAddress().getHostAddress();

        playerService.onJoin(playerRef.getUuid(), playerRef.getUsername(), ip);

        // They are on the hub, so whatever match the cache still holds them in is over for them.
        matchmaking.onPlayerReady(playerRef.getUuid());
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef().getReference();
        Store<EntityStore> store = ref.getStore();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        playerService.onLeave(playerRef.getUuid());

        // Includes players leaving for an instancer: the referral already took them out, and this
        // covers anyone who quit while queued.
        matchmaking.onPlayerDisconnect(playerRef.getUuid());
    }
}
