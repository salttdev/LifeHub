package dev.saltt.hub.matchmaking;

import dev.saltt.hub.HubConfig;
import dev.saltt.hub.TestConfigs;
import dev.saltt.hub.matchmaking.grpc.NodeClient;
import dev.saltt.hub.matchmaking.node.NodePool;
import dev.saltt.hub.matchmaking.objects.InstancerNode;
import dev.saltt.hub.matchmaking.objects.QueuedPlayer;
import dev.saltt.hub.matchmaking.queue.QueueManager;
import dev.saltt.hub.matchmaking.region.GeoIpService;
import dev.saltt.hub.matchmaking.region.LatencyModel;
import dev.saltt.hub.matchmaking.region.RegionSelector;
import dev.saltt.life.protocol.GameType;
import dev.saltt.life.protocol.TransferTicket;
import dev.saltt.life.protocol.TransferTickets;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    private record Referral(UUID player, String nodeId, byte[] payload) {}

    private static final class FakePlayers implements HubPlayers {
        final Set<UUID> online = new HashSet<>();
        final List<String> messages = new ArrayList<>();
        final List<Referral> referrals = new ArrayList<>();

        @Override public boolean isOnline(UUID player) { return online.contains(player); }
        @Override public void message(UUID player, String text) { messages.add(player + ": " + text); }
        @Override public boolean refer(UUID player, InstancerNode node, byte[] payload) {
            if (!online.contains(player)) {
                return false;
            }
            referrals.add(new Referral(player, node.id(), payload));
            return true;
        }
    }

    private static final class FakeClient implements NodeClient {
        final List<String> setupsOn = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();
        final Set<String> refusing = new HashSet<>();

        @Override
        public CompletableFuture<String> setupMatch(InstancerNode node, String matchId, GameType gameType,
                                                    List<QueuedPlayer> players, String mapId,
                                                    int minimumStartingPlayers, String region) {
            setupsOn.add(node.id());
            return refusing.contains(node.id())
                    ? CompletableFuture.failedFuture(new RuntimeException("refused"))
                    : CompletableFuture.completedFuture(matchId);
        }

        @Override public CompletableFuture<Boolean> spectatorReserve(InstancerNode n, UUID s, String m) {
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletableFuture<Boolean> cancelMatch(InstancerNode n, String matchId, String reason) {
            cancelled.add(matchId);
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletableFuture<Boolean> forfeitPlayer(InstancerNode n, String m, UUID p) {
            return CompletableFuture.completedFuture(true);
        }
        @Override public void close() { }
    }

    private final HubConfig config = TestConfigs.twoRegions();
    private final FakePlayers players = new FakePlayers();
    private final FakeClient client = new FakeClient();
    private final QueueManager queues = new QueueManager(() -> config);
    private final MatchCache matches = new MatchCache(30_000);
    private final LatencyModel latency = new LatencyModel(() -> new LatencyModel.Settings(
            config.getRegions(), config.getLatencyBaseMillis(), config.getLatencyMillisPerKm(),
            config.getAcceptablePingMillis()));
    private final Matchmaker matchmaker = new Matchmaker(queues, matches,
            new NodePool(config::getInstancerNodes, matches), client, players, latency,
            new RegionSelector(latency), new Tickets(config::getApiToken), () -> config);

    private UUID queued(double lat, double lon) {
        UUID uuid = UUID.randomUUID();
        players.online.add(uuid);
        latency.setLocation(uuid, new GeoIpService.Coordinates(lat, lon));
        queues.add(GameType.SURVIVAL_GAMES, uuid, "p");
        return uuid;
    }

    @Test
    void placesALobbyOnTheBestRegionAndRefersWithSignedTickets() {
        UUID london = queued(51.5, -0.1);
        UUID paris = queued(48.9, 2.3);

        matchmaker.tick();

        assertEquals(List.of("eu-1"), client.setupsOn);
        assertEquals(2, players.referrals.size());
        for (Referral referral : players.referrals) {
            assertEquals("eu-1", referral.nodeId());
            TransferTicket ticket = TransferTickets.verify(referral.payload(), "test-secret",
                    60_000, System.currentTimeMillis()).orElseThrow();
            assertEquals(referral.player().toString(), ticket.getPlayerUuid());
            assertEquals("eu-1", ticket.getNodeId());
        }
        assertTrue(matches.isInMatch(london) && matches.isInMatch(paris));
        assertEquals(0, queues.queue(GameType.SURVIVAL_GAMES).size());
        assertEquals(2, matchmaker.pendingDispatches().size());
    }

    @Test
    void fallsThroughToTheNextNodeWhenOneRefuses() {
        client.refusing.add("eu-1");
        queued(51.5, -0.1);
        queued(48.9, 2.3);

        matchmaker.tick();

        assertEquals(List.of("eu-1", "na-1"), client.setupsOn);
        assertEquals(1, matches.countOnNode("na-1"));
    }

    @Test
    void putsTheLobbyBackWhenNobodyTakesIt() {
        client.refusing.addAll(Set.of("eu-1", "na-1"));
        UUID a = queued(51.5, -0.1);
        UUID b = queued(48.9, 2.3);

        matchmaker.tick();

        assertEquals(2, queues.queue(GameType.SURVIVAL_GAMES).size());
        assertTrue(!matches.isInMatch(a) && !matches.isInMatch(b));
        assertEquals(0, matches.all().size());
    }

    @Test
    void cancelsAMatchNobodyLeftFor() throws InterruptedException {
        UUID a = queued(51.5, -0.1);
        queued(48.9, 2.3);
        matchmaker.tick();
        String matchId = matches.findByPlayer(a).orElseThrow().matchId();

        Thread.sleep(1_100);
        matchmaker.tick();

        assertEquals(List.of(matchId), client.cancelled);
        assertEquals(0, matches.all().size());
    }

    @Test
    void nudgesAStragglerWhileTheOthersMadeIt() throws InterruptedException {
        UUID straggler = queued(51.5, -0.1);
        UUID gone = queued(48.9, 2.3);
        matchmaker.tick();
        players.online.remove(gone);
        matchmaker.markArrived(Set.of(gone));

        Thread.sleep(1_100);
        matchmaker.tick();

        assertTrue(client.cancelled.isEmpty());
        assertEquals(1, matches.all().size());
        assertTrue(players.messages.stream().anyMatch(m -> m.startsWith(straggler + ": ") && m.contains("/rejoin")));
        assertEquals(0, matchmaker.pendingDispatches().size());
    }
}
