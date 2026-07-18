package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A Block Studio workspace: one or more components, one of them active.
 * v1 workspace files were a single doc's JSON; {@link #fromJson} wraps
 * those as a one-component workspace verbatim, and every save writes the
 * v2 shape ({@code version: 2, active, components: [...]}) — the
 * forward-only migration idiom. The workspace is never empty: removing
 * the last component replaces it with a fresh doc.
 */
public final class BlockWorkspace {

    private final List<BlockDoc> components = new ArrayList<>();
    private int active;

    public BlockWorkspace() {
        components.add(new BlockDoc());
    }

    private BlockWorkspace(List<BlockDoc> docs, int active) {
        components.addAll(docs);
        this.active = Math.max(0, Math.min(active, components.size() - 1));
    }

    public List<BlockDoc> components() {
        return java.util.Collections.unmodifiableList(components);
    }

    public int active() {
        return active;
    }

    public BlockDoc activeDoc() {
        return components.get(active);
    }

    /** Every component's tag, in order — the switcher's row model. */
    public List<String> tags() {
        List<String> out = new ArrayList<>(components.size());
        for (BlockDoc d : components) {
            out.add(d.root().param("tag"));
        }
        return out;
    }

    /** Selects a component; refuses (false, no change) out of range. */
    public boolean setActive(int index) {
        if (index < 0 || index >= components.size()) {
            return false;
        }
        active = index;
        return true;
    }

    /**
     * Adds a fresh component with a tag no existing component carries
     * ({@code my-widget}, {@code my-widget-2}, ...), makes it active,
     * and returns its doc.
     */
    public BlockDoc add() {
        List<String> taken = tags();
        String tag = "my-widget";
        for (int n = 2; taken.contains(tag); n++) {
            tag = "my-widget-" + n;
        }
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", tag);
        components.add(doc);
        active = components.size() - 1;
        return doc;
    }

    /**
     * Removes the component at {@code index}; refuses out of range. The
     * workspace is never left empty — removing the last component
     * replaces it with a fresh doc — and the active index stays valid.
     */
    public boolean remove(int index) {
        if (index < 0 || index >= components.size()) {
            return false;
        }
        components.remove(index);
        if (components.isEmpty()) {
            components.add(new BlockDoc());
        }
        if (active > index || active >= components.size()) {
            active = Math.max(0, Math.min(
                    active > index ? active - 1 : active, components.size() - 1));
        }
        return true;
    }

    /** Swaps the active slot's doc (undo restore rides this). */
    public void replaceActive(BlockDoc doc) {
        components.set(active, doc);
    }

    /** The index of the component whose tag matches, or -1. */
    public int indexOfTag(String tag) {
        List<String> t = tags();
        return t.indexOf(tag);
    }

    // ---- persistence ----

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("version", 2);
        o.put("active", active);
        JSONArray arr = new JSONArray();
        for (BlockDoc d : components) {
            arr.put(d.toJson());
        }
        o.put("components", arr);
        return o;
    }

    /**
     * Rebuilds a workspace. A v2 object ({@code components} array) loads
     * each doc through {@link BlockDoc#fromJson} — the interlock law
     * re-checks free; anything else is treated as a v1 single-doc file
     * and wrapped verbatim. An empty components array, like every other
     * illegal shape, throws rather than half-loads.
     */
    public static BlockWorkspace fromJson(JSONObject o) {
        JSONArray arr = o.optJSONArray("components");
        if (arr == null) {
            List<BlockDoc> one = new ArrayList<>();
            one.add(BlockDoc.fromJson(o));
            return new BlockWorkspace(one, 0);
        }
        if (arr.length() == 0) {
            throw new IllegalArgumentException("components must not be empty");
        }
        List<BlockDoc> docs = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            docs.add(BlockDoc.fromJson(arr.getJSONObject(i)));
        }
        return new BlockWorkspace(docs, o.optInt("active", 0));
    }
}
