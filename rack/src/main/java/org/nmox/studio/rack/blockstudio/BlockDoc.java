package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The document a Block Studio session edits: one COMPONENT root, an id
 * counter, and every structural edit as a checked operation. All edits
 * refuse (returning false) rather than corrupt: the interlock law is
 * enforced HERE, so the canvas and undo can trust any doc they hold.
 *
 * <p>Serializes to the {@code .nmoxblocks.json} shape; round-trip is
 * lossless and pinned by test.
 */
public final class BlockDoc {

    private Block root;
    private int nextId = 1;

    public BlockDoc() {
        root = new Block(allocId(), BlockKind.COMPONENT);
    }

    private String allocId() {
        return "b" + (nextId++);
    }

    public Block root() {
        return root;
    }

    /** A fresh piece of {@code kind}, not yet attached anywhere. */
    public Block create(BlockKind kind) {
        return new Block(allocId(), kind);
    }

    /** Depth-first search by id; null when absent. */
    public Block find(String id) {
        return find(root, id);
    }

    private static Block find(Block b, String id) {
        if (b.id().equals(id)) {
            return b;
        }
        for (Block c : b.children()) {
            Block hit = find(c, id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** The parent of {@code id}; null for the root or an unknown id. */
    public Block parentOf(String id) {
        return parentOf(root, id);
    }

    private static Block parentOf(Block b, String id) {
        for (Block c : b.children()) {
            if (c.id().equals(id)) {
                return b;
            }
            Block hit = parentOf(c, id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Snap {@code child} into {@code parent} at {@code index}. Refuses
     * (false, no change) when the interlock law says no, when either
     * side is unknown, or when the move would put a block inside its
     * own subtree.
     */
    public boolean insert(Block parent, Block child, int index) {
        if (parent == null || child == null
                || !BlockRules.accepts(parent.kind(), child.kind())
                || find(child, parent.id()) != null) {
            return false;
        }
        int at = Math.max(0, Math.min(index, parent.children().size()));
        parent.children().add(at, child);
        return true;
    }

    /** Detach {@code id} from its parent; the removed block, or null. */
    public Block detach(String id) {
        Block parent = parentOf(id);
        if (parent == null) {
            return null;
        }
        Block child = find(id);
        parent.children().remove(child);
        return child;
    }

    /**
     * Move {@code id} to {@code newParent} at {@code index} — one
     * atomic re-snap; on refusal the block stays exactly where it was.
     */
    public boolean move(String id, Block newParent, int index) {
        Block child = find(id);
        Block oldParent = parentOf(id);
        if (child == null || oldParent == null || newParent == null
                || !BlockRules.accepts(newParent.kind(), child.kind())
                || find(child, newParent.id()) != null) {
            return false;
        }
        int oldIndex = oldParent.children().indexOf(child);
        oldParent.children().remove(child);
        int at = Math.max(0, Math.min(index, newParent.children().size()));
        newParent.children().add(at, child);
        if (oldParent == newParent && at == oldIndex) {
            return true; // legal no-op move
        }
        return true;
    }

    /** Every block, preorder — the canvas and tests walk this. */
    public List<Block> preorder() {
        List<Block> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(Block b, List<Block> out) {
        out.add(b);
        for (Block c : b.children()) {
            walk(c, out);
        }
    }

    // ---- persistence ----

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("version", 1);
        o.put("nextId", nextId);
        o.put("root", blockJson(root));
        return o;
    }

    private static JSONObject blockJson(Block b) {
        JSONObject o = new JSONObject();
        o.put("id", b.id());
        o.put("kind", b.kind().name());
        JSONObject params = new JSONObject();
        b.params().forEach(params::put);
        o.put("params", params);
        JSONArray children = new JSONArray();
        for (Block c : b.children()) {
            children.put(blockJson(c));
        }
        o.put("children", children);
        return o;
    }

    /**
     * Rebuilds a doc; unknown kinds or an illegal tree are refused with
     * an IllegalArgumentException (never a half-loaded doc). The
     * interlock law is re-checked on load so a hand-edited file cannot
     * smuggle an illegal nesting past the canvas.
     */
    public static BlockDoc fromJson(JSONObject o) {
        BlockDoc doc = new BlockDoc();
        doc.nextId = Math.max(1, o.optInt("nextId", 1));
        Block loaded = blockFrom(o.getJSONObject("root"));
        if (loaded.kind() != BlockKind.COMPONENT) {
            throw new IllegalArgumentException("root must be a COMPONENT block");
        }
        doc.root = loaded;
        return doc;
    }

    private static Block blockFrom(JSONObject o) {
        BlockKind kind = BlockKind.valueOf(o.getString("kind"));
        Block b = new Block(o.getString("id"), kind);
        JSONObject params = o.optJSONObject("params");
        if (params != null) {
            for (String key : params.keySet()) {
                b.setParam(key, params.getString(key));
            }
        }
        JSONArray children = o.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                Block c = blockFrom(children.getJSONObject(i));
                if (!BlockRules.accepts(kind, c.kind())) {
                    throw new IllegalArgumentException(
                            c.kind() + " cannot nest inside " + kind);
                }
                b.children().add(c);
            }
        }
        return b;
    }
}
