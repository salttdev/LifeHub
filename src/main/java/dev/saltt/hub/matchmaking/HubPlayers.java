package dev.saltt.hub.matchmaking;

import dev.saltt.hub.matchmaking.objects.InstancerNode;

import java.util.UUID;

/** The players on this hub, as the matchmaker needs them. Implemented on the engine and in tests. */
public interface HubPlayers {

    boolean isOnline(UUID player);

    void message(UUID player, String text);

    /** Sends them to the node's player address with the payload. False if they are not here. */
    boolean refer(UUID player, InstancerNode node, byte[] payload);
}
