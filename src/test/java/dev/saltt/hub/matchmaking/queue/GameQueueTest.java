package dev.saltt.hub.matchmaking.queue;

import dev.saltt.hub.matchmaking.objects.QueueRules;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.life.protocol.GameType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameQueueTest {

    private static GameQueue queueOf(QueueRules rules) {
        AtomicReference<QueueRules> holder = new AtomicReference<>(rules);
        return new GameQueue(GameType.SURVIVAL_GAMES, holder::get);
    }

    private static UUID join(GameQueue queue) {
        UUID uuid = UUID.randomUUID();
        queue.add(uuid, "player-" + uuid.toString().substring(0, 4));
        return uuid;
    }

    @Test
    void staysWaitingBelowTheMinimum() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 3, 8, 0, 0));
        join(queue);
        join(queue);

        assertEquals(GameQueue.Readiness.WAITING, queue.readiness());
        assertEquals(-1L, queue.secondsUntilDispatch());
    }

    @Test
    void dispatchesAtOnceWhenTheFillWindowIsZero() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 2, 8, 0, 60));
        join(queue);
        join(queue);

        assertEquals(GameQueue.Readiness.READY, queue.readiness());
    }

    @Test
    void aFullLobbyIgnoresBothClocks() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 2, 3, 600, 600));
        join(queue);
        join(queue);
        assertEquals(GameQueue.Readiness.FILLING, queue.readiness());

        join(queue);
        assertEquals(GameQueue.Readiness.READY, queue.readiness());
    }

    @Test
    void aJoinRestartsTheFillWindowButNotTheCap() throws InterruptedException {
        // Cap of 1s, fill window of 10s: the cap is what eventually fires.
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 2, 8, 10, 1));
        join(queue);
        join(queue);
        assertEquals(GameQueue.Readiness.FILLING, queue.readiness());

        Thread.sleep(600L);
        join(queue);
        assertEquals(GameQueue.Readiness.FILLING, queue.readiness(),
                "the fill window restarted, so nothing should have dispatched yet");

        Thread.sleep(600L);
        assertEquals(GameQueue.Readiness.READY, queue.readiness(),
                "the cap runs from the minimum being reached and a join cannot extend it");
    }

    @Test
    void droppingBelowTheMinimumStopsTheClocks() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 2, 8, 10, 10));
        UUID first = join(queue);
        join(queue);
        assertTrue(queue.secondsUntilDispatch() >= 0);

        queue.remove(first);
        assertEquals(GameQueue.Readiness.WAITING, queue.readiness());
        assertEquals(-1L, queue.secondsUntilDispatch());
    }

    @Test
    void claimTakesTheLongestWaitingFirstAndRemovesThem() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 2, 2, 0, 0));

        UUID oldest = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID newest = UUID.randomUUID();
        queue.add(new QueuedPlayer(oldest, "oldest", 1_000L));
        queue.add(new QueuedPlayer(middle, "middle", 2_000L));
        queue.add(new QueuedPlayer(newest, "newest", 3_000L));

        List<QueuedPlayer> claimed = queue.claim(2);

        assertEquals(List.of(oldest, middle), claimed.stream().map(QueuedPlayer::uuid).toList());
        assertEquals(1, queue.size());
        assertTrue(queue.contains(newest));
    }

    @Test
    void aRestoredPlayerKeepsTheirOriginalQueueTime() {
        GameQueue queue = queueOf(new QueueRules(GameType.SURVIVAL_GAMES, 1, 4, 0, 0));
        QueuedPlayer player = new QueuedPlayer(UUID.randomUUID(), "player", 1_000L);

        queue.add(player);
        List<QueuedPlayer> claimed = queue.claim(1);
        claimed.forEach(queue::add);

        assertEquals(1_000L, queue.get(player.uuid()).queuedAtMillis());
    }
}
