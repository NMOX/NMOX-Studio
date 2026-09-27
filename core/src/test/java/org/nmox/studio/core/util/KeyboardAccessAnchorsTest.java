package org.nmox.studio.core.util;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a menu opened from the keyboard appears, and the bindings behind
 * the keys (3.4, question 3): under the selected row of a tree, list or
 * table — the item the menu acts on — else under the component's corner.
 */
class KeyboardAccessAnchorsTest {

    @Test
    @DisplayName("a tree, list or table anchors the keyboard menu under its selected row")
    void anchorsFollowTheSelection() {
        JTree tree = new JTree();
        tree.setSize(200, 400);
        tree.setSelectionRow(1);
        Point t = KeyboardAccess.menuAnchor(tree);
        java.awt.Rectangle row = tree.getRowBounds(1);
        assertThat(t.y).isEqualTo(row.y + row.height);

        JList<String> list = new JList<>(new String[]{"a", "b", "c"});
        list.setSize(100, 100);
        list.setSelectedIndex(2);
        java.awt.Rectangle cell = list.getCellBounds(2, 2);
        assertThat(KeyboardAccess.menuAnchor(list).y).isEqualTo(cell.y + cell.height);

        JTable table = new JTable(3, 2);
        table.setSize(200, 100);
        table.setRowSelectionInterval(1, 1);
        java.awt.Rectangle r = table.getCellRect(1, 0, true);
        assertThat(KeyboardAccess.menuAnchor(table).y).isEqualTo(r.y + r.height);

        JPanel plain = new JPanel();
        plain.setSize(50, 30);
        assertThat(KeyboardAccess.menuAnchor(plain)).isEqualTo(new Point(0, 30));
        JList<String> none = new JList<>(new String[]{"a"});
        none.setSize(50, 30);
        assertThat(KeyboardAccess.menuAnchor(none)).as("nothing selected: the corner").isEqualTo(new Point(0, 30));
    }

    @Test
    @DisplayName("the menu keys and Enter are bound, and a disabled or absent binding does nothing")
    void bindings() {
        JButton b = new JButton("x");
        AtomicInteger shown = new AtomicInteger();
        KeyboardAccess.onMenuKey(b, shown::incrementAndGet);
        assertThat(KeyboardAccess.perform(b, KeyboardAccess.SHIFT_F10)).isTrue();
        assertThat(KeyboardAccess.perform(b, KeyboardAccess.CONTEXT_MENU)).isTrue();
        assertThat(shown).hasValue(2);
        assertThat(KeyboardAccess.perform(new JPanel(), KeyboardAccess.SPACE)).as("nothing bound").isFalse();

        JList<String> list = new JList<>(new String[]{"a"});
        list.setComponentPopupMenu(new JPopupMenu());
        KeyboardAccess.componentMenuKeys(list);
        assertThat(KeyboardAccess.perform(list, KeyboardAccess.SHIFT_F10))
                .as("bound; not showing, so no menu is shown headless").isTrue();

        AtomicInteger entered = new AtomicInteger();
        KeyboardAccess.onEnter(list, entered::incrementAndGet);
        list.getActionMap().get(KeyboardAccess.ENTER_ACTION).setEnabled(false);
        assertThat(KeyboardAccess.perform(list, KeyboardAccess.ENTER)).as("a disabled action is not run").isFalse();
        assertThat(entered).hasValue(0);
    }

    @Test
    @DisplayName("a focus ring is installed once, keeps the border it wraps, and has a colour")
    void focusRing() {
        JButton b = new JButton("x");
        b.setBorder(new EmptyBorder(2, 3, 4, 5));
        KeyboardAccess.focusRing(b);
        KeyboardAccess.focusRing(b);
        assertThat(b.getBorder()).isInstanceOf(KeyboardAccess.FocusRingBorder.class);
        assertThat(b.getFocusListeners().length).as("one listener, however often installed")
                .isEqualTo(new JButton().getFocusListeners().length + 1);
        assertThat(KeyboardAccess.focusColor()).isNotNull();
        assertThat(KeyboardAccess.menuShortcutMask()).isNotZero();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        b.setSize(40, 20);
        b.getBorder().paintBorder(b, img.getGraphics(), 0, 0, 40, 20);
        assertThat(b.getBorder().getBorderInsets(b)).isNotNull();
    }
}
