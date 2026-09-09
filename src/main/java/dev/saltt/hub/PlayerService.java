package dev.saltt.hub;

import dev.saltt.hub.database.repos.PlayerRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Records joins off the engine thread. */
public final class PlayerService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PlayerService.class.getName());

    private final PlayerRepository repo;
    private final ExecutorService dbExecutor;

    public PlayerService(PlayerRepository repo) {
        this.repo = repo;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LifeHub-PlayerDB");
            t.setDaemon(true);
            return t;
        });
    }

    public void onJoin(UUID uuid, String currentName, String ip) {
        dbExecutor.execute(() -> {
            try {
                repo.observe(uuid, currentName, ip, Instant.now());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to persist join for " + uuid, e);
            }
        });
    }

    @Override public void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
