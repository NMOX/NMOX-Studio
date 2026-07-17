package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * The canvas geometry, computed purely: the doc flattens to a vertical
 * stack of rows (one per block, children indented Scratch-style), and
 * between rows sit insertion slots — each knowing the parent and index
 * a dropped piece would snap into. The Swing canvas only paints rows
 * and asks {@code slotFor}/{@code rowAt}; every decision that could be
 * wrong lives here, under tests.
 */
public final class BlockLayout {

    public static final int ROW_H = 34;
    public static final int INDENT = 26;
    /** Extra depth painted under a container's last child (its C-mouth). */
    public static final int FOOT_H = 10;

    /** One painted block row. */
    public record Row(Block block, int depth, int y, int h) {

        public int x() {
            return 8 + depth * INDENT;
        }
    }

    /** One legal drop position: snap into {@code parent} at {@code index}. */
    public record Slot(Block parent, int index, int y, int depth) { }

    private final List<Row> rows = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private int height;

    public BlockLayout(BlockDoc doc) {
        int y = layout(doc.root(), 0, 8);
        height = y + ROW_H;
    }

    private int layout(Block b, int depth, int y) {
        rows.add(new Row(b, depth, y, ROW_H));
        y += ROW_H;
        if (BlockRules.container(b.kind())) {
            int index = 0;
            for (Block c : b.children()) {
                slots.add(new Slot(b, index, y, depth + 1));
                y = layout(c, depth + 1, y);
                index++;
            }
            slots.add(new Slot(b, index, y, depth + 1));
            y += FOOT_H;
        }
        return y;
    }

    public List<Row> rows() {
        return rows;
    }

    public int height() {
        return height;
    }

    /** The row under {@code y}; null in a gap or past the end. */
    public Row rowAt(int y) {
        for (Row r : rows) {
            if (y >= r.y() && y < r.y() + r.h()) {
                return r;
            }
        }
        return null;
    }

    /**
     * The nearest slot that ACCEPTS {@code kind}, judged by vertical
     * distance; null when no legal slot exists (an illegal piece never
     * gets a phantom target).
     */
    public Slot slotFor(int y, BlockKind kind) {
        Slot best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Slot s : slots) {
            if (!BlockRules.accepts(s.parent().kind(), kind)) {
                continue;
            }
            int dist = Math.abs(s.y() - y);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    /**
     * Like {@link #slotFor} but for moving an existing block: slots
     * inside the moving block's own subtree are never offered.
     */
    public Slot slotForMove(int y, Block moving) {
        Slot best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Slot s : slots) {
            if (!BlockRules.accepts(s.parent().kind(), moving.kind())
                    || inSubtree(moving, s.parent())) {
                continue;
            }
            int dist = Math.abs(s.y() - y);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private static boolean inSubtree(Block root, Block candidate) {
        if (root == candidate) {
            return true;
        }
        for (Block c : root.children()) {
            if (inSubtree(c, candidate)) {
                return true;
            }
        }
        return false;
    }
}
