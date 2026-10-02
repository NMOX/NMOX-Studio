package org.nmox.studio.ui.a11y;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeCellRenderer;

import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import org.nmox.studio.core.util.PlainText;

/**
 * Every explorer tree in the product is heard as the words it paints.
 *
 * <p>The platform's node renderer paints a row's display name as markup: a
 * file's git state in colour, a database connection in bold. Swing takes a
 * label's accessible name from its text, so a screen reader was handed the
 * markup: {@code <b>jdbc:postgresql://127.0.0.1:55001/postgres</b>} for a
 * connection in the Services window (ledger 126, read from the accessibility
 * tree in the 3.4.1 walk). 3.4.0 repaired exactly this in Project Studio's
 * file tree, where the tree is ours to build. The Services, Projects, Files
 * and Favorites windows are the platform's.
 *
 * <p>So this reaches them the way {@link WindowTabsAccessibility} reaches the
 * platform's tab containers: once the main window shows, and again whenever a
 * window opens or comes forward, every tree still using the platform's own
 * renderer has it wrapped. The wrapper paints nothing differently; it names
 * the row with {@link PlainText#words}. A tree whose renderer is anything
 * else is left alone: its author chose what it says.
 */
@OnShowing
public final class SpokenTreeRows implements Runnable {

    /** The explorer's renderer: every tree view the platform builds starts with it. */
    static final String PLATFORM_RENDERER = "org.openide.explorer.view.NodeRenderer";

    @Override
    public void run() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof Window w) {
                speakUnder(w);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
        // a window opened inside an open window fires no WINDOW_OPENED; the
        // registry announces it, and an explorer view built lazily inside a
        // window already open is met when that window comes forward
        WindowManager.getDefault().getRegistry().addPropertyChangeListener(ev -> {
            String p = ev.getPropertyName();
            if (TopComponent.Registry.PROP_OPENED.equals(p)
                    || TopComponent.Registry.PROP_TC_OPENED.equals(p)
                    || TopComponent.Registry.PROP_ACTIVATED.equals(p)) {
                SwingUtilities.invokeLater(SpokenTreeRows::speakEverywhere);
            }
        });
        SwingUtilities.invokeLater(SpokenTreeRows::speakEverywhere);
    }

    static void speakEverywhere() {
        for (Window w : Window.getWindows()) {
            speakUnder(w);
        }
    }

    /** Wraps the renderer of every platform-rendered tree under {@code root}; returns how many. */
    static int speakUnder(Component root) {
        int wrapped = 0;
        if (root instanceof JTree tree && speak(tree)) {
            wrapped++;
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                wrapped += speakUnder(child);
            }
        }
        return wrapped;
    }

    /** True when this call wrapped the tree's renderer; false when there was nothing to do. */
    static boolean speak(JTree tree) {
        TreeCellRenderer renderer = tree.getCellRenderer();
        if (renderer == null || !isThePlatforms(renderer)) {
            return false;
        }
        tree.setCellRenderer(new Spoken(renderer));
        return true;
    }

    /** The platform's renderer or a subclass of it, by name: the class is not ours to import everywhere. */
    static boolean isThePlatforms(TreeCellRenderer renderer) {
        for (Class<?> c = renderer.getClass(); c != null; c = c.getSuperclass()) {
            if (PLATFORM_RENDERER.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Paints exactly what the platform's renderer paints and names the row with its words. */
    static final class Spoken implements TreeCellRenderer {
        private final TreeCellRenderer platform;

        Spoken(TreeCellRenderer platform) {
            this.platform = platform;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = platform.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row,
                    hasFocus);
            if (c instanceof JLabel label) {
                label.getAccessibleContext().setAccessibleName(PlainText.words(label.getText()));
            }
            return c;
        }
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public SpokenTreeRows() {
    }
}
