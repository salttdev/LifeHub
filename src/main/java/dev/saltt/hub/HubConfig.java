package dev.saltt.hub;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.region.RegionConfig;
import dev.saltt.life.protocol.GameType;

import java.util.List;

// Saved as JSON under: mods/<Group>_<Name>/LifeHub.json  (keys MUST be capitalized)
public class HubConfig {

    /**
     * Binds inside a container that has no interface of its own, so a specific address here fails
     * outright and loopback is only reachable from the same container. Exposure is controlled by
     * the host's allocation and firewall instead.
     */
    private static final String DEFAULT_API_BIND = "0.0.0.0";

    private static final int DEFAULT_MATCHMAKER_INTERVAL_MILLIS = 1_000;
    private static final int DEFAULT_MATCH_STALE_AFTER_SECONDS = 30;
    private static final int DEFAULT_SETUP_MATCH_DEADLINE_SECONDS = 15;
    private static final int DEFAULT_REFER_TIMEOUT_SECONDS = 20;
    private static final int DEFAULT_GROUPING_DEPTH = 16;
    private static final int DEFAULT_LATENCY_BASE_MILLIS = 10;
    private static final double DEFAULT_LATENCY_MILLIS_PER_KM = 0.02;
    private static final String DEFAULT_HUB_REGION = "na";
    private static final String DEFAULT_GEOIP_PATH = "GeoLite2-City.mmdb";

    private static final RegionConfig[] DEFAULT_REGIONS = {
            new RegionConfig("na", 40.7, -74.0),
            new RegionConfig("eu", 50.1, 8.7)
    };

    private static final InstancerNode[] DEFAULT_NODES = {
            new InstancerNode("instancer-1", "127.0.0.1", 50051, "127.0.0.1", 5530, 10, "na")
    };

