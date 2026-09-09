package dev.saltt.hub.matchmaking.queue;

import dev.saltt.hub.HubConfig;
import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every queue on this hub, one per playable game type.
 *
 * <p>A player may sit in several at once; the first lobby that fills claims them and
 * {@link #removeFromAll(UUID)} takes them out of the rest.
 */
public final class QueueManager {

    private final Supplier<HubConfig> config;
    private final Map<GameType, GameQueue> queues = new EnumMap<>(GameType.class);

    public QueueManager(Supplier<HubConfig> config) {
        this.config = config;
        for (GameType type : playable()) {
            queues.put(type, new GameQueue(type, () -> rulesFor(type)));
        }
    }

    /** Every game type a lobby can be built for, so GAME_TYPE_UNSPECIFIED and UNRECOGNIZED are out. */
    public static Set<GameType> playable() {
        EnumSet<GameType> types = EnumSet.noneOf(GameType.class);
        for (GameType type : GameType.values()) {
            if (type != GameType.GAME_TYPE_UNSPECIFIED && type != GameType.UNRECOGNIZED) {
                types.add(type);
            }
        }
        return types;
    }

    /** The configured rules, or the built-in defaults when config names no rules for this type. */
    public QueueRules rulesFor(GameType type) {
        for (QueueRules rules : config.get().getQueueRules()) {
            if (rules.gameType() == type) {
                return rules;
            }
        }
        return new QueueRules(type);
    }

    public List<GameQueue> all() {
        return new ArrayList<>(queues.values());
    }

    @Nullable
    public GameQueue queue(GameType type) {
        return queues.get(type);
    }

    public boolean add(GameType type, UUID player, String userName) {
        GameQueue queue = queues.get(type);
        return queue != null && queue.add(player, userName);
    }

    public boolean remove(GameType type, UUID player) {
        GameQueue queue = queues.get(type);
        return queue != null && queue.remove(player);
    }

    /** True when they were in at least one queue. */
    public boolean removeFromAll(UUID player) {
        boolean removed = false;
        for (GameQueue queue : queues.values()) {
            removed |= queue.remove(player);
        }
        return removed;
    }

    public boolean isQueued(UUID player) {
        for (GameQueue queue : queues.values()) {
            if (queue.contains(player)) {
                return true;
            }
        }
        return false;
    }

    /** The types this player is waiting in, for rendering their queue state. */
    public Set<GameType> queuedTypes(UUID player) {
        EnumSet<GameType> types = EnumSet.noneOf(GameType.class);
        for (GameQueue queue : queues.values()) {
            if (queue.contains(player)) {
                types.add(queue.gameType());
            }
        }
        return types;
    }

    /** How long they have waited in this queue, or -1 if they are not in it. */
    public long waitedMillis(GameType type, UUID player) {
        GameQueue queue = queues.get(type);
        if (queue == null) {
            return -1L;
        }
        QueuedPlayer queued = queue.get(player);
        return queued == null ? -1L : queued.waitedMillis();
    }

    public void clearAll() {
        queues.values().forEach(GameQueue::clear);
    }
}
