package org.nmox.studio.dbstudio.io;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO.HistoryEntry;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO.SavedQuery;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * The pure list edits behind the console's persistent history and
 * saved-query shelf — every rule the UI needs is pinned here by tests,
 * the (coverage-excluded) TopComponent just calls and stores. All
 * methods return NEW lists; inputs are never mutated.
 *
 * <p>History mirrors {@code ConsoleHistory}'s shell semantics:
 * newest first, re-running a text moves it to the front instead of
 * duplicating, capped at {@link DbWorkspaceIO#HISTORY_CAP}. Saved
 * queries are keyed by name — saving under an existing name replaces
 * it in place, matching {@link DbWorkspaceIO}'s serialization
 * contract.
 */
public final class WorkspaceEdits {

    private WorkspaceEdits() {
    }

    /**
     * The history after one console run: the entry moves to the front
     * (replacing any older entry with the same text and engine), the
     * list stays capped at {@link DbWorkspaceIO#HISTORY_CAP}. A blank
     * run remembers nothing — the input list comes back copied,
     * unchanged.
     */
    public static List<HistoryEntry> withRun(List<HistoryEntry> history, HistoryEntry entry) {
        List<HistoryEntry> updated = new ArrayList<>(history);
        if (entry == null || entry.text() == null || entry.text().isBlank()) {
            return updated;
        }
        updated.removeIf(e -> e.text().equals(entry.text())
                && e.engine().equals(entry.engine()));
        updated.add(0, entry);
        while (updated.size() > DbWorkspaceIO.HISTORY_CAP) {
            updated.remove(updated.size() - 1);
        }
        return updated;
    }

    /**
     * The shelf after "Save query…": an existing name is replaced in
     * place (keeping its position), a new name appends. A blank name
     * has nothing to file under — the input comes back copied,
     * unchanged.
     */
    public static List<SavedQuery> withSaved(List<SavedQuery> saved, SavedQuery query) {
        List<SavedQuery> updated = new ArrayList<>(saved);
        if (query == null || query.name() == null || query.name().isBlank()) {
            return updated;
        }
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).name().equals(query.name())) {
                updated.set(i, query);
                return updated;
            }
        }
        updated.add(query);
        return updated;
    }

    /**
     * The default name offered when saving a query: the first line of
     * the text, stripped, capped at 30 characters; a blank text
     * defaults to {@code "query"}.
     */
    public static String defaultName(String queryText) {
        String text = queryText == null ? "" : queryText.strip();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).stripTrailing();
        }
        if (text.isEmpty()) {
            return "query";
        }
        return text.length() <= 30 ? text : text.substring(0, 30);
    }

    /**
     * Whether a {@code .env} suggestion already has a matching
     * workspace connection — same engine and same target: for SQLite
     * the file path (the suggestion carries it in
     * {@link EnvConnections.Suggestion#database()}), for server engines
     * host (case-insensitive) + port + database, with an unset spec
     * port meaning the engine default, exactly as it does when
     * connecting. Specs without a modeled engine (Services entries)
     * never match.
     */
    public static boolean alreadyConfigured(EnvConnections.Suggestion suggestion,
            List<ConnectionSpec> specs) {
        if (suggestion == null) {
            return false;
        }
        for (ConnectionSpec spec : specs) {
            if (spec.engine() != suggestion.engine() || spec.engine() == null) {
                continue;
            }
            if (spec.engine() == DbEngine.SQLITE) {
                if (nz(spec.filePath()).equals(suggestion.database())) {
                    return true;
                }
                continue;
            }
            if (nz(spec.host()).equalsIgnoreCase(suggestion.host())
                    && effectivePort(spec) == suggestion.port()
                    && nz(spec.database()).equals(suggestion.database())) {
                return true;
            }
        }
        return false;
    }

    private static int effectivePort(ConnectionSpec spec) {
        return spec.port() > 0 ? spec.port() : spec.engine().defaultPort();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
