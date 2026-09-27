package org.nmox.studio.core.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keyboard's way in (3.4, question 3): the menu keys, Enter, the
 * pressable chip and the focus ring, each driven through the maps and
 * paint Swing itself uses.
 */
class KeyboardAccessTest {

    @Test
    @DisplayName("Shift+F10 and the context-menu key run the menu; Enter runs the double-click's gesture")
    void keysRunTheirGestures() {
        JList<String> list = new JList<>(new String[]{"a", "b"});
        AtomicInteger menus = new AtomicInteger();
        AtomicInteger enters = new AtomicInteger();
        KeyboardAccess.onMenuKey(list, menus::incrementAndGet);
        KeyboardAccess.onEnter(list, enters::incrementAndGet);

        assertThat(KeyboardAccess.perform(list, KeyboardAccess.SHIFT_F10)).isTrue();
        assertThat(KeyboardAccess.perform(list, KeyboardAccess.CONTEXT_MENU)).isTrue();
        assertThat(KeyboardAccess.perform(list, KeyboardAccess.ENTER)).isTrue();
        assertThat(menus).hasValue(2);
        assertThat(enters).hasValue(1);
    }

    @Test
    @DisplayName("a keyboard-opened menu appears under the selected item, the one it acts on")
    void anchorIsTheSelectedRow() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("one"));
        root.add(new DefaultMutableTreeNode("two"));
        JTree tree = new JTree(root);
        tree.setSize(200, 200);
        tree.setSelectionRow(2);
        java.awt.Rectangle row = tree.getRowBounds(2);
        Point p = KeyboardAccess.menuAnchor(tree);
        assertThat(p.y).isEqualTo(row.y + row.height);

        JButton plain = new JButton("x");
        plain.setSize(40, 20);
        assertThat(KeyboardAccess.menuAnchor(plain)).isEqualTo(new Point(0, 20));
    }

    @Test
    @DisplayName("componentMenuKeys binds the menu keys to the component's own popup")
    void componentMenuKeysBind() {
        JButton b = new JButton("x");
        b.setComponentPopupMenu(new JPopupMenu());
        KeyboardAccess.componentMenuKeys(b);
        // not showing headless, so nothing opens — but the key is bound
        assertThat(KeyboardAccess.perform(b, KeyboardAccess.SHIFT_F10)).isTrue();
    }

    @Test
    @DisplayName("a chip is a button to a screen reader, pressable by its action, Enter, Space and Shift+F10")
    void chipIsPressable() {
        AtomicInteger opened = new AtomicInteger();
        KeyboardAccess.Chip chip = new KeyboardAccess.Chip(opened::incrementAndGet);
        chip.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        assertThat(chip.isFocusable()).isTrue();
        assertThat(chip.isRequestFocusEnabled()).as("a mouse press keeps the editor focused").isFalse();
        assertThat(chip.getBorder()).isInstanceOf(KeyboardAccess.FocusRingBorder.class);

        AccessibleContext ac = chip.getAccessibleContext();
        assertThat(ac.getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(ac.getAccessibleAction().getAccessibleActionCount()).isEqualTo(1);
        // an assistive tool calls from its own thread; the press lands on the EDT
        assertThat(ac.getAccessibleAction().doAccessibleAction(0)).isTrue();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        KeyboardAccess.perform(chip, KeyboardAccess.ENTER);
        KeyboardAccess.perform(chip, KeyboardAccess.SPACE);
        KeyboardAccess.perform(chip, KeyboardAccess.SHIFT_F10);
        assertThat(opened).hasValue(4);
    }

    @Test
    @DisplayName("the focus ring paints while focused and never otherwise")
    void ringPaintsOnlyWithFocus() {
        Color ring = new Color(0xFF, 0x00, 0xFF);
        Object saved = UIManager.get("Component.focusColor");
        UIManager.put("Component.focusColor", ring);
        try {
            assertThat(ringPixels(link(false), ring)).as("unfocused").isZero();
            assertThat(ringPixels(link(true), ring)).as("focused").isPositive();
        } finally {
            UIManager.put("Component.focusColor", saved);
        }
    }

    private static JButton link(boolean focused) {
        JButton b = new JButton("Open Folder") {
            @Override
            public boolean hasFocus() {
                return focused;
            }
        };
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
        b.setFocusPainted(false);
        KeyboardAccess.focusRing(b);
        b.setSize(120, 24);
        return b;
    }

    private static int ringPixels(JButton b, Color ring) {
        BufferedImage img = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        b.getBorder().paintBorder(b, g, 0, 0, b.getWidth(), b.getHeight());
        g.dispose();
        int count = 0;
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                Color c = new Color(img.getRGB(x, y), true);
                if (c.getAlpha() > 100 && c.getRed() > 150 && c.getBlue() > 150 && c.getGreen() < 100) {
                    count++;
                }
            }
        }
        return count;
    }
}