    private static final QueueRules[] DEFAULT_QUEUE_RULES = {
            new QueueRules(GameType.SURVIVAL_GAMES)
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
            // Shared with every node: signs the transfer tickets players carry to a node.
            .append(new KeyedCodec<String>("ApiToken", Codec.STRING),
                    (c, v, info) -> c.apiToken = v,   (c, info) -> c.apiToken).add()
            .append(new KeyedCodec<Integer>("ApiThreads", Codec.INTEGER),
                    (c, v, info) -> c.apiThreads = v, (c, info) -> c.apiThreads).add()

            // ----- Regions -----
            .append(new KeyedCodec<RegionConfig[]>("Regions",
                            new ArrayCodec<>(RegionConfig.CODEC, RegionConfig[]::new)),
                    (c, v, info) -> c.regions = v == null ? DEFAULT_REGIONS : v,
                    (c, info) -> c.regions).add()
            // Region this hub itself runs in, so a player's ping to the hub is a sample for it.
            .append(new KeyedCodec<String>("HubRegion", Codec.STRING),
                    (c, v, info) -> c.hubRegion = v, (c, info) -> c.hubRegion).add()
            .append(new KeyedCodec<String>("GeoIpDatabasePath", Codec.STRING),
                    (c, v, info) -> c.geoIpDatabasePath = v, (c, info) -> c.geoIpDatabasePath).add()
            .append(new KeyedCodec<Integer>("LatencyBaseMillis", Codec.INTEGER),
                    (c, v, info) -> c.latencyBaseMillis = v == null ? DEFAULT_LATENCY_BASE_MILLIS : v,
                    (c, info) -> c.latencyBaseMillis).add()
            .append(new KeyedCodec<Double>("LatencyMillisPerKm", Codec.DOUBLE),
                    (c, v, info) -> c.latencyMillisPerKm = v == null ? DEFAULT_LATENCY_MILLIS_PER_KM : v,
                    (c, info) -> c.latencyMillisPerKm).add()
            .append(new KeyedCodec<Boolean>("AllowCrossRegionFallback", Codec.BOOLEAN),
                    (c, v, info) -> c.allowCrossRegionFallback = v == null || v,
                    (c, info) -> c.allowCrossRegionFallback).add()
            // Queue size from which a lobby is built out of players who share a best region.
            .append(new KeyedCodec<Integer>("GroupingDepth", Codec.INTEGER),
                    (c, v, info) -> c.groupingDepth = v == null ? DEFAULT_GROUPING_DEPTH : v,
                    (c, info) -> c.groupingDepth).add()

            // ----- Matchmaking -----
            .append(new KeyedCodec<InstancerNode[]>("InstancerNodes",
                            new ArrayCodec<>(InstancerNode.CODEC, InstancerNode[]::new)),
                    (c, v, info) -> c.instancerNodes = v == null ? DEFAULT_NODES : v,
                    (c, info) -> c.instancerNodes).add()
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
            // How long a referred player may still be on the hub before they count as not gone.
            .append(new KeyedCodec<Integer>("ReferTimeoutSeconds", Codec.INTEGER),
                    (c, v, info) -> c.referTimeoutSeconds =
                            v == null ? DEFAULT_REFER_TIMEOUT_SECONDS : v,
                    (c, info) -> c.referTimeoutSeconds).add()
            .build();

    private String jdbcUrl    = "jdbc:mariadb://localhost:3306/life?allowPublicKeyRetrieval=true";
    private String dbUser     = "life";
    private String dbPassword = "change-me";
    private int    dbPoolSize = 10;
    private String apiBind    = DEFAULT_API_BIND;
    private int    apiPort    = 8080;
    private String apiToken   = "change-me-to-a-long-random-secret";
    private int    apiThreads = 4;

    private RegionConfig[] regions = DEFAULT_REGIONS;
    private String hubRegion = DEFAULT_HUB_REGION;
    private String geoIpDatabasePath = DEFAULT_GEOIP_PATH;
    private int latencyBaseMillis = DEFAULT_LATENCY_BASE_MILLIS;
    private double latencyMillisPerKm = DEFAULT_LATENCY_MILLIS_PER_KM;
    private boolean allowCrossRegionFallback = true;
    private int groupingDepth = DEFAULT_GROUPING_DEPTH;

    private InstancerNode[] instancerNodes = DEFAULT_NODES;
    private QueueRules[] queueRules = DEFAULT_QUEUE_RULES;
    private int matchmakerIntervalMillis = DEFAULT_MATCHMAKER_INTERVAL_MILLIS;
    private int matchStaleAfterSeconds = DEFAULT_MATCH_STALE_AFTER_SECONDS;
    private int setupMatchDeadlineSeconds = DEFAULT_SETUP_MATCH_DEADLINE_SECONDS;
    private int referTimeoutSeconds = DEFAULT_REFER_TIMEOUT_SECONDS;

    public HubConfig() {}

    public String getJdbcUrl()    { return jdbcUrl; }
    public String getDbUser()     { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public int    getDbPoolSize() { return dbPoolSize; }
    public int    getApiPort()    { return apiPort; }
    public String getApiToken()   { return apiToken; }
    public int    getApiThreads() { return apiThreads; }

    public String getApiBind() {
        return apiBind == null || apiBind.isBlank() ? DEFAULT_API_BIND : apiBind.strip();
    }

    public List<RegionConfig> getRegions() {
        return regions == null ? List.of() : List.of(regions);
    }

    public String getHubRegion() {
        return hubRegion == null || hubRegion.isBlank() ? DEFAULT_HUB_REGION : hubRegion.strip();
    }

    public String getGeoIpDatabasePath() {
        return geoIpDatabasePath == null || geoIpDatabasePath.isBlank()
                ? DEFAULT_GEOIP_PATH : geoIpDatabasePath.strip();
    }

    public int getLatencyBaseMillis() {
        return Math.max(0, latencyBaseMillis);
    }

    public double getLatencyMillisPerKm() {
        return latencyMillisPerKm <= 0 ? DEFAULT_LATENCY_MILLIS_PER_KM : latencyMillisPerKm;
    }

    public boolean isAllowCrossRegionFallback() {
        return allowCrossRegionFallback;
    }

    /** 0 disables grouping. */
    public int getGroupingDepth() {
        return Math.max(0, groupingDepth);
    }

    public List<InstancerNode> getInstancerNodes() {
        return instancerNodes == null ? List.of() : List.of(instancerNodes);
    }

    public List<QueueRules> getQueueRules() {
        return queueRules == null ? List.of() : List.of(queueRules);
    }

    public int getMatchmakerIntervalMillis() {
        return matchmakerIntervalMillis <= 0
                ? DEFAULT_MATCHMAKER_INTERVAL_MILLIS : matchmakerIntervalMillis;
    }

    /** Must comfortably exceed the instancer's HeartbeatIntervalSeconds. */
    public int getMatchStaleAfterSeconds() {
        return matchStaleAfterSeconds <= 0
                ? DEFAULT_MATCH_STALE_AFTER_SECONDS : matchStaleAfterSeconds;
    }

    /** SetupMatch builds a world before answering, so this covers a world load, not a round trip. */
    public int getSetupMatchDeadlineSeconds() {
        return setupMatchDeadlineSeconds <= 0
                ? DEFAULT_SETUP_MATCH_DEADLINE_SECONDS : setupMatchDeadlineSeconds;
    }

    public int getReferTimeoutSeconds() {
        return referTimeoutSeconds <= 0 ? DEFAULT_REFER_TIMEOUT_SECONDS : referTimeoutSeconds;
    }
}
