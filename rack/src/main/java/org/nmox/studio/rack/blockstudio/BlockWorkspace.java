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

    /** What {@link #renameActive} did — the UI speaks each case. */
    public enum RenameOutcome { RENAMED, INVALID, TAKEN, NO_CHANGE }

    /** A rename's result: the outcome plus how many sibling references moved. */
    public record Rename(RenameOutcome outcome, int refsUpdated) {
    }

    /**
     * Renames the active component's tag AND follows every reference
     * (v1.268.0 — the organize-gesture sweep's third instance, sharper
     * than the first two: an F2 rename already EXISTED via the generic
     * param editor, but it silently orphaned every sibling component's
     * Element piece still naming the old tag — composed previews
     * 404'd — and nothing stopped two components from colliding on one
     * tag, which makes the preview harness's double
     * {@code customElements.define} throw (the v1.85 hazard).
     * Refusals mutate NOTHING: an invalid tag (the custom-element
     * rules {@link BlockCodegen#validTag}) or a tag another component
     * already carries comes back {@code INVALID}/{@code TAKEN} with
     * zero side effects. On success every OTHER component's ELEMENT
     * blocks whose {@code tag} equals the old tag follow the rename,
     * and the count comes back so the status line can say so.
     */
    public Rename renameActive(String newTag) {
        String oldTag = activeDoc().root().param("tag");
        String tag = newTag == null ? "" : newTag.trim();
        if (tag.equals(oldTag)) {
            return new Rename(RenameOutcome.NO_CHANGE, 0);
        }
        if (!BlockCodegen.validTag(tag)) {
            return new Rename(RenameOutcome.INVALID, 0);
        }
        int existing = indexOfTag(tag);
        if (existing >= 0 && existing != active) {
            return new Rename(RenameOutcome.TAKEN, 0);
        }
        activeDoc().root().setParam("tag", tag);
        int refs = 0;
        for (int i = 0; i < components.size(); i++) {
            if (i == active) {
                continue;
            }
            for (Block b : components.get(i).preorder()) {
                if (b.kind() == BlockKind.ELEMENT && oldTag.equals(b.param("tag"))) {
                    b.setParam("tag", tag);
                    refs++;
                }
            }
        }
        return new Rename(RenameOutcome.RENAMED, refs);
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
