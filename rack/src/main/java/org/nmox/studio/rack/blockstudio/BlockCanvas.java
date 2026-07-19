package org.nmox.studio.rack.blockstudio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/**
 * The snapping surface: paints the block tree as interlocking rounded
 * pieces (a hue per category, children indented in their parent's
 * mouth) and turns gestures into checked {@link BlockDoc} edits. All
 * geometry decisions live in the pure {@link BlockLayout}; this class
 * is paint + gesture glue and stays coverage-exempt like the other
 * pure-Swing canvases.
 *
 * <p>Gestures: click selects (and drives the code-range highlight),
 * drag moves a piece to the nearest legal slot, dropping a palette
 * kind inserts a new piece, double-click opens the param editor,
 * Delete removes. Illegal targets simply never light up — the
 * interlock law shows itself.
 */
final class BlockCanvas extends JComponent {

    interface Host {

        /** Called before any mutation — push an undo snapshot. */
        void aboutToChange();

        /** Called after any successful mutation. */
        void changed();

        void selected(Block block);

        void editParams(Block block);
    }

    private BlockDoc doc;
    private BlockLayout layout;
    private final Host host;
    private String selectedId;
    private String dragId;
    private BlockLayout.Slot dropPreview;
    private BlockKind paletteDrag;


    BlockCanvas(Host host) {
        this.host = host;
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Block canvas");
        getAccessibleContext().setAccessibleDescription(
                "Interlocking web-component pieces. Arrows traverse (Left parent,"
                + " Right child), Alt+Up/Down reorder, Enter adds a child piece,"
                + " Shift+Enter a sibling, F2 edits, Delete removes, Escape clears;"
                + " or drag from the palette");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                BlockLayout.Row row = layout == null ? null : layout.rowAt(e.getY());
                select(row == null ? null : row.block());
                dragId = row == null || row.block() == doc.root() ? null : row.block().id();
                if (e.getClickCount() == 2 && row != null) {
                    host.editParams(row.block());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragId != null && layout != null) {
                    Block moving = doc.find(dragId);
                    dropPreview = moving == null ? null : layout.slotForMove(e.getY(), moving);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragId != null && dropPreview != null) {
                    host.aboutToChange();
                    if (doc.move(dragId, dropPreview.parent(), dropPreview.index())) {
                        host.changed();
                    }
                }
                dragId = null;
                dropPreview = null;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                BlockKind kind = kindOf(support);
                if (kind == null || layout == null) {
                    paletteDrag = null;
                    // a preview painted by an earlier legal position must
                    // not linger once the drag leaves legal territory
                    if (dropPreview != null) {
                        dropPreview = null;
                        repaint();
                    }
                    return false;
                }
                Point p = support.getDropLocation().getDropPoint();
                paletteDrag = kind;
                dropPreview = layout.slotFor(p.y, kind);
                repaint();
                return dropPreview != null;
            }

