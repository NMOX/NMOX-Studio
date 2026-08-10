package org.nmox.studio.core.util;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * Makes a context menu act on the item under the CURSOR, not whatever
 * happened to be selected earlier.
 *
 * <p>{@code setComponentPopupMenu} shows the menu on the platform's
 * popup trigger but never moves the selection, while the menu's verbs
 * almost always read {@code getSelectedRow()}/{@code
 * getLastSelectedPathComponent()}/{@code getSelectedValue()}. The gap
 * is invisible in casual use (people usually left-click first) and
 * wrong exactly when it matters: right-click row 3 while row 1 is
 * selected and "Delete"/"Forget"/"Run Script" hits row 1 — the
 * v1.270.0 arc review found the mismatch at every popup site in the
 * product, sharpest in API Studio where a request delete has no
 * confirm and wipes the request's keychain token with it.
 *
 * <p>Each install method adds a mouse listener that, on the popup
 * trigger (checked on BOTH press and release — macOS/Linux fire on
 * press, Windows on release), selects the item under the pointer
 * before the menu opens. A trigger over empty space CLEARS the
 * selection, so selection-reading verbs honestly no-op instead of
 * acting on a stale item the user isn't even looking at.
 */
public final class Popups {

    private Popups() {
    }

    /** Selects the row under a popup trigger; empty space clears. */
    public static void selectOnTrigger(JTable table) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeSelect(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeSelect(e);
            }

            private void maybeSelect(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                } else {
                    table.clearSelection();
                }
            }
        });
    }

    /** Selects the node under a popup trigger; empty space clears. */
    public static void selectOnTrigger(JTree tree) {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeSelect(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeSelect(e);
            }

            private void maybeSelect(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                // exact hit only — getClosestPathForLocation would map a
                // click in the blank area below the tree onto the last
                // node, re-creating the acts-on-the-wrong-item bug
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    tree.setSelectionPath(path);
                } else {
                    tree.clearSelection();
                }
            }
        });
    }

    /** Selects the entry under a popup trigger; empty space clears. */
    public static void selectOnTrigger(JList<?> list) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeSelect(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeSelect(e);
            }

            private void maybeSelect(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                selectAt(list, e.getPoint());
            }
        });
    }

    /**
     * Selects whatever entry covers {@code p}; a point in empty space
     * clears the selection. Shared by both install forms so the
     * hit-testing rule lives once.
     *
     * <p>{@code locationToIndex} returns the CLOSEST index even for a
     * point past the last entry, so only a point inside the cell's real
     * bounds counts as a hit — otherwise a right-click in the blank
     * area below the list would act on the last row.
     */
    public static void selectAt(JList<?> list, Point p) {
        int index = list.locationToIndex(p);
        Rectangle cell = index >= 0 ? list.getCellBounds(index, index) : null;
        if (cell != null && cell.contains(p)) {
            list.setSelectedIndex(index);
        } else {
            list.clearSelection();
        }
    }

    /**
     * A list whose context menu acts on the item under the pointer even
     * when the list has drag enabled.
     *
     * <p>Use this instead of {@link #selectOnTrigger(JList)} whenever
     * the list calls {@code setDragEnabled(true)}. Measured in the
     * shipped 1.325.0 Task Board (v1.326.0): on a drag-enabled list the
     * trigger mouse listener never runs — proven not by reading Swing
     * but by behaviour, because a right-click in empty space failed to
     * CLEAR the selection, which that listener is the only thing that
     * does. The consequence was a dead menu: right-clicking a card the
     * user had not already left-clicked left the selection empty, so
     * Edit… and Delete… silently did nothing.
     *
     * <p>{@code getPopupLocation} is the hook Swing itself calls on the
     * way to showing a component popup, handing over the very event
     * that triggered it — so it cannot be bypassed by listener ordering
     * or event consumption, whatever the drag machinery does first.
     */
    public static <T> JList<T> popupTargetList(javax.swing.ListModel<T> model) {
        return new JList<T>(model) {
            @Override
            public Point getPopupLocation(MouseEvent event) {
                if (event != null) {
                    selectAt(this, event.getPoint());
                }
                return super.getPopupLocation(event);
            }
        };
    }
}
