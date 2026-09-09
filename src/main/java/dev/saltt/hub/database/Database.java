package dev.saltt.hub.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.saltt.hub.HubConfig;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;

public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;

    private Database(HikariDataSource dataSource, Jdbi jdbi) {
        this.dataSource = dataSource;
        this.jdbi = jdbi;
    }

    public static Database connect(HubConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.getJdbcUrl());
        hc.setUsername(cfg.getDbUser());
        hc.setPassword(cfg.getDbPassword());
        hc.setMaximumPoolSize(cfg.getDbPoolSize());
        hc.setPoolName("LifeHub-Hikari");
        HikariDataSource ds = new HikariDataSource(hc);

        // The mod classloader, so classpath:db/migration resolves inside the shaded jar. Migrations
        // are grouped by domain (player/, match/) and ordered by version across both.
        Flyway.configure(Database.class.getClassLoader())
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return new Database(ds, Jdbi.create(ds));
    }

    public Jdbi jdbi() { return jdbi; }

    @Override public void close() { dataSource.close(); }
}
