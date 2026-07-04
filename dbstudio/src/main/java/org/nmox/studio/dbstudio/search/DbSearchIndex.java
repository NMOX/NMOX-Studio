package org.nmox.studio.dbstudio.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The pure matcher behind DB Studio's Quick Search (⌘I) reach:
 * connections match on name, engine display name, or host; known
 * tables match on table name and carry their owning spec so the UI can
 * jump straight to them. Case-insensitive substring, connection hits
 * before table hits, input order preserved within each group.
 *
 * <p>Deliberately UI-free — the QuickSearch SPI provider wraps this
 * class; that keeps the semantics unit-testable without a platform.
 * Instances are immutable snapshots, safe to share across threads.
 */
public final class DbSearchIndex {

    /** What a hit points at. */
    public enum Kind { CONNECTION, TABLE }

    /**
     * One search hit.
     *
     * @param kind  connection or table
     * @param label ready-to-display text ("staging (PostgreSQL)",
     *              "users — staging")
     * @param spec  the connection — for table hits, the connection the
     *              table lives in
     * @param table the matched table, or null for connection hits
     */
    public record Hit(Kind kind, String label, ConnectionSpec spec, TableInfo table) {
    }

    private final List<ConnectionSpec> specs;
    private final Map<String, List<TableInfo>> tablesBySpecId;

    /**
     * @param specs          the workspace's connections, in display order
     * @param tablesBySpecId tables already discovered per spec id (the
     *                       UI feeds whatever {@code listTables} has
     *                       returned so far); may be null/partial —
     *                       unknown connections simply have no table hits
     */
    public DbSearchIndex(List<ConnectionSpec> specs, Map<String, List<TableInfo>> tablesBySpecId) {
        this.specs = specs == null ? List.of() : List.copyOf(specs);
        Map<String, List<TableInfo>> copy = new LinkedHashMap<>();
        if (tablesBySpecId != null) {
            for (Map.Entry<String, List<TableInfo>> entry : tablesBySpecId.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
        }
        this.tablesBySpecId = copy;
    }

    /**
     * Case-insensitive substring match; blank queries match nothing.
     * Connection hits come first, then table hits, each group in the
     * order the connections were given.
     */
    public List<Hit> matches(String query) {
        List<Hit> hits = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return hits;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (ConnectionSpec spec : specs) {
            if (contains(spec.name(), needle)
                    || contains(spec.engine().displayName(), needle)
                    || contains(spec.host(), needle)) {
                hits.add(new Hit(Kind.CONNECTION,
                        nz(spec.name()) + " (" + spec.engine().displayName() + ")", spec, null));
            }
        }
        for (ConnectionSpec spec : specs) {
            for (TableInfo table : tablesBySpecId.getOrDefault(spec.id(), List.of())) {
                if (contains(table.name(), needle)) {
                    hits.add(new Hit(Kind.TABLE,
                            table.name() + " — " + nz(spec.name()), spec, table));
                }
            }
        }
        return hits;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
