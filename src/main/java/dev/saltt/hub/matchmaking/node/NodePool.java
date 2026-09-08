package dev.saltt.hub.matchmaking.node;

import com.hypixel.hytale.server.core.util.Config;
import dev.saltt.hub.HubConfig;
import dev.saltt.hub.matchmaking.MatchCache;
import dev.saltt.hub.matchmaking.objects.InstancerNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** The instancer nodes from config, and which of them has room for another match. */
public final class NodePool {

    private static final Logger LOG = Logger.getLogger(NodePool.class.getName());

    /** No node in the list has room, or the list is empty. */
    public static final class NoCapacityException extends RuntimeException {
        public NoCapacityException(String message) {
            super(message);
        }
    }

    private final Config<HubConfig> config;
    private final MatchCache matches;

    public NodePool(Config<HubConfig> config, MatchCache matches) {
        this.config = config;
        this.matches = matches;
    }

    /** Read per call, so nodes added to LifeHub.json are picked up without a restart. */
    public List<InstancerNode> nodes() {
        List<InstancerNode> usable = new ArrayList<>();
        for (InstancerNode node : config.get().getInstancerNodes()) {
            if (node.isUsable()) {
                usable.add(node);
            } else {
                LOG.warning("ignoring instancer node with an incomplete entry: " + node);
            }
        }
        return usable;
    }

    public Optional<InstancerNode> byId(String nodeId) {
        for (InstancerNode node : nodes()) {
            if (node.id().equals(nodeId)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /**
     * Nodes with room, emptiest first, so matches spread rather than piling onto whichever node
     * happens to be listed first. The caller walks the list: a node that refuses is not the end of
     * the dispatch, only of that attempt.
     */
    public List<InstancerNode> withCapacity() {
        List<InstancerNode> candidates = new ArrayList<>();
        for (InstancerNode node : nodes()) {
            if (matches.countOnNode(node.id()) < node.maxMatches()) {
                candidates.add(node);
            }
        }
        candidates.sort(Comparator.comparingInt(node -> matches.countOnNode(node.id())));
        return candidates;
    }

    /** Describes why a dispatch has nowhere to go, for the log line and the player's message. */
    public String describeExhaustion() {
        List<InstancerNode> all = nodes();
        if (all.isEmpty()) {
            return "no instancer nodes are configured";
        }
        StringBuilder text = new StringBuilder("every instancer node is full:");
        for (InstancerNode node : all) {
            text.append(' ').append(node.id()).append('=')
                    .append(matches.countOnNode(node.id())).append('/').append(node.maxMatches());
        }
        return text.toString();
    }
}
