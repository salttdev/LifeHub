package dev.saltt.hub;

import com.hypixel.hytale.codec.Codec;          // NOTE: this Codec, not com.mojang or others
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.life.protocol.GameType;

import java.util.List;

// Saved as JSON under: mods/<Group>_<Name>/LifeHub.json  (keys MUST be capitalized)
public class HubConfig {

    /**
     * Binds inside a container that has no interface of its own, so a specific address here fails
     * outright and loopback is only reachable from the same container. Exposure is controlled by
     * the host's allocation and firewall instead. Set this to the allocated address on a host that
     * does give the process its own interface.
     */
    private static final String DEFAULT_API_BIND = "0.0.0.0";

    private static final int DEFAULT_MATCHMAKER_INTERVAL_MILLIS = 1_000;
    private static final int DEFAULT_MATCH_STALE_AFTER_SECONDS = 30;
    private static final int DEFAULT_SETUP_MATCH_DEADLINE_SECONDS = 15;

    private static final InstancerNode[] DEFAULT_NODES = {
            new InstancerNode("instancer-1", "127.0.0.1", 50051, "127.0.0.1", 5530, 10)
    };

    private static final QueueRules[] DEFAULT_QUEUE_RULES = {
            new QueueRules(GameType.SURVIVAL_GAMES, 2, 24, 20, 120)
    };

    public static final BuilderCodec<HubConfig> CODEC = BuilderCodec.builder(HubConfig.class, HubConfig::new)
            .append(new KeyedCodec<String>("JdbcUrl", Codec.STRING),
                    (c, v, info) -> c.jdbcUrl = v,    (c, info) -> c.jdbcUrl).add()
            .append(new KeyedCodec<String>("DbUser", Codec.STRING),
                    (c, v, info) -> c.dbUser = v,     (c, info) -> c.dbUser).add()
            .append(new KeyedCodec<String>("DbPassword", Codec.STRING),
                    (c, v, info) -> c.dbPassword = v, (c, info) -> c.dbPassword).add()
            .append(new KeyedCodec<Integer>("DbPoolSize", Codec.INTEGER),
                    (c, v, info) -> c.dbPoolSize = v, (c, info) -> c.dbPoolSize).add()
            .append(new KeyedCodec<String>("ApiBind", Codec.STRING),
                    (c, v, info) -> c.apiBind = v,    (c, info) -> c.apiBind).add()
            .append(new KeyedCodec<Integer>("ApiPort", Codec.INTEGER),
                    (c, v, info) -> c.apiPort = v,    (c, info) -> c.apiPort).add()
            .append(new KeyedCodec<String>("ApiToken", Codec.STRING),
                    (c, v, info) -> c.apiToken = v,   (c, info) -> c.apiToken).add()
            .append(new KeyedCodec<Integer>("ApiThreads", Codec.INTEGER),
                    (c, v, info) -> c.apiThreads = v, (c, info) -> c.apiThreads).add()

            // ----- Matchmaking -----
            // The instancers this hub can place matches on. Every node needs both addresses: the
            // gRPC one the hub dials, and the one players are referred to.
            .append(new KeyedCodec<InstancerNode[]>("InstancerNodes",
                            new ArrayCodec<>(InstancerNode.CODEC, InstancerNode[]::new)),
                    (c, v, info) -> c.instancerNodes = v == null ? DEFAULT_NODES : v,
                    (c, info) -> c.instancerNodes).add()

            // One entry per game type. A type with no entry falls back to the built-in defaults.
            .append(new KeyedCodec<QueueRules[]>("QueueRules",
                            new ArrayCodec<>(QueueRules.CODEC, QueueRules[]::new)),
                    (c, v, info) -> c.queueRules = v == null ? DEFAULT_QUEUE_RULES : v,
                    (c, info) -> c.queueRules).add()

            .append(new KeyedCodec<Integer>("MatchmakerIntervalMillis", Codec.INTEGER),
                    (c, v, info) -> c.matchmakerIntervalMillis =
                            v == null ? DEFAULT_MATCHMAKER_INTERVAL_MILLIS : v,
                    (c, info) -> c.matchmakerIntervalMillis).add()

            .append(new KeyedCodec<Integer>("MatchStaleAfterSeconds", Codec.INTEGER),
                    (c, v, info) -> c.matchStaleAfterSeconds =
                            v == null ? DEFAULT_MATCH_STALE_AFTER_SECONDS : v,
                    (c, info) -> c.matchStaleAfterSeconds).add()

            .append(new KeyedCodec<Integer>("SetupMatchDeadlineSeconds", Codec.INTEGER),
                    (c, v, info) -> c.setupMatchDeadlineSeconds =
                            v == null ? DEFAULT_SETUP_MATCH_DEADLINE_SECONDS : v,
                    (c, info) -> c.setupMatchDeadlineSeconds).add()
            .build();

    private String jdbcUrl    = "jdbc:mysql://localhost:3306/life?connectionTimeZone=UTC";
    private String dbUser     = "life";
    private String dbPassword = "change-me";
    private int    dbPoolSize = 10;
    private String apiBind    = DEFAULT_API_BIND;
    private int    apiPort    = 8080;
    private String apiToken   = "change-me-to-a-long-random-secret";
    private int    apiThreads = 4;

    private InstancerNode[] instancerNodes = DEFAULT_NODES;
    private QueueRules[] queueRules = DEFAULT_QUEUE_RULES;
    private int matchmakerIntervalMillis = DEFAULT_MATCHMAKER_INTERVAL_MILLIS;
    private int matchStaleAfterSeconds = DEFAULT_MATCH_STALE_AFTER_SECONDS;
    private int setupMatchDeadlineSeconds = DEFAULT_SETUP_MATCH_DEADLINE_SECONDS;

    public HubConfig() {}

    public String getJdbcUrl()    { return jdbcUrl; }
    public String getDbUser()     { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public int    getDbPoolSize() { return dbPoolSize; }
    public int    getApiPort()    { return apiPort; }
    public String getApiToken()   { return apiToken; }
    public int    getApiThreads() { return apiThreads; }

    /** Interface the gRPC server binds to; see DEFAULT_API_BIND. */
    public String getApiBind() {
        return apiBind == null || apiBind.isBlank() ? DEFAULT_API_BIND : apiBind.strip();
    }

    public List<InstancerNode> getInstancerNodes() {
        return instancerNodes == null ? List.of() : List.of(instancerNodes);
    }

    public List<QueueRules> getQueueRules() {
        return queueRules == null ? List.of() : List.of(queueRules);
    }

    /** How often the matchmaking pass runs. Floored at 250ms by the matchmaker itself. */
    public int getMatchmakerIntervalMillis() {
        return matchmakerIntervalMillis <= 0
                ? DEFAULT_MATCHMAKER_INTERVAL_MILLIS : matchmakerIntervalMillis;
    }

    /**
     * How long a match may go without a heartbeat before the hub drops it and frees its players.
     * Must comfortably exceed the instancer's HeartbeatIntervalSeconds.
     */
    public int getMatchStaleAfterSeconds() {
        return matchStaleAfterSeconds <= 0
                ? DEFAULT_MATCH_STALE_AFTER_SECONDS : matchStaleAfterSeconds;
    }

    /** SetupMatch builds a world before answering, so this covers a world load, not a round trip. */
    public int getSetupMatchDeadlineSeconds() {
        return setupMatchDeadlineSeconds <= 0
                ? DEFAULT_SETUP_MATCH_DEADLINE_SECONDS : setupMatchDeadlineSeconds;
    }
}
