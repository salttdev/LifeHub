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
import dev.saltt.hub.database.repos.MatchPlayerStatsRepository;
import dev.saltt.hub.database.repos.PlayerRegionLatencyRepository;
import dev.saltt.hub.database.repos.PlayerRepository;
import dev.saltt.hub.database.repos.SurvivalGamesPlayerRepository;
import dev.saltt.hub.database.results.MatchResultWriter;
import dev.saltt.hub.database.results.SurvivalGamesResultSink;
import dev.saltt.hub.grpc.LifePlayerServiceImpl;
import dev.saltt.hub.grpc.MatchmakerServiceImpl;
import dev.saltt.hub.matchmaking.MatchmakingService;
import dev.saltt.hub.matchmaking.commands.ForfeitCommand;
import dev.saltt.hub.matchmaking.commands.MatchmakingAdminCommand;
import dev.saltt.hub.matchmaking.commands.QueueCommand;
import dev.saltt.hub.matchmaking.commands.RejoinCommand;
import dev.saltt.hub.matchmaking.commands.SpectateCommand;
import dev.saltt.hub.matchmaking.region.GeoIpService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Main extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Config<HubConfig> config = this.withConfig("LifeHub", HubConfig.CODEC);

    private Database database;
    private PlayerService playerService;
    private GeoIpService geoIp;

    private Server grpcServer;
    private ExecutorService grpcExecutor;

    private PlayerRepository playerRepo;
    private MatchResultWriter matchResults;
    private MatchmakingService matchmaking;

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[LifeHub] Setting up...");

        config.save();
        HubConfig cfg = config.get();

        this.database = Database.connect(cfg);

        this.playerRepo = new PlayerRepository(database.jdbi());
        this.playerService = new PlayerService(playerRepo);

        this.matchResults = new MatchResultWriter(
                database.jdbi(),
                new LifeMatchRepository(database.jdbi()),
                new MatchPlayerStatsRepository(database.jdbi()))
                .register(new SurvivalGamesResultSink(
                        new SurvivalGamesPlayerRepository(database.jdbi())));

        Path geoIpPath = Path.of(cfg.getGeoIpDatabasePath());
        if (!geoIpPath.isAbsolute()) {
            geoIpPath = getDataDirectory().resolve(geoIpPath);
        }
        this.geoIp = new GeoIpService(geoIpPath);
        this.matchmaking = new MatchmakingService(config, geoIp,
                new PlayerRegionLatencyRepository(database.jdbi()));

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        getCommandRegistry().registerCommand(new QueueCommand(matchmaking));
        getCommandRegistry().registerCommand(new SpectateCommand(matchmaking));
        getCommandRegistry().registerCommand(new RejoinCommand(matchmaking));
        getCommandRegistry().registerCommand(new ForfeitCommand(matchmaking));
        getCommandRegistry().registerCommand(new MatchmakingAdminCommand(matchmaking));

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
            // Without the API the nodes cannot report back, so a hub without it must not place matches.
            throw new IllegalStateException("[LifeHub] Failed to start gRPC server on "
                    + cfg.getApiBind() + ":" + cfg.getApiPort(), e);
        }

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
        if (geoIp != null) geoIp.close();
        if (database != null) database.close();
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        Store<EntityStore> store = ref.getStore();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        InetSocketAddress address =
                (InetSocketAddress) playerRef.getPacketHandler().getChannel().remoteAddress();
        String ip = address.getAddress().getHostAddress();

        playerService.onJoin(playerRef.getUuid(), playerRef.getUsername(), ip);
        matchmaking.onPlayerReady(playerRef, ip);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef().getReference();
        Store<EntityStore> store = ref.getStore();

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        matchmaking.onPlayerDisconnect(playerRef.getUuid());
    }
}
