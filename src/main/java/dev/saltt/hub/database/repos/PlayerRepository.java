package dev.saltt.hub.database.repos;

import dev.saltt.hub.database.domains.LifePlayer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerRepository {

    private final Jdbi jdbi;

    public PlayerRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void observe(UUID uuid, String displayName, String ip, Instant when) {
        List<String> ips = (ip == null || ip.isBlank()) ? List.of() : List.of(ip);
        save(new LifePlayer(uuid, displayName, when, when, List.of(), ips));
    }

    public void save(LifePlayer p) {
        jdbi.useTransaction(h -> save(h, p));
    }

    public void save(Handle h, LifePlayer p) {
        LocalDateTime last = ldt(p.lastJoined());
        String uuid = p.uuid().toString();

        h.createUpdate("""
                INSERT INTO life_player (uuid, display_name, first_join, last_joined)
                VALUES (:uuid, :display_name, :first_join, :last_joined)
                ON DUPLICATE KEY UPDATE
                    display_name = COALESCE(VALUES(display_name), display_name),
                    last_joined  = GREATEST(last_joined, VALUES(last_joined))
                """)
                .bind("uuid", uuid)
                .bind("display_name", p.displayName())
                .bind("first_join", ldt(p.firstJoin()))
                .bind("last_joined", last)
                .execute();

        List<String> names = new ArrayList<>();
        if (p.displayName() != null && !p.displayName().isBlank()) names.add(p.displayName());
        for (String n : p.pastNames())
            if (n != null && !n.isBlank() && !names.contains(n)) names.add(n);
        if (!names.isEmpty()) batchHistory(h,
                "life_player_name", "username", uuid, names, last);

        List<String> ips = new ArrayList<>();
        for (String ip : p.usedIps())
            if (ip != null && !ip.isBlank() && !ips.contains(ip)) ips.add(ip);
        if (!ips.isEmpty()) batchHistory(h,
                "life_player_ip", "ip_address", uuid, ips, last);
    }

    private static void batchHistory(Handle h, String table, String col,
                                     String uuid, List<String> values, LocalDateTime when) {
        PreparedBatch b = h.prepareBatch(
                "INSERT INTO " + table + " (player_uuid, " + col + ", first_seen, last_seen) "
                        + "VALUES (:uuid, :val, :seen, :seen) "
                        + "ON DUPLICATE KEY UPDATE last_seen = GREATEST(last_seen, VALUES(last_seen))");
        for (String v : values)
            b.bind("uuid", uuid).bind("val", v).bind("seen", when).add();
        b.execute();
    }

    public Optional<LifePlayer> findByUuid(UUID uuid) {
        return jdbi.withHandle(h -> load(h, uuid.toString()));
    }

    public Optional<LifePlayer> findByName(String username) {
        return jdbi.withHandle(h -> h.createQuery("""
                        SELECT player_uuid FROM life_player_name
                        WHERE username = :u ORDER BY last_seen DESC LIMIT 1
                        """)
                .bind("u", username).mapTo(String.class).findOne()
                .flatMap(id -> load(h, id)));
    }

    private Optional<LifePlayer> load(Handle h, String uuid) {
        Optional<LifePlayer> core = h.createQuery(
                        "SELECT uuid, display_name, first_join, last_joined FROM life_player WHERE uuid = :uuid")
                .bind("uuid", uuid)
                .map((rs, ctx) -> new LifePlayer(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("display_name"),
                        instant(rs.getObject("first_join", LocalDateTime.class)),
                        instant(rs.getObject("last_joined", LocalDateTime.class)),
                        List.of(), List.of()))
                .findOne();
        if (core.isEmpty()) return Optional.empty();
        LifePlayer base = core.get();

        List<String> past = new ArrayList<>();
        for (String n : h.createQuery(
                        "SELECT username FROM life_player_name WHERE player_uuid = :uuid ORDER BY first_seen")
                .bind("uuid", uuid).mapTo(String.class).list())
            if (!n.equals(base.displayName())) past.add(n);

        List<String> ips = h.createQuery(
                        "SELECT ip_address FROM life_player_ip WHERE player_uuid = :uuid ORDER BY first_seen")
                .bind("uuid", uuid).mapTo(String.class).list();

        return Optional.of(new LifePlayer(
                base.uuid(), base.displayName(), base.firstJoin(), base.lastJoined(), past, ips));
    }

    private static LocalDateTime ldt(Instant i) { return LocalDateTime.ofInstant(i, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime l) { return l.toInstant(ZoneOffset.UTC); }
}