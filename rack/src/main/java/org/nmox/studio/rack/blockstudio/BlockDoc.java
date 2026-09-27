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
                || find(child, parent.id()) != null
                // aliasing guard (v1.82.0 review): a block already in the
                // tree must be move()d, never re-insert()ed — two positions
                // sharing one id would corrupt ranges and undo
                || find(child.id()) != null) {
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
        // the caller computed index against the tree WITH the child still
        // in place — after removal, same-parent downward targets shift
        // left by one (the v1.82.0 review's off-by-one: [A,B,C] moving A
        // to the line between B and C must yield [B,A,C], not [B,C,A])
        if (oldParent == newParent && index > oldIndex) {
            index--;
        }
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

    /** The doc format this build writes; a higher {@code version} came from a newer NMOX Studio (3.4). */
    public static final int FORMAT = 1;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("version", FORMAT);
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
        int version = o.optInt("version", FORMAT);
        if (version > FORMAT) {
            throw new BlockWorkspace.NewerFormatException(version, FORMAT);
        }
        BlockDoc doc = new BlockDoc();
        doc.nextId = Math.max(1, o.optInt("nextId", 1));
        Block loaded = blockFrom(o.getJSONObject("root"));
        if (loaded.kind() != BlockKind.COMPONENT) {
            throw new IllegalArgumentException("root must be a COMPONENT block");
        }
        doc.root = loaded;
        doc.healIds();
        return doc;
    }

    /**
     * A piece kind this build does not know (3.4): the file was written by a
     * newer NMOX Studio. Refused like every illegal shape, but typed, because
     * the answer is not "corrupt, start fresh" — it is "leave this file alone":
     * a fresh workspace saved over it would delete the newer build's pieces.
     */
    public static final class UnknownKindException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;
        private final String kind;

        UnknownKindException(String kind) {
            super("unknown piece kind " + kind);
            this.kind = kind;
        }

        /** The kind the file named, as written. */
        public String kind() {
            return kind;
        }
    }

    /**
     * Gives every piece a distinct id (3.4, the parse-time-heal law). Every
     * gesture allocates ids from one counter, but the file is checked in: two
     * people who each added a piece both wrote {@code b3}, and a keep-both
     * merge hands us both. Two pieces on one id collide in the generated
     * code (one {@code data-b} anchor, one {@code const}) and a click on
     * either selects the first. The FIRST occurrence in reading order keeps
     * its id — it is the one the code already names — and every later one
     * gets a fresh id no piece in the file carries; the counter then starts
     * past every id in the file, so the next gesture cannot mint a third.
     */
    private void healIds() {
        java.util.Set<String> all = new java.util.HashSet<>();
        int highest = 0;
        for (Block b : preorder()) {
            all.add(b.id());
            if (b.id().matches("b\\d{1,9}")) {
                highest = Math.max(highest, Integer.parseInt(b.id().substring(1)));
            }
        }
        nextId = Math.max(nextId, highest + 1);
        heal(root, new java.util.HashSet<>(), all);
    }

    private void heal(Block parent, java.util.Set<String> seen, java.util.Set<String> all) {
        seen.add(parent.id());
        List<Block> kids = parent.children();
        for (int i = 0; i < kids.size(); i++) {
            Block child = kids.get(i);
            if (seen.contains(child.id())) {
                String fresh = allocId();
                while (all.contains(fresh)) {
                    fresh = allocId();
                }
                all.add(fresh);
                Block renamed = new Block(fresh, child.kind());
                renamed.params().putAll(child.params());
                renamed.children().addAll(child.children());
                kids.set(i, renamed);
                child = renamed;
            }
            heal(child, seen, all);
        }
    }

    private static Block blockFrom(JSONObject o) {
        String kindName = o.getString("kind");
        BlockKind kind;
        try {
            kind = BlockKind.valueOf(kindName);
        } catch (IllegalArgumentException unknown) {
            throw new UnknownKindException(kindName);
        }
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
