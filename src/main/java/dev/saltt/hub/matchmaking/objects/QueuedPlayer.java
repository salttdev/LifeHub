package dev.saltt.hub.matchmaking.objects;

import java.util.UUID;

/**
 * @param queuedAtMillis when they first entered this queue. Preserved across a failed dispatch so
 *                       a player is not sent to the back for a failure that was not theirs.
 */
public record QueuedPlayer(UUID uuid, String userName, long queuedAtMillis) {

    public long waitedMillis() {
        return Math.max(0L, System.currentTimeMillis() - queuedAtMillis);
    }
}
