package dev.saltt.hub.matchmaking;

import dev.saltt.life.protocol.TransferTicket;
import dev.saltt.life.protocol.TransferTickets;

import java.util.UUID;
import java.util.function.Supplier;

/** Issues the signed ticket a player carries to a node in the referral payload. */
public final class Tickets {

    private final Supplier<String> secret;

    public Tickets(Supplier<String> secret) {
        this.secret = secret;
    }

    public byte[] issue(String matchId, UUID player, String nodeId, boolean spectator) {
        TransferTicket unsigned = TransferTicket.newBuilder()
                .setMatchId(matchId)
                .setPlayerUuid(player.toString())
                .setIssuedAtMillis(System.currentTimeMillis())
                .setNodeId(nodeId)
                .setSpectator(spectator)
                .build();
        return TransferTickets.toPayload(TransferTickets.sign(unsigned, secret.get()));
    }
}
