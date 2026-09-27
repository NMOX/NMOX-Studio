package org.nmox.studio.infra.model;

import java.io.File;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;

/**
 * Persists an infrastructure design as JSON in the project directory -
 * the design travels with the repo, exactly like the rack patch.
 */
public final class GraphIO {

    public static final String DEFAULT_FILENAME = ".nmoxinfra.json";

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(GraphIO.class.getName());

    private GraphIO() {
    }

    public static JSONObject toJson(InfraGraph graph) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray nodeArr = new JSONArray();
        for (InfraGraph.InfraNode node : graph.getNodes()) {
            JSONObject nj = new JSONObject();
            nj.put("id", node.id);
            nj.put("kind", node.kind.name());
            nj.put("x", node.x);
            nj.put("y", node.y);
            nj.put("label", node.label);
            nj.put("props", new JSONObject(node.props));
            if (node.doId != null) {
                nj.put("doId", node.doId);
            }
            if (node.ip != null) {
                nj.put("ip", node.ip);
            }
            nodeArr.put(nj);
        }
        root.put("nodes", nodeArr);
        JSONArray wireArr = new JSONArray();
        for (InfraGraph.Wire wire : graph.getWires()) {
            wireArr.put(new JSONObject().put("from", wire.fromId()).put("to", wire.toId()));
        }
        root.put("wires", wireArr);
        return root;
    }

    public static void fromJson(InfraGraph graph, JSONObject root) {
        fromJson(graph, root, new java.util.ArrayList<>());
    }

    /**
     * The parse, noting in {@code newer} every node kind this version does
     * not know. Such a node cannot be shown — and until 3.4 the next save
     * dropped it for everyone who shares the design, with its {@code doId}
     * (the link to a live billed resource) and its wires. A noted design is
     * bound read-only instead.
     */
    static void fromJson(InfraGraph graph, JSONObject root, java.util.List<String> newer) {
        graph.clear();
        JSONArray nodeArr = root.optJSONArray("nodes");
        if (nodeArr == null) {
            return;
        }
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (int i = 0; i < nodeArr.length(); i++) {
            JSONObject nj = nodeArr.getJSONObject(i);
            NodeKind kind;
            try {
                kind = NodeKind.valueOf(nj.getString("kind"));
            } catch (IllegalArgumentException ex) {
                // a kind from a newer version: not shown, and NOTED so the
                // design binds read-only rather than a save deleting it
                newer.add(nj.optString("kind", ""));
                continue;
            }
            // restoreNode is a map put — a duplicated id (a keep-both git
            // merge of .nmoxinfra.json) silently REPLACED the first node,
            // and with it the doId linkage to a live billed resource
            // (Sync would re-create it, Destroy could not find it). The
            // v2.9.0 parse-time-heal law: first occurrence keeps the id
            // (wires resolve to it); later duplicates re-mint and keep
            // their own doId under the new id — nothing is lost.
            String id = nj.getString("id");
            String healed = id;
            for (int n = 2; !seenIds.add(healed); n++) {
                healed = id + "-" + n;
            }
            InfraGraph.InfraNode node = graph.restoreNode(
                    healed, kind, nj.getInt("x"), nj.getInt("y"));
            node.label = nj.optString("label", node.label);
            node.doId = nj.has("doId") ? nj.getString("doId") : null;
            node.ip = nj.has("ip") ? nj.getString("ip") : null;
            JSONObject props = nj.optJSONObject("props");
            if (props != null) {
                for (String key : props.keySet()) {
                    node.props.put(key, props.getString(key));
                }
            }
        }
        JSONArray wireArr = root.optJSONArray("wires");
        if (wireArr != null) {
            for (int i = 0; i < wireArr.length(); i++) {
                JSONObject wj = wireArr.getJSONObject(i);
                InfraGraph.InfraNode from = graph.node(wj.getString("from"));
                InfraGraph.InfraNode to = graph.node(wj.getString("to"));
                if (from != null && to != null) {
                    graph.connect(from, to);
                }
            }
        }
        graph.fireChanged();
    }

    public static void save(InfraGraph graph, File file) throws IOException {
        // atomic rename, never truncate-then-write: external-edit watchers
        // (and other readers) must never observe a torn .nmoxinfra.json
        AtomicFiles.writeString(file.toPath(), toJson(graph).toString(2));
    }

    public static void load(InfraGraph graph, File file) throws IOException {
        // .nmoxinfra.json sits beside the project and travels with a clone
        fromJson(graph, new JSONObject(
                org.nmox.studio.core.util.BoundedReads.read(file.toPath())));
    }

    /**
     * What a guarded load found.
     *
     * <p>{@code backup} is non-null when the file EXISTED but failed to
     * parse and was copied aside first; {@code unreadable} is true when
     * the file EXISTS and its bytes could not be read at all.
     *
     * <p>Those are different failures and were treated as one. A PARSE
     * failure hands back bytes we have seen and kept as {@code .bak}, so
     * the empty graph may replace them. A READ failure — a permission
     * error, a transient fault, or
     * {@link org.nmox.studio.core.util.BoundedReads.TooLarge}, which is
     * an {@link IOException} like any other — hands back nothing at all,
     * and it used to THROW: {@code InfraDesignerTopComponent.load}
     * caught it, cleared the graph, and stamped the file as its own in a
     * {@code finally} that ran on the catch path too, so even the
     * never-clobber guard was disarmed. The next debounced save wrote an
     * empty design over it. Measured through the real designer on an
     * over-cap file: 9,437,184 bytes became 48.
     */
    public record LoadOutcome(File backup, boolean unreadable, boolean conflicted,
            boolean newerFormat) {

        /** The pre-3.4 shape: no conflict, nothing from a newer version. */
        public LoadOutcome(File backup, boolean unreadable) {
            this(backup, unreadable, false, false);
        }

        /**
         * True when the design file must not be written (3.4): it could not
         * be read, it holds git's unresolved merge conflict, or it carries a
         * node kind a newer NMOX Studio wrote.
         */
        public boolean readOnly() {
            return unreadable || conflicted || newerFormat;
        }
    }

    /**
     * Loads, guarding the user's file against the corrupt-load →
     * empty-model → autosave-clobbers-original sequence: when the file
     * exists but fails to parse, the unreadable original is copied to
     * {@code <name>.bak} FIRST, the graph is cleared to the empty
     * fallback, and the backup is named so the UI can say so. A file
     * that cannot be READ leaves the graph empty and comes back marked
     * {@link LoadOutcome#unreadable()} instead, because the caller must
     * not treat a stand-in design as this project's. Never throws.
     */
    public static LoadOutcome loadGuarded(InfraGraph graph, File file) {
        String text;
        try {
            text = org.nmox.studio.core.util.BoundedReads.read(file.toPath());
        } catch (IOException unreadable) {
            // No .bak here, and that is deliberate: the parse failure copies
            // the bytes aside because they are about to be replaced, while a
            // file we could not read must not be written over at all — so
            // there is nothing to rescue it from.
            LOG.log(java.util.logging.Level.WARNING,
                    "Unreadable {0}; the design is read-only until it can be read ({1})",
                    new Object[]{file, unreadable.getMessage()});
            graph.clear();
            return new LoadOutcome(null, true);
        }
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(text)) {
            // git's unresolved merge (3.4): both people's designs — and
            // their links to live billed resources — are IN this file, so it
            // is neither corrupt nor ours to repair. No .bak, no parse; the
            // design is read-only until the conflict is resolved in git, and
            // the external-edit check reloads it then.
            LOG.log(java.util.logging.Level.WARNING,
                    "{0} has unresolved merge conflicts; read-only until resolved", file);
            graph.clear();
            return new LoadOutcome(null, false, true, false);
        }
        try {
            java.util.List<String> newer = new java.util.ArrayList<>();
            fromJson(graph, new JSONObject(text), newer);
            if (!newer.isEmpty()) {
                LOG.log(java.util.logging.Level.WARNING,
                        "{0} holds node kinds a newer NMOX Studio wrote ({1}); read-only",
                        new Object[]{file, newer});
            }
            return new LoadOutcome(null, false, false, !newer.isEmpty());
        } catch (RuntimeException malformed) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{DEFAULT_FILENAME, malformed.getMessage()});
            graph.clear(); // fromJson may have half-populated before throwing
            return new LoadOutcome(backupCorrupt(file), false);
        }
    }

    /**
     * Copies the corrupt file aside — {@code <name>.bak}, or the first free
     * numbered sibling when an earlier rescue holds that name (3.4: a
     * second rescue used to overwrite the first); null when even that fails.
     */
    private static File backupCorrupt(File file) {
        try {
            return org.nmox.studio.core.util.Backups.copyAside(file);
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Could not back up corrupt " + file, e);
            return null;
        }
    }
}
