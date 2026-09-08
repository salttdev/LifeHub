package dev.saltt.hub.matchmaking.node;

import dev.saltt.hub.matchmaking.MatchCache;
import dev.saltt.hub.matchmaking.objects.InstancerNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** The configured instancer nodes, and which of them has room for another match. */
public final class NodePool {

    private static final Logger LOG = Logger.getLogger(NodePool.class.getName());

    private final Supplier<List<InstancerNode>> configured;
    private final MatchCache matches;

    /** Read per call, so nodes edited in LifeHub.json are picked up without a restart. */
    public NodePool(Supplier<List<InstancerNode>> configured, MatchCache matches) {
        this.configured = configured;
        this.matches = matches;
    }

    public List<InstancerNode> nodes() {
        List<InstancerNode> usable = new ArrayList<>();
        for (InstancerNode node : configured.get()) {
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

    public int load(InstancerNode node) {
        return matches.countOnNode(node.id());
    }

    /**
     * Nodes with room, in the order a dispatch should try them: the best-ranked region's nodes
     * first (emptiest first within a region), then the next region's, and so on. With
     * {@code fallback} off only the best-ranked region's nodes are returned; nodes in a region
     * the ranking does not name come last, or not at all.
     */
    public List<InstancerNode> withCapacity(List<String> rankedRegions, boolean fallback) {
        List<InstancerNode> free = new ArrayList<>();
        for (InstancerNode node : nodes()) {
            if (load(node) < node.maxMatches()) {
                free.add(node);
            }
        }
        free.sort(Comparator.comparingInt(this::load));

        List<InstancerNode> ordered = new ArrayList<>();
        for (String region : rankedRegions) {
            for (InstancerNode node : free) {
                if (node.region().equals(region)) {
                    ordered.add(node);
                }
            }
            if (!fallback) {
                return ordered;
            }
        }
        for (InstancerNode node : free) {
            if (!ordered.contains(node)) {
                ordered.add(node);
            }
        }
        return ordered;
    }

    public String describeExhaustion() {
        List<InstancerNode> all = nodes();
        if (all.isEmpty()) {
            return "no instancer nodes are configured";
        }
        StringBuilder text = new StringBuilder("every instancer node is full:");
        for (InstancerNode node : all) {
            text.append(' ').append(node.id()).append('=')
                    .append(load(node)).append('/').append(node.maxMatches());
        }
        return text.toString();
    }
}