            @Override
            public boolean importData(TransferSupport support) {
                BlockKind kind = kindOf(support);
                BlockLayout.Slot slot = dropPreview;
                paletteDrag = null;
                dropPreview = null;
                if (kind == null || slot == null) {
                    repaint();
                    return false;
                }
                host.aboutToChange();
                Block fresh = doc.create(kind);
                boolean ok = doc.insert(slot.parent(), fresh, slot.index());
                if (ok) {
                    select(fresh);
                    host.changed();
                }
                repaint();
                return ok;
            }
        });
    }

    /**
     * The keyboard surface (ledger 48): the canvas is the studio's
     * primary control, so every mouse gesture has a key equivalent —
     * Up/Down walk the pieces, Left/Right walk the tree, Alt+Up/Down
     * reorder within the parent, Enter adds a child piece, Shift+Enter
     * a sibling after, F2 edits params, Delete removes, Escape clears.
     * Package-private: tests drive it with synthesized events.
     */
    void handleKey(KeyEvent e) {
        if (doc == null || layout == null) {
            return;
        }
        Block sel = selectedId == null ? null : doc.find(selectedId);
        boolean alt = (e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0;
        boolean shift = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                if (sel != null && !sel.id().equals(doc.root().id())) {
                    host.aboutToChange();
                    if (doc.detach(sel.id()) != null) {
                        select(null);
                        host.changed();
                    }
                }
            }
            case KeyEvent.VK_UP -> {
                if (alt) {
                    reorder(sel, -1);
                } else {
                    selectRow(rowIndexOf(sel) - 1);
                }
            }
            case KeyEvent.VK_DOWN -> {
                if (alt) {
                    reorder(sel, +1);
                } else {
                    selectRow(sel == null ? 0 : rowIndexOf(sel) + 1);
                }
            }
            case KeyEvent.VK_LEFT -> {
                Block parent = sel == null ? null : doc.parentOf(sel.id());
                if (parent != null) {
                    select(parent);
                    scrollToSelection();
                }
            }
            case KeyEvent.VK_RIGHT -> {
                if (sel != null && !sel.children().isEmpty()) {
                    select(sel.children().get(0));
                    scrollToSelection();
                }
            }
            case KeyEvent.VK_ENTER -> {
                if (sel == null) {
                    return;
                }
                if (shift) {
                    Block parent = doc.parentOf(sel.id());
                    if (parent != null) {
                        showAddMenu(parent, parent.children().indexOf(sel) + 1);
                    }
                } else {
                    showAddMenu(sel, sel.children().size());
                }
            }
            case KeyEvent.VK_F2 -> {
                if (sel != null) {
                    host.editParams(sel);
                }
            }
            case KeyEvent.VK_ESCAPE -> {
                select(null);
                repaint();
            }
            default -> { }
        }
    }

    /** Alt+Up/Down: move the piece within its parent by one slot. */
    private void reorder(Block sel, int direction) {
        if (sel == null) {
            return;
        }
        Block parent = doc.parentOf(sel.id());
        if (parent == null) {
            return;
        }
        int at = parent.children().indexOf(sel);
        // slot indices are computed against the tree WITH the child in
        // place (BlockDoc.move adjusts same-parent downward targets):
        // up = the slot before the previous sibling, down = the slot
        // after the next one
        int target = direction < 0 ? at - 1 : at + 2;
        if (target < 0 || target > parent.children().size()) {
            return;
        }
        host.aboutToChange();
        if (doc.move(sel.id(), parent, target)) {
            host.changed();
            scrollToSelection();
        }
        repaint();
    }

    private int rowIndexOf(Block b) {
        if (b == null) {
            return -1;
        }
        List<BlockLayout.Row> rows = layout.rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).block().id().equals(b.id())) {
                return i;
            }
        }
        return -1;
    }

    private void selectRow(int index) {
        List<BlockLayout.Row> rows = layout.rows();
        if (rows.isEmpty()) {
            return;
        }
        int at = Math.max(0, Math.min(index, rows.size() - 1));
        select(rows.get(at).block());
        scrollToSelection();
        repaint();
    }

    private void scrollToSelection() {
        int i = selectedId == null ? -1 : rowIndexOf(doc.find(selectedId));
        if (i >= 0) {
            BlockLayout.Row r = layout.rows().get(i);
            scrollRectToVisible(new Rectangle(0, r.y(), getWidth(), r.h()));
        }
    }

    /** Kinds the interlock law admits under {@code parent} — menu order. */
    List<BlockKind> legalChildren(Block parent) {
        List<BlockKind> out = new java.util.ArrayList<>();
        for (BlockKind k : BlockKind.values()) {
            if (k != BlockKind.COMPONENT && BlockRules.accepts(parent.kind(), k)) {
                out.add(k);
            }
        }
        return out;
    }

    /** The keyboard insert: same doc path as a palette drop. */
    boolean insertKind(BlockKind kind, Block parent, int index) {
        if (doc == null) {
            // the add-menu popup outlives a re-aim: a menu item clicked
            // after the doc was swapped away must be a no-op, not an NPE
            return false;
        }
        host.aboutToChange();
        Block fresh = doc.create(kind);
        boolean ok = doc.insert(parent, fresh, index);
        if (ok) {
            select(fresh);
            host.changed();
            scrollToSelection();
        }
        repaint();
        return ok;
    }

    private void showAddMenu(Block parent, int index) {
        List<BlockKind> kinds = legalChildren(parent);
        if (kinds.isEmpty()) {
            return;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu("Add piece");
        for (BlockKind k : kinds) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(k.display());
            item.getAccessibleContext().setAccessibleName("Add " + k.display());
            item.addActionListener(ev -> insertKind(k, parent, index));
            menu.add(item);
        }
        int row = rowIndexOf(parent);
        int y = row >= 0 ? layout.rows().get(row).y() + BlockLayout.ROW_H : 0;
        menu.show(this, BlockLayout.INDENT, y);
    }

    // ---- accessible children (ledger 48): pieces visible to AT ----

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleBlockCanvas();
        }
        return accessibleContext;
    }

    private final class AccessibleBlockCanvas extends AccessibleJComponent {

        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LIST;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return layout == null ? 0 : layout.rows().size();
        }

        @Override
        public javax.accessibility.Accessible getAccessibleChild(int i) {
            if (layout == null || i < 0 || i >= layout.rows().size()) {
                return null;
            }
            return new AccessiblePiece(layout.rows().get(i), i);
        }
    }

    /** One piece as assistive tech sees it: kind, summary, position, selection. */
    private final class AccessiblePiece extends javax.accessibility.AccessibleContext
            implements javax.accessibility.Accessible {

        private final BlockLayout.Row row;
        private final int index;

        AccessiblePiece(BlockLayout.Row row, int index) {
            this.row = row;
            this.index = index;
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            return this;
        }

        @Override
        public String getAccessibleName() {
            Block b = row.block();
            return b.kind().display() + " " + b.face()
                    + ", level " + (row.depth() + 1);
        }

        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.LIST_ITEM;
        }

        @Override
        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet set = new javax.accessibility.AccessibleStateSet();
            set.add(javax.accessibility.AccessibleState.VISIBLE);
            set.add(javax.accessibility.AccessibleState.SELECTABLE);
            if (row.block().id().equals(selectedId)) {
                set.add(javax.accessibility.AccessibleState.SELECTED);
            }
            return set;
        }

        @Override
        public int getAccessibleIndexInParent() {
            return index;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Override
        public javax.accessibility.Accessible getAccessibleChild(int i) {
            return null;
        }

        @Override
        public java.util.Locale getLocale() {
            return BlockCanvas.this.getLocale();
        }
    }

    private static BlockKind kindOf(TransferHandler.TransferSupport support) {
        try {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return null;
            }
            String name = (String) support.getTransferable()
                    .getTransferData(DataFlavor.stringFlavor);
            return BlockKind.valueOf(name);
        } catch (Exception ex) {
            return null;
        }
    }

    void setDoc(BlockDoc doc) {
        this.doc = doc;
        this.selectedId = null;
        refresh();
    }

    BlockDoc doc() {
        return doc;
    }

    String selectedId() {
        return selectedId;
    }

    void refresh() {
        layout = doc == null ? null : new BlockLayout(doc);
        setPreferredSize(new Dimension(420, layout == null ? 100 : layout.height() + 40));
        revalidate();
        repaint();
    }

    private void select(Block block) {
        selectedId = block == null ? null : block.id();
        host.selected(block);
        repaint();
    }

    static Color fill(BlockKind.Category c) {
        return switch (c) {
            case STRUCTURE -> new Color(59, 125, 216);
            case CONTENT -> new Color(154, 92, 199);
            case STATE -> new Color(216, 138, 30);
            case LOGIC -> new Color(58, 161, 91);
        };
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(24, 26, 30));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (layout == null) {
            g.setColor(Color.GRAY);
            g.drawString("Aim a project to start composing", 16, 24);
            g.dispose();
            return;
        }
        for (BlockLayout.Row row : layout.rows()) {
            Block b = row.block();
            int x = row.x();
            int w = Math.max(140, getWidth() - x - 16);
            Color fill = fill(b.kind().category());
            g.setColor(b.id().equals(dragId) ? fill.brighter() : fill);
            g.fillRoundRect(x, row.y() + 2, w, row.h() - 6, 12, 12);
            // the interlock notch — pieces read as snapped, not stacked
            g.fillRect(x + 14, row.y() + row.h() - 6, 22, 4);
            if (b.id().equals(selectedId)) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x - 1, row.y() + 1, w + 2, row.h() - 4, 12, 12);
            }
            g.setColor(Color.WHITE);
            g.drawString(b.kind().display(), x + 10, row.y() + 15);
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(b.face(), x + 10, row.y() + 27);
        }
        if (dropPreview != null) {
            g.setColor(new Color(255, 235, 120));
            int x = 8 + dropPreview.depth() * BlockLayout.INDENT;
            g.fillRoundRect(x, dropPreview.y() - 2, Math.max(120, getWidth() - x - 16), 4, 4, 4);
        }
        g.dispose();
    }
}
