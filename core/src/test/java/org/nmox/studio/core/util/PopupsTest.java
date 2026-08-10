package org.nmox.studio.core.util;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clicked-item-wins law (v1.270.0 arc review): a context menu's
 * verbs read the SELECTION, but {@code setComponentPopupMenu} never
 * moves the selection to the clicked item — so a right-click on row 3
 * with row 1 selected made Delete/Forget/Run Script act on row 1.
 * {@link Popups} closes the gap by selecting the item under the popup
 * trigger; these tests drive the real listener with synthesized
 * trigger events on real (headless) Swing components.
 */
class PopupsTest {

    /** Fires the installed listener with a popup-trigger press at (x, y). */
    private static void trigger(java.awt.Component c, int x, int y) {
        MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, true);
        for (MouseListener l : c.getMouseListeners()) {
            l.mousePressed(e);
        }
    }

    /** A plain (non-trigger) press at (x, y). */
    private static void plainPress(java.awt.Component c, int x, int y) {
        MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false);
        for (MouseListener l : c.getMouseListeners()) {
            l.mousePressed(e);
        }
    }

    private static JTable table() {
        JTable t = new JTable(new String[][]{{"a"}, {"b"}, {"c"}},
                new String[]{"col"});
        t.setSize(200, 200);
        Popups.selectOnTrigger(t);
        return t;
    }

    @Test
    @DisplayName("a popup trigger on a table row selects that row")
    void tableTriggerSelectsClickedRow() {
        JTable t = table();
        t.setRowSelectionInterval(0, 0); // the stale selection the bug acted on
        Rectangle r = t.getCellRect(2, 0, true);
        trigger(t, r.x + 2, r.y + 2);
        assertThat(t.getSelectedRow())
                .as("the CLICKED row wins over the stale selection")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a popup trigger below the last table row clears the selection")
    void tableTriggerOnEmptySpaceClears() {
        JTable t = table();
        t.setRowSelectionInterval(0, 0);
        Rectangle last = t.getCellRect(2, 0, true);
        trigger(t, 5, last.y + last.height + 20);
        assertThat(t.getSelectedRow())
                .as("empty space clears — verbs must no-op, not act on a "
                        + "row the user isn't looking at")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("an ordinary left press never moves the selection")
    void tablePlainPressLeavesSelectionAlone() {
        JTable t = table();
        t.setRowSelectionInterval(0, 0);
        Rectangle r = t.getCellRect(2, 0, true);
        plainPress(t, r.x + 2, r.y + 2);
        assertThat(t.getSelectedRow())
                .as("only the popup trigger re-targets; normal clicks keep "
                        + "the component's own selection behaviour")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("a popup trigger on a tree node selects that node")
    void treeTriggerSelectsClickedNode() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
        DefaultMutableTreeNode b = new DefaultMutableTreeNode("b");
        root.add(a);
        root.add(b);
        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(true);
        tree.expandPath(new TreePath(root));
        tree.setSize(200, 200);
        Popups.selectOnTrigger(tree);

        tree.setSelectionRow(1); // stale: "a"
        Rectangle rb = tree.getRowBounds(2); // "b"
        assertThat(rb).as("headless tree lays out rows").isNotNull();
        trigger(tree, rb.x + 2, rb.y + 2);
        assertThat(tree.getSelectionPath().getLastPathComponent())
                .isEqualTo(b);

        trigger(tree, 5, rb.y + rb.height + 50); // below every row
        assertThat(tree.getSelectionPath())
                .as("empty tree space clears the selection")
                .isNull();
    }

    @Test
    @DisplayName("a popup trigger on a list entry selects it; past the end clears")
    void listTriggerSelectsClickedEntry() {
        JList<String> list = new JList<>(new String[]{"a", "b", "c"});
        list.setFixedCellHeight(20);
        list.setFixedCellWidth(100);
        list.setSize(100, 200);
        Popups.selectOnTrigger(list);

        list.setSelectedIndex(0);
        trigger(list, 5, 45); // inside entry 2's cell
        assertThat(list.getSelectedIndex()).isEqualTo(2);

        // locationToIndex maps a point past the last cell to the CLOSEST
        // index — the helper must check real cell bounds and clear instead
        trigger(list, 5, 150);
        assertThat(list.getSelectedIndex())
                .as("past the last entry is empty space, not entry 2")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("a drag-enabled list claims the clicked card through the popup hook")
    void popupTargetListSelectsUnderPointer() {
        javax.swing.DefaultListModel<String> model =
                new javax.swing.DefaultListModel<>();
        model.addElement("first");
        model.addElement("second");
        javax.swing.JList<String> list = Popups.popupTargetList(model);
        // NOTE: setDragEnabled(true) — the production condition that
        // makes the listener form inert — throws HeadlessException in a
        // test JVM, so it is left off here. The hook under test does not
        // depend on it: getPopupLocation is called by Swing on the popup
        // path either way, which is exactly why it is the robust form.
        list.setFixedCellHeight(20);
        list.setFixedCellWidth(200);
        list.setSize(200, 40);

        // Swing calls getPopupLocation on its way to showing the menu,
        // handing over the triggering event — nothing the drag machinery
        // does can bypass it (v1.326.0, measured in the shipped app).
        list.getPopupLocation(popupAt(list, 5, 30));
        assertThat(list.getSelectedValue())
                .as("the SECOND row was under the pointer")
                .isEqualTo("second");

        list.getPopupLocation(popupAt(list, 5, 10));
        assertThat(list.getSelectedValue()).isEqualTo("first");

        // and empty space below the last row clears, so a menu that
        // reads the selection honestly no-ops instead of acting on a
        // row the user is not pointing at
        list.getPopupLocation(popupAt(list, 5, 300));
        assertThat(list.getSelectedValue())
                .as("empty space clears rather than snapping to the last row")
                .isNull();
    }

    private static java.awt.event.MouseEvent popupAt(
            javax.swing.JComponent c, int x, int y) {
        return new java.awt.event.MouseEvent(c,
                java.awt.event.MouseEvent.MOUSE_PRESSED, 0,
                java.awt.event.InputEvent.BUTTON3_DOWN_MASK,
                x, y, 1, true);
    }
}
