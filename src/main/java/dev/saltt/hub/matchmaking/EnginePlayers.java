package dev.saltt.hub.matchmaking;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.saltt.hub.matchmaking.objects.InstancerNode;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EnginePlayers implements HubPlayers {

    private static final Logger LOG = Logger.getLogger(EnginePlayers.class.getName());

    @Override
    public boolean isOnline(UUID player) {
        return ref(player) != null;
    }

    @Override
    public void message(UUID player, String text) {
        PlayerRef ref = ref(player);
        if (ref != null) {
            ref.sendMessage(Message.raw(text));
        }
    }

    @Override
    public boolean refer(UUID player, InstancerNode node, byte[] payload) {
        PlayerRef ref = ref(player);
        if (ref == null) {
            return false;
        }
        try {
            ref.referToServer(node.playerHost(), node.playerPort(), payload);
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "referToServer failed for " + player + " -> " + node, e);
            return false;
        }
    }

    @Nullable
    private static PlayerRef ref(UUID player) {
        Universe universe = Universe.get();
        if (universe == null || player == null) {
            return null;
        }
        PlayerRef ref = universe.getPlayer(player);
        return ref == null || !ref.isValid() ? null : ref;
    }
}
