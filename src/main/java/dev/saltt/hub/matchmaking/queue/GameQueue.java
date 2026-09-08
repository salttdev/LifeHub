package dev.saltt.hub.matchmaking.queue;

import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The players waiting for one game type, and the two clocks that decide when they become a lobby.
 *
 * <p>Nothing happens below the minimum. Crossing it starts both clocks: a fill window that every
 * further join restarts, and a cap measured from the crossing that the restarts cannot extend.
 * Whichever expires first dispatches the lobby, and reaching the maximum dispatches immediately.
 *
 * <p>Rules are read through a supplier rather than held, so an edited LifeHub.json takes effect on
 * the next pass without a restart. Every decision and every claim goes through the monitor, so two
 * matchmaking passes can never hand the same player to two instancers.
 */
public final class GameQueue {

    /** What a pass should do with this queue right now. */
    public enum Readiness {
        /** Below the minimum. */
        WAITING,
        /** At the minimum, still inside one of the two clocks. */
        FILLING,
        /** Dispatch now. */
        READY
    }

    private final GameType gameType;
    private final Supplier<QueueRules> rules;
    private final ConcurrentHashMap<UUID, QueuedPlayer> players = new ConcurrentHashMap<>();

    /** When the queue last crossed the minimum, or -1 while it is below it. */
    private long minimumReachedAtMillis = -1L;

    /** Restarted by every join made while at or above the minimum. */
    private long lastJoinAtMillis = -1L;

    public GameQueue(GameType gameType, Supplier<QueueRules> rules) {
        this.gameType = gameType;
        this.rules = rules;
    }

    public GameType gameType() {
        return gameType;
    }

    public QueueRules rules() {
        return rules.get();
    }

    public int size() {
        return players.size();
    }

    public boolean contains(UUID player) {
        return players.containsKey(player);
    }

    public QueuedPlayer get(UUID player) {
        return players.get(player);
    }

    public Collection<QueuedPlayer> snapshot() {
        return List.copyOf(players.values());
    }

    /** False when they were already queued here, which leaves their original wait intact. */
    public synchronized boolean add(UUID player, String userName) {
        return add(new QueuedPlayer(player, userName, System.currentTimeMillis()));
    }

    /** Re-adds a player at their original queue time, used when a dispatch failed. */
    public synchronized boolean add(QueuedPlayer player) {
        if (players.putIfAbsent(player.uuid(), player) != null) {
            return false;
        }
        onJoin();
        return true;
    }

    public synchronized boolean remove(UUID player) {
        if (players.remove(player) == null) {
            return false;
        }
        onLeave();
        return true;
    }

    /** Starts the clocks on the crossing, and restarts the fill window on every join above it. */
    private void onJoin() {
        long now = System.currentTimeMillis();
        if (players.size() < rules.get().minPlayers()) {
            return;
        }
        if (minimumReachedAtMillis < 0L) {
            minimumReachedAtMillis = now;
        }
        lastJoinAtMillis = now;
    }

    /**
     * Dropping back below the minimum stops both clocks; a lobby that refills starts its wait
     * again rather than inheriting the abandoned one. A leave that stays above the minimum does
     * not touch the fill window, or someone leaving at the wrong moment would stall a lobby that
     * was about to go.
     */
    private void onLeave() {
        if (players.size() < rules.get().minPlayers()) {
            minimumReachedAtMillis = -1L;
            lastJoinAtMillis = -1L;
        }
    }

    public synchronized Readiness readiness() {
        QueueRules current = rules.get();
        int size = players.size();

        if (size < current.minPlayers()) {
            return Readiness.WAITING;
        }
        // A minimum lowered in config while the queue was already above it leaves the clocks
        // unstarted, so start them here rather than waiting for the next join.
        if (minimumReachedAtMillis < 0L) {
            long now = System.currentTimeMillis();
            minimumReachedAtMillis = now;
            lastJoinAtMillis = now;
        }
        if (size >= current.maxPlayers()) {
            return Readiness.READY;
        }

        long now = System.currentTimeMillis();
        boolean fillWindowExpired = now - lastJoinAtMillis >= current.fillWindowMillis();
        boolean capExpired = now - minimumReachedAtMillis >= current.maxWaitAfterMinMillis();

        return fillWindowExpired || capExpired ? Readiness.READY : Readiness.FILLING;
    }

    /** Seconds left on whichever clock fires first, or -1 while the queue is below the minimum. */
    public synchronized long secondsUntilDispatch() {
        if (minimumReachedAtMillis < 0L) {
            return -1L;
        }
        QueueRules current = rules.get();
        long now = System.currentTimeMillis();
        long fillLeft = current.fillWindowMillis() - (now - lastJoinAtMillis);
        long capLeft = current.maxWaitAfterMinMillis() - (now - minimumReachedAtMillis);
        long left = Math.max(0L, Math.min(fillLeft, capLeft));
        return (left + 999L) / 1000L;
    }

    /**
     * Takes up to {@code limit} players out of the queue, longest wait first. Removal is the
     * claim: whatever comes back cannot be handed to another lobby, and putting them back is the
     * caller's job if the instancer refuses.
     */
    public synchronized List<QueuedPlayer> claim(int limit) {
        List<QueuedPlayer> ordered = new ArrayList<>(players.values());
        ordered.sort(Comparator.comparingLong(QueuedPlayer::queuedAtMillis));

        List<QueuedPlayer> claimed = new ArrayList<>(Math.min(limit, ordered.size()));
        for (QueuedPlayer player : ordered) {
            if (claimed.size() >= limit) {
                break;
            }
            if (players.remove(player.uuid()) != null) {
                claimed.add(player);
            }
        }
        onLeave();
        return claimed;
    }

    public synchronized void clear() {
        players.clear();
        minimumReachedAtMillis = -1L;
        lastJoinAtMillis = -1L;
    }
}
