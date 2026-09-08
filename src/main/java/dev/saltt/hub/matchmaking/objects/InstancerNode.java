package dev.saltt.hub.matchmaking.objects;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * One instancer process the hub can put matches on.
 *
 * <p>Two addresses, and they are not interchangeable. GrpcHost/GrpcPort is the internal
 * InstancerService the hub dials; PlayerHost/PlayerPort is what a client is referred to.
 */
public class InstancerNode {

    private static final int DEFAULT_GRPC_PORT = 50051;
    private static final int DEFAULT_PLAYER_PORT = 5530;
    private static final int DEFAULT_MAX_MATCHES = 10;

    public static final BuilderCodec<InstancerNode> CODEC = BuilderCodec
            .builder(InstancerNode.class, InstancerNode::new)
            .append(new KeyedCodec<String>("Id", Codec.STRING),
                    (node, value, info) -> node.id = strip(value), (node, info) -> node.id)
            .add()
            .append(new KeyedCodec<String>("GrpcHost", Codec.STRING),
                    (node, value, info) -> node.grpcHost = strip(value), (node, info) -> node.grpcHost)
            .add()
            .append(new KeyedCodec<Integer>("GrpcPort", Codec.INTEGER),
                    (node, value, info) -> node.grpcPort = value == null ? DEFAULT_GRPC_PORT : value,
                    (node, info) -> node.grpcPort)
            .add()
            .append(new KeyedCodec<String>("PlayerHost", Codec.STRING),
                    (node, value, info) -> node.playerHost = strip(value), (node, info) -> node.playerHost)
            .add()
            .append(new KeyedCodec<Integer>("PlayerPort", Codec.INTEGER),
                    (node, value, info) -> node.playerPort = value == null ? DEFAULT_PLAYER_PORT : value,
                    (node, info) -> node.playerPort)
            .add()
            .append(new KeyedCodec<Integer>("MaxMatches", Codec.INTEGER),
                    (node, value, info) -> node.maxMatches = value == null ? DEFAULT_MAX_MATCHES : value,
                    (node, info) -> node.maxMatches)
            .add()
            // Must name an entry in Regions; the node's own RpcConfig.Region should say the same.
            .append(new KeyedCodec<String>("Region", Codec.STRING),
                    (node, value, info) -> node.region = strip(value), (node, info) -> node.region)
            .add()
            .build();

    private String id = "";
    private String grpcHost = "";
    private int grpcPort = DEFAULT_GRPC_PORT;
    private String playerHost = "";
    private int playerPort = DEFAULT_PLAYER_PORT;
    private int maxMatches = DEFAULT_MAX_MATCHES;
    private String region = "";

    public InstancerNode() {
    }

    public InstancerNode(String id, String grpcHost, int grpcPort,
                         String playerHost, int playerPort, int maxMatches, String region) {
        this.id = strip(id);
        this.grpcHost = strip(grpcHost);
        this.grpcPort = grpcPort;
        this.playerHost = strip(playerHost);
        this.playerPort = playerPort;
        this.maxMatches = maxMatches;
        this.region = strip(region);
    }

    public String id() { return id; }
    public String grpcHost() { return grpcHost; }
    public int grpcPort() { return grpcPort; }
    public String playerHost() { return playerHost; }
    public int playerPort() { return playerPort; }
    public int maxMatches() { return maxMatches; }
    public String region() { return region; }

    public boolean isUsable() {
        return !id.isBlank() && !grpcHost.isBlank() && !playerHost.isBlank()
                && isPort(grpcPort) && isPort(playerPort) && maxMatches > 0;
    }

    private static boolean isPort(int port) {
        return port > 0 && port <= 65535;
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }

    @Override
    public String toString() {
        return id + "[" + region + "] (" + grpcHost + ":" + grpcPort + " -> "
                + playerHost + ":" + playerPort + ")";
    }
}
