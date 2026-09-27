package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.io.IOException;
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
 * ATMOS rule and {@code .nmoxapi.json}'s policy). The {@link SavedQuery
 * saved queries} are shared like the connections. The console
 * {@link HistoryEntry history} is not, since 3.4: it is one person's
 * record of what they ran, rewritten on every Run, and it lives in
 * {@link #personalJson} outside the project; a pre-3.4 file's copy is
 * read once so the first load migrates it.
 *
 * <p>A file holding git's unresolved merge conflict, or an engine a newer
 * NMOX wrote, loads read-only through {@link #loadWorkspaceGuarded} (3.4).
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
            cj.put("secure", spec.secure());
            connections.put(cj);
        }
        root.put("connections", connections);
        // 3.4: no "history" here any more. The console history is one
        // person's — every query they ran, SQL text included — and it was
        // rewritten on every Run, so two people sharing the project
        // conflicted on every merge over it. It lives in personalJson,
        // outside the project; a pre-3.4 file's copy is read once.

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

    /** The studio key this workspace's per-person document is filed under. */
    public static final String PERSONAL_STUDIO = "db";

    /**
     * One person's console history (3.4), newest first and capped at
     * {@value #HISTORY_CAP} — written to {@code core.util.PersonalState},
     * never into the committed file.
     */
    public static String personalJson(List<HistoryEntry> history) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray array = new JSONArray();
        for (HistoryEntry entry : cappedHistory(history)) {
            JSONObject hj = new JSONObject();
            hj.put("text", nz(entry.text()));
            hj.put("engine", nz(entry.engine()));
            hj.put("at", entry.at());
            array.put(hj);
        }
        root.put("history", array);
        return root.toString(2);
    }

    /**
     * The history a {@link #personalJson} document holds, or null when
     * there is no document or it does not parse (the caller keeps a
     * pre-3.4 file's legacy copy then).
     */
    public static List<HistoryEntry> personalHistory(String personal) {
        if (personal == null || personal.isBlank()) {
            return null;
        }
        try {
            return new ArrayList<>(cappedHistory(history(
                    new JSONObject(personal).optJSONArray("history"))));
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Ignoring an unreadable personal DB Studio history ({0})",
                    malformed.getMessage());
            return null;
        }
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
        return parseStrict(json, new Heal());
    }

    /**
     * What a parse had to note (3.4): values a newer NMOX Studio wrote that
     * this one cannot model (the workspace must then be bound read-only),
     * and saved queries renamed so a keep-both merge keeps both.
     */
    static final class Heal {
        final List<String> newer = new ArrayList<>();
        final List<String> renamed = new ArrayList<>();
    }

    private static Workspace parseStrict(String json, Heal heal) {
        JSONObject root = new JSONObject(json);
        return new Workspace(
                connections(root.optJSONArray("connections"), heal),
                // legacy per-person field: read so a pre-3.4 file migrates
                cappedHistory(history(root.optJSONArray("history"))),
                healSaved(saved(root.optJSONArray("saved")), heal));
    }

    /**
     * The parse-time heal for saved queries (3.4; the v2.9.0 law). Names
     * are unique at runtime — saving under an existing name replaces it —
     * but a keep-both git merge of {@code .nmoxdb.json} can leave two
     * queries with one name, and until 3.4 the parse kept the LAST and
     * dropped the first with no word to anyone. Now the first keeps its
     * name and a later one with DIFFERENT text is renamed {@code "name
     * (2)"} (the next free number), noted so the studio can say so; an
     * exact copy — same text, same engine — is only a duplicate line, and
     * collapses.
     */
    static List<SavedQuery> healSaved(List<SavedQuery> queries, Heal heal) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (SavedQuery query : queries) {
            taken.add(query.name());
        }
        Map<String, SavedQuery> firstByName = new LinkedHashMap<>();
        List<SavedQuery> out = new ArrayList<>();
        for (SavedQuery query : queries) {
            SavedQuery first = firstByName.putIfAbsent(query.name(), query);
            if (first == null) {
                out.add(query);
                continue;
            }
            if (first.text().equals(query.text()) && first.engine().equals(query.engine())) {
                continue; // the same query twice: nothing to keep apart
            }
            String name = query.name();
            String renamed = name;
            for (int n = 2; taken.contains(renamed); n++) {
                renamed = name + " (" + n + ")";
            }
            taken.add(renamed);
            heal.renamed.add(name + " → " + renamed);
            out.add(new SavedQuery(renamed, query.text(), query.engine()));
        }
        return out;
    }

    private static List<ConnectionSpec> connections(JSONArray array, Heal heal) {
        List<ConnectionSpec> specs = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        if (array == null) {
            return specs;
        }
        for (int i = 0; i < array.length(); i++) {
            ConnectionSpec spec = connection(array.getJSONObject(i), heal);
            if (spec != null) {
                // the missing-id heal above has a twin: a keep-both git
                // merge can DUPLICATE an id, and Passwords is keyed by it
                // (nmox.db.<id>) — removing either connection would wipe
                // the keychain password both resolve. First occurrence
                // keeps the id and the stored password; later duplicates
                // re-mint (the v2.9.0 parse-time-heal law).
                if (!seenIds.add(spec.id())) {
                    spec = spec.withId(UUID.randomUUID().toString());
                }
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

    private static ConnectionSpec connection(JSONObject cj, Heal heal) {
        DbEngine engine;
        String engineName = cj.optString("engine", "");
        try {
            engine = DbEngine.valueOf(engineName);
        } catch (IllegalArgumentException unknownEngine) {
            // an engine from a newer NMOX: this version cannot model the
            // connection, so it is not shown — and it is NOTED, because the
            // next save used to drop it for everyone who shares the file
            // (3.4): a noted workspace is bound read-only instead
            heal.newer.add("engine " + engineName);
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
                cj.optString("filePath", ""),
                // absent in every pre-v1.122.0 file → false, the exact
                // cleartext behavior those workspaces always had (54 L2)
                cj.optBoolean("secure", false));
    }

    /**
     * Writes {@code .nmoxdb.json} into the given project directory,
     * replacing the connections but PRESERVING whatever history and
     * saved queries the existing file holds — a connections-only caller
     * must not wipe the user's query shelf.
     */
    public static void save(File dir, List<ConnectionSpec> specs) throws IOException {
        LoadOutcome guarded = loadWorkspaceGuarded(dir);
        if (guarded.readOnly()) {
            // conflicted, unreadable or newer (3.4): what "the existing
            // saved queries" are cannot be known, so nothing is written
            throw new IOException(FILENAME + " in " + dir
                    + " must not be written (unreadable, conflicted or newer)");
        }
        Workspace existing = guarded.workspace();
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
     * when the file EXISTED but failed to parse and was copied aside;
     * {@code unreadable} is true when the file EXISTS and its bytes could
     * not be read at all.
     *
     * <p>Those last two are different failures and were treated as one.
     * A PARSE failure hands back bytes we have seen and kept as
     * {@code .bak}, so the empty workspace may replace them. A READ
     * failure — a permission error, a transient fault, or
     * {@link org.nmox.studio.core.util.BoundedReads.TooLarge}, which is
     * an {@link IOException} like any other — hands back nothing at all,
     * and the empty workspace it used to come with was indistinguishable
     * from a fresh project: the studio recorded the file as its own and
     * the next change wrote an empty workspace over every connection,
     * every saved query and the whole history, with no backup. Measured
     * on an over-cap file: 9,437,184 bytes became 71.
     */
    public record LoadOutcome(Workspace workspace, File backup, boolean unreadable,
            boolean conflicted, boolean newerFormat, List<String> renamedSaved) {

        public LoadOutcome {
            renamedSaved = renamedSaved == null ? List.of() : List.copyOf(renamedSaved);
        }

        /** The pre-3.4 shape: no conflict, nothing from a newer version, no renames. */
        public LoadOutcome(Workspace workspace, File backup, boolean unreadable) {
            this(workspace, backup, unreadable, false, false, List.of());
        }

        /**
         * True when the file exists and must not be written (3.4): it could
         * not be read, it holds git's unresolved merge conflict, or it names
         * an engine a newer NMOX Studio wrote, which the next save would
         * drop for everyone.
         */
        public boolean readOnly() {
            return unreadable || conflicted || newerFormat;
        }
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
            return new LoadOutcome(Workspace.empty(), null, false);
        }
        String json;
        try {
            // .nmoxdb.json sits beside the project and travels with a clone
            json = org.nmox.studio.core.util.BoundedReads.read(file.toPath());
        } catch (IOException e) {
            // No .bak here, and that is deliberate: the parse failure copies
            // the bytes aside because they are about to be replaced, while
            // a file we could not read must not be written over at all — so
            // there is nothing to rescue it from. The caller binds the
            // workspace read-only instead.
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return new LoadOutcome(Workspace.empty(), null, true);
        }
        if (json.isBlank()) {
            return new LoadOutcome(Workspace.empty(), null, false); // nothing to lose
        }
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(json)) {
            // git's unresolved merge (3.4): both people's connections and
            // queries are IN this file, so it is neither corrupt nor ours
            // to repair — no .bak, no parse; the studio binds read-only and
            // the watcher reloads it once git's conflict is resolved
            LOG.log(Level.WARNING, "{0} has unresolved merge conflicts; read-only until resolved",
                    file);
            return new LoadOutcome(Workspace.empty(), null, false, true, false, List.of());
        }
        try {
            Heal heal = new Heal();
            Workspace parsed = parseStrict(json, heal);
            if (!heal.newer.isEmpty()) {
                LOG.log(Level.WARNING, "{0} holds values a newer NMOX Studio wrote ({1}); read-only",
                        new Object[]{file, heal.newer});
            }
            return new LoadOutcome(parsed, null, false, false, !heal.newer.isEmpty(),
                    heal.renamed);
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return new LoadOutcome(Workspace.empty(), backupCorrupt(file), false);
        }
    }

    /**
     * Copies the corrupt file aside — {@code <name>.bak}, or the first free
     * numbered sibling when an earlier rescue holds that name (3.4: a
     * second rescue used to overwrite the first); null when even that fails.
     */
    private static File backupCorrupt(File file) {
        try {
            return org.nmox.studio.core.util.KeptCopies.copyAside(file);
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
            return workspaceFromJson(org.nmox.studio.core.util.BoundedReads.read(file.toPath()));
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
