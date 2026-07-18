package org.nmox.studio.rack.blockstudio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        // a bare JComponent has none — without this the canvas is
        // invisible to assistive tech (the widget-library name law)
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.PANEL;
                }
            };
        }
        return accessibleContext;
    }

    BlockCanvas(Host host) {
        this.host = host;
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Block canvas");
        getAccessibleContext().setAccessibleDescription(
                "Interlocking web-component pieces; drag from the palette, Delete removes");
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
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
                        && selectedId != null && doc != null
                        && !selectedId.equals(doc.root().id())) {
                    host.aboutToChange();
                    if (doc.detach(selectedId) != null) {
                        select(null);
                        host.changed();
                    }
                }
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
