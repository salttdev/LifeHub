package dev.saltt.hub.database;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Insert-or-update of one row type, keyed on the table's primary key. */
public abstract class UpsertRepository<T> {

    protected final Jdbi jdbi;
    private final String upsertSql;

    protected UpsertRepository(Jdbi jdbi, String table, List<String> keyColumns, List<String> columns) {
        this.jdbi = jdbi;
        this.upsertSql = buildUpsert(table, keyColumns, columns);
    }

    /** Column name -> value. Use HashMap when any value may be null. */
    protected abstract Map<String, Object> toRow(T entity);

    private static String buildUpsert(String table, List<String> keyColumns, List<String> columns) {
        String cols = String.join(", ", columns);
        String vals = columns.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
        List<String> nonKey = columns.stream().filter(c -> !keyColumns.contains(c)).toList();

        if (nonKey.isEmpty()) {
            return "INSERT IGNORE INTO " + table + " (" + cols + ") VALUES (" + vals + ")";
        }
        String updates = nonKey.stream()
                .map(c -> c + " = VALUES(" + c + ")")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ") "
                + "ON DUPLICATE KEY UPDATE " + updates;
    }

    public void save(T entity) {
        jdbi.useHandle(h -> save(h, entity));
    }

    /** Participates in a caller-managed transaction. */
    public void save(Handle handle, T entity) {
        handle.createUpdate(upsertSql).bindMap(toRow(entity)).execute();
    }
}
