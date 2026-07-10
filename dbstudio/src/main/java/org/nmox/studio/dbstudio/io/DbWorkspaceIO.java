package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * Reads and writes the project's DB Studio state as
 * {@code .nmoxdb.json} beside the project — the file is meant to be
 * committed and shared, so BY CONSTRUCTION it never carries a secret:
 * {@link ConnectionSpec} has no password field, and passwords live only
 * in the OS keychain via
 * {@link org.nmox.studio.dbstudio.engine.Passwords} (mirroring the
 * ATMOS rule and {@code .nmoxapi.json}'s policy). The console
 * {@link HistoryEntry history} and {@link SavedQuery saved queries}
 * added in this schema are the user's own SQL text — shareable by the
 * same standard as the queries themselves.
 *
 * <p>The engine takes an explicit directory {@link File} rather than a
 * project object — the UI decides what "the project dir" is, keeping
 * this class free of platform coupling and trivially testable.
 *
 * <p>Loading is tolerant in both directions: a missing file, malformed
 * JSON, or an unknown engine from a newer NMOX degrade to "less
 * state", never an exception; keys this version doesn't know are
 * ignored (org.json's natural behavior), and files written before the
 * history/saved keys existed load with those lists empty. The
 * {@code version} stamp stays {@code 1} — the schema only ever grew
 * additively.
 */
public final class DbWorkspaceIO {

    public static final String FILENAME = ".nmoxdb.json";

    /** How many history entries the file keeps — the newest 50. */
    public static final int HISTORY_CAP = 50;

    private static final Logger LOG = Logger.getLogger(DbWorkspaceIO.class.getName());

    /**
     * One remembered console run, mirroring the console's History tab.
     *
     * @param text   the console text exactly as executed
     * @param engine display name of the engine it ran against, or ""
     * @param at     epoch millis of the run
     */
    public record HistoryEntry(String text, String engine, long at) {
    }

    /**
     * One named query the user chose to keep.
     *
     * @param name   the user's label — unique within the workspace,
     *               saving under an existing name replaces it
     * @param text   the query text
     * @param engine display name of the engine it targets, or ""
     */
    public record SavedQuery(String name, String text, String engine) {
    }

    /**
     * Everything {@code .nmoxdb.json} holds: connection specs, the run
     * {@code history} (newest first, capped at {@link #HISTORY_CAP} on
     * write and load), and the {@code saved} queries (names unique —
     * a later duplicate replaces the earlier one in place).
     */
    public record Workspace(
            List<ConnectionSpec> connections,
            List<HistoryEntry> history,
            List<SavedQuery> saved) {

        public Workspace {
            connections = List.copyOf(connections);
            history = List.copyOf(history);
            saved = List.copyOf(saved);
        }

        /** A workspace with nothing in it. */
        public static Workspace empty() {
            return new Workspace(List.of(), List.of(), List.of());
        }
    }

    private DbWorkspaceIO() {
    }

    /** Serializes connections only — history and saved queries empty. */
    public static String toJson(List<ConnectionSpec> specs) {
        return toJson(new Workspace(specs, List.of(), List.of()));
    }

    /**
     * Serializes the whole workspace. History keeps its first
     * {@value #HISTORY_CAP} entries (callers keep the list newest-first,
     * as the console's history model does); saved queries are
     * deduplicated by name — the last occurrence wins, holding the
     * first occurrence's position (replace-on-save semantics).
     */
    public static String toJson(Workspace workspace) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray connections = new JSONArray();
        for (ConnectionSpec spec : workspace.connections()) {
            JSONObject cj = new JSONObject();
            cj.put("id", nz(spec.id()));
            cj.put("name", nz(spec.name()));
            cj.put("engine", spec.engine().name());
            cj.put("host", nz(spec.host()));
            cj.put("port", spec.port());
            cj.put("database", nz(spec.database()));
            cj.put("user", nz(spec.user()));
            cj.put("filePath", nz(spec.filePath()));
            connections.put(cj);
        }
        root.put("connections", connections);

        JSONArray history = new JSONArray();
        for (HistoryEntry entry : cappedHistory(workspace.history())) {
            JSONObject hj = new JSONObject();
            hj.put("text", nz(entry.text()));
            hj.put("engine", nz(entry.engine()));
            hj.put("at", entry.at());
            history.put(hj);
        }
        root.put("history", history);

        JSONArray saved = new JSONArray();
        for (SavedQuery query : dedupedByName(workspace.saved())) {
            JSONObject sj = new JSONObject();
            sj.put("name", nz(query.name()));
            sj.put("text", nz(query.text()));
            sj.put("engine", nz(query.engine()));
            saved.put(sj);
        }
        root.put("saved", saved);

        return root.toString(2);
    }

    /** Parses connections; malformed input yields an empty list, never throws. */
    public static List<ConnectionSpec> fromJson(String json) {
        return new ArrayList<>(workspaceFromJson(json).connections());
    }

    /**
     * Parses the whole workspace. Malformed JSON yields
     * {@link Workspace#empty()}; missing keys yield empty lists (a
     * pre-history file loads fine); unknown keys are ignored (a file
     * from a newer NMOX loads fine); entries missing their essential
     * field (a connection's engine, a history entry's text, a saved
     * query's name) are skipped, keeping the rest.
     */
    public static Workspace workspaceFromJson(String json) {
        if (json == null || json.isBlank()) {
            return Workspace.empty();
        }
        try {
            return parseStrict(json);
        } catch (RuntimeException malformed) {
            // the message carries the parse position; the stack adds nothing
            LOG.log(Level.WARNING, "Malformed {0}; starting with an empty workspace ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return Workspace.empty();
        }
    }

    /** The parse itself — throws on malformed JSON so guarded callers can react. */
    private static Workspace parseStrict(String json) {
        JSONObject root = new JSONObject(json);
        return new Workspace(
                connections(root.optJSONArray("connections")),
                cappedHistory(history(root.optJSONArray("history"))),
                dedupedByName(saved(root.optJSONArray("saved"))));
    }

    private static List<ConnectionSpec> connections(JSONArray array) {
        List<ConnectionSpec> specs = new ArrayList<>();
        if (array == null) {
            return specs;
        }
        for (int i = 0; i < array.length(); i++) {
            ConnectionSpec spec = connection(array.getJSONObject(i));
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private static List<HistoryEntry> history(JSONArray array) {
        List<HistoryEntry> entries = new ArrayList<>();
        if (array == null) {
            return entries;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject hj = array.getJSONObject(i);
            String text = hj.optString("text", "");
            if (text.isBlank()) {
                continue; // an entry without text remembers nothing
            }
            entries.add(new HistoryEntry(text, hj.optString("engine", ""), hj.optLong("at", 0L)));
        }
        return entries;
    }

    private static List<SavedQuery> saved(JSONArray array) {
        List<SavedQuery> queries = new ArrayList<>();
        if (array == null) {
            return queries;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject sj = array.getJSONObject(i);
            String name = sj.optString("name", "");
            if (name.isBlank()) {
                continue; // the name is the key — nothing to file it under
            }
            queries.add(new SavedQuery(name, sj.optString("text", ""),
                    sj.optString("engine", "")));
        }
        return queries;
    }

    private static ConnectionSpec connection(JSONObject cj) {
        DbEngine engine;
        try {
            engine = DbEngine.valueOf(cj.optString("engine", ""));
        } catch (IllegalArgumentException unknownEngine) {
            // an engine from a newer NMOX: skip this connection, keep the rest
            return null;
        }
        String id = cj.optString("id", "");
        if (id.isBlank()) {
            id = UUID.randomUUID().toString(); // heal a hand-edited file
        }
        return new ConnectionSpec(
                id,
                cj.optString("name", "connection"),
                engine,
                cj.optString("host", ""),
                cj.optInt("port", -1),
                cj.optString("database", ""),
                cj.optString("user", ""),
                cj.optString("filePath", ""));
    }

    /**
     * Writes {@code .nmoxdb.json} into the given project directory,
     * replacing the connections but PRESERVING whatever history and
     * saved queries the existing file holds — a connections-only caller
     * must not wipe the user's query shelf.
     */
    public static void save(File dir, List<ConnectionSpec> specs) throws IOException {
        Workspace existing = readWorkspace(new File(dir, FILENAME));
        save(dir, new Workspace(specs, existing.history(), existing.saved()));
    }

    /** Writes the whole workspace as {@code .nmoxdb.json} into the directory. */
    public static void save(File dir, Workspace workspace) throws IOException {
        // atomic rename, never truncate-then-write: the workspace watcher
        // (and any foreign reader) must never observe a torn .nmoxdb.json —
        // the other three studios' writers went atomic in v1.39; this one
        // was missed and matters more now that writes run off the EDT
        org.nmox.studio.core.util.AtomicFiles.writeString(
                new File(dir, FILENAME).toPath(), toJson(workspace));
    }

    /**
     * Loads connections from the given project directory. A missing or
     * unreadable or malformed file loads as an empty list — never throws.
     */
    public static List<ConnectionSpec> load(File dir) {
        return new ArrayList<>(loadWorkspace(dir).connections());
    }

    /**
     * Loads the whole workspace from the given project directory. A
     * missing, unreadable or malformed file loads as
     * {@link Workspace#empty()} — never throws.
     */
    public static Workspace loadWorkspace(File dir) {
        return readWorkspace(new File(dir, FILENAME));
    }

    /**
     * A guarded load: the {@code workspace} is never null (empty on any
     * failure, like {@link #loadWorkspace}); {@code backup} is non-null
     * when the file EXISTED but failed to parse and was copied aside.
     */
    public record LoadOutcome(Workspace workspace, File backup) {
    }

    /**
     * Loads like {@link #loadWorkspace}, but guards the user's file
     * against the corrupt-load → empty-model → save-clobbers-original
     * sequence: when {@code .nmoxdb.json} exists and fails to parse,
     * the unreadable original is copied to {@code .nmoxdb.json.bak}
     * BEFORE the empty fallback is returned, so the studio's next save
     * can never destroy the only copy. Missing/unreadable files make no
     * backup. Never throws.
     */
    public static LoadOutcome loadWorkspaceGuarded(File dir) {
        File file = new File(dir, FILENAME);
        if (!file.isFile()) {
            return new LoadOutcome(Workspace.empty(), null);
        }
        String json;
        try {
            json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return new LoadOutcome(Workspace.empty(), null);
        }
        if (json.isBlank()) {
            return new LoadOutcome(Workspace.empty(), null); // nothing to lose
        }
        try {
            return new LoadOutcome(parseStrict(json), null);
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return new LoadOutcome(Workspace.empty(), backupCorrupt(file));
        }
    }

    /** Copies the corrupt file to {@code <name>.bak}; null when even that fails. */
    private static File backupCorrupt(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not back up corrupt " + file, e);
            return null;
        }
    }

    private static Workspace readWorkspace(File file) {
        if (!file.isFile()) {
            return Workspace.empty();
        }
        try {
            return workspaceFromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return Workspace.empty();
        }
    }

    private static List<HistoryEntry> cappedHistory(List<HistoryEntry> history) {
        return history.size() <= HISTORY_CAP ? history : history.subList(0, HISTORY_CAP);
    }

    private static List<SavedQuery> dedupedByName(List<SavedQuery> queries) {
        Map<String, SavedQuery> byName = new LinkedHashMap<>();
        for (SavedQuery query : queries) {
            byName.put(query.name(), query); // last wins, first position kept
        }
        return new ArrayList<>(byName.values());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
