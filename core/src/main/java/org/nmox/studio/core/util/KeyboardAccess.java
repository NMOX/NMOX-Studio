package org.nmox.studio.core.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * The keyboard's way in to gestures the product first wrote for a mouse
 * (3.4, question 3: can someone use it without a mouse, or without seeing
 * it?).
 *
 * <p>A census of the assembled app found three shapes a keyboard could not
 * reach, and each has one answer here, so a window states the gesture once
 * and the key rides along:
 *
 * <ul>
 *   <li><b>A menu opened from a mouse listener.</b> Shift+F10 and the
 *       context-menu key are the platform's "open this thing's menu"
 *       ({@code postPopup} in every Swing look and feel), but they only
 *       find a menu installed with {@code setComponentPopupMenu}. A menu
 *       built in {@code mousePressed} gets {@link #onMenuKey}; one
 *       installed on a component that could not take focus gets
 *       {@link #componentMenuKeys} once the component can.</li>
 *   <li><b>A double-click that does something.</b> Enter is the same
 *       gesture for a keyboard ({@link #onEnter}).</li>
 *   <li><b>A painted control a screen reader cannot press.</b> A status
 *       chip is a label with a mouse listener; {@link Chip} is the same
 *       label announced as a button, with an accessible action, Enter,
 *       Space and Shift+F10.</li>
 * </ul>
 *
 * <p>And one shape a keyboard could reach but nobody could see: a link
 * button with its focus painting off and an empty border shows nothing
 * when Tab lands on it. {@link #focusRing} paints a ring while, and only
 * while, the component holds focus.
 *
 * <p>Every binding is {@code WHEN_FOCUSED}, so it answers only in the
 * component the user is on, and it wins over the component's own binding
 * for the same key (a table's Enter moves to the next row; here it opens
 * what a double-click opens).
 */
public final class KeyboardAccess {

    /** The action-map key the menu keys run. */
    public static final String MENU_ACTION = "nmox-keyboard-menu";
    /** The action-map key Enter runs. */
    public static final String ENTER_ACTION = "nmox-keyboard-enter";
    /** The action-map key Space runs on a {@link Chip}. */
    public static final String SPACE_ACTION = "nmox-keyboard-space";

    /** Shift+F10, the keyboard's context-menu chord on every desktop. */
    public static final KeyStroke SHIFT_F10 = KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK);
    /** The dedicated context-menu key (the "menu" key on a PC keyboard). */
    public static final KeyStroke CONTEXT_MENU = KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0);
    public static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    public static final KeyStroke SPACE = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);

    private static final String RING_LISTENER = "nmox.keyboardAccess.ringListener";

    private KeyboardAccess() {
    }

    /** Shift+F10 and the context-menu key run {@code show} while {@code c} has focus. */
    public static void onMenuKey(JComponent c, Runnable show) {
        bind(c, SHIFT_F10, MENU_ACTION, show);
        bind(c, CONTEXT_MENU, MENU_ACTION, show);
    }

    /**
     * The menu keys open {@code c}'s own {@code getComponentPopupMenu()}
     * under the selected item ({@link #menuAnchor}). Swing's root pane does
     * this too, but at the component's centre and only when no ancestor
     * binds the keys; binding here states it where the menu lives.
     */
    public static void componentMenuKeys(JComponent c) {
        onMenuKey(c, () -> {
            JPopupMenu menu = c.getComponentPopupMenu();
            if (menu != null && c.isShowing()) {
                Point p = menuAnchor(c);
                menu.show(c, p.x, p.y);
            }
        });
    }

    /** Enter runs {@code action} while {@code c} has focus. */
    public static void onEnter(JComponent c, Runnable action) {
        bind(c, ENTER, ENTER_ACTION, action);
    }

    /**
     * Where a menu opened from the keyboard appears: just below the
     * selected row of a tree, list or table (the item the menu acts on, as
     * a right-click's menu appears at the item it was opened on), else
     * below the component's top-left corner.
     */
    public static Point menuAnchor(JComponent c) {
        Rectangle r = null;
        if (c instanceof JTree tree) {
            int row = tree.getLeadSelectionRow();
            r = row >= 0 ? tree.getRowBounds(row) : null;
        } else if (c instanceof JList<?> list) {
            int i = list.getLeadSelectionIndex();
            r = i >= 0 ? list.getCellBounds(i, i) : null;
        } else if (c instanceof JTable table) {
            int row = table.getSelectedRow();
            r = row >= 0 ? table.getCellRect(row, Math.max(0, table.getSelectedColumn()), true) : null;
        }
        if (r != null) {
            return new Point(r.x + Math.min(12, r.width / 2), r.y + r.height);
        }
        return new Point(0, Math.max(0, c.getHeight()));
    }

    /**
     * Runs what pressing {@code key} does in {@code c} while it has focus:
     * its own {@code WHEN_FOCUSED} binding, else the ancestor binding Swing
     * would try next. Returns false when nothing is bound. A headless test
     * has no focus to type into; this reads the same maps Swing reads.
     */
    public static boolean perform(JComponent c, KeyStroke key) {
        for (int condition : new int[]{JComponent.WHEN_FOCUSED,
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}) {
            Object name = c.getInputMap(condition).get(key);
            javax.swing.Action a = name == null ? null : c.getActionMap().get(name);
            if (a != null && a.isEnabled()) {
                a.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED,
                        String.valueOf(name), key.getModifiers()));
                return true;
            }
        }
        return false;
    }

    private static void bind(JComponent c, KeyStroke key, String name, Runnable run) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        c.getActionMap().put(name, new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
    }

    /**
     * Paints a ring around {@code c} while it holds focus, over whatever
     * border it has now (a link's empty border keeps its spacing). Call
     * after the component's own {@code setBorder}.
     */
    public static <T extends JComponent> T focusRing(T c) {
        Border b = c.getBorder();
        if (!(b instanceof FocusRingBorder)) {
            c.setBorder(new FocusRingBorder(b));
        }
        if (c.getClientProperty(RING_LISTENER) == null) {
            c.putClientProperty(RING_LISTENER, Boolean.TRUE);
            c.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    c.repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    c.repaint();
                }
            });
        }
        return c;
    }

    /** The colour a focus indicator is drawn in: the look and feel's own, else a clear blue. */
    public static Color focusColor() {
        Color c = UIManager.getColor("Component.focusColor");
        if (c == null) {
            c = UIManager.getColor("Focus.color");
        }
        return c != null ? c : new Color(0x3D, 0x8E, 0xE8);
    }

    /**
     * A border that draws a focus ring outside an inner border's content
     * while the component holds focus, and nothing otherwise. It keeps the
     * inner border's insets, widened to one pixel where they were thinner,
     * so the ring never paints over the text.
     */
    public static final class FocusRingBorder implements Border {

        private final Border inner;

        public FocusRingBorder(Border inner) {
            this.inner = inner;
        }

        public Border inner() {
            return inner;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if (inner != null) {
                inner.paintBorder(c, g, x, y, width, height);
            }
            if (!c.hasFocus()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(focusColor());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x, y, width - 1, height - 1, 6, 6);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            Insets i = inner == null ? new Insets(0, 0, 0, 0) : inner.getBorderInsets(c);
            return new Insets(Math.max(1, i.top), Math.max(1, i.left),
                    Math.max(1, i.bottom), Math.max(1, i.right));
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    /**
     * A status-line chip a keyboard and a screen reader can operate: a
     * label announced as a button, whose accessible action, Enter, Space
     * and the menu keys all run the same {@code open} its mouse press runs.
     * It takes focus from the keyboard or an assistive tool and never from
     * a mouse click, so pressing it with the mouse leaves the editor
     * focused as before.
     */
    public static class Chip extends JLabel {

        private final transient Runnable open;

        public Chip(Runnable open) {
            this.open = open;
            setFocusable(true);
            setRequestFocusEnabled(false);
            onEnter(this, open);
            bind(this, SPACE, SPACE_ACTION, open);
            onMenuKey(this, open);
            focusRing(this);
        }

        @Override
        public void setBorder(Border border) {
            super.setBorder(border == null || border instanceof FocusRingBorder
                    ? border : new FocusRingBorder(border));
        }

        /** Runs what a press runs (the accessible action's body). */
        public void press() {
            if (open != null) {
                open.run();
            }
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleChip();
            }
            return accessibleContext;
        }

        /** A label read as a button, pressable by an assistive tool. */
        protected class AccessibleChip extends AccessibleJLabel implements AccessibleAction {

            @Override
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.PUSH_BUTTON;
            }

            @Override
            public AccessibleAction getAccessibleAction() {
                return this;
            }

            @Override
            public int getAccessibleActionCount() {
                return 1;
            }

            @Override
            public String getAccessibleActionDescription(int i) {
                return i == 0 ? AccessibleAction.CLICK : null;
            }

            @Override
            public boolean doAccessibleAction(int i) {
                if (i != 0) {
                    return false;
                }
                if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                    press();
                } else {
                    javax.swing.SwingUtilities.invokeLater(Chip.this::press);
                }
                return true;
            }
        }
    }
}
