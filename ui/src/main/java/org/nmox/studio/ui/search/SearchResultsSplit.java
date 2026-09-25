package org.nmox.studio.ui.search;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;

/**
 * Find in Projects' results open readable (after 3.2.0). The platform's
 * results panel splits a tree of matches from a preview and places the
 * divider at {@code max(saved, 250)} pixels — but its first layout happens
 * while the Search Results window has almost no width, Swing clamps the
 * divider to fit, and the panel's own listener saves the clamp
 * ({@code replace_results_divider=14}, read from a fresh userdir's
 * preferences). From then on every search opens with its matches squeezed
 * into a column about ten pixels wide beside the preview, so a search that
 * found what it looked for reads as one that found nothing — on every
 * later launch too, until the user finds the divider.
 *
 * <p>The platform's code cannot be changed from here, so the fix is at the
 * one moment it can be seen: the first time a search-results split is laid
 * out at a real width, a tree too narrow to read gets the platform's own
 * 250 pixels, and the platform's listener saves that instead. Once per
 * split; a user who drags it narrow afterwards is not overruled.
 *
 * <p>Only the Search Results window is watched (its review): a toolkit-wide
 * component listener would make every component in the IDE post an event
 * on every resize. The window's registry events say when it opens or comes
 * forward; its own containers say when a search adds a results tab.
 */
@OnShowing
public final class SearchResultsSplit implements Runnable {

    /** Narrower than this, the tree of matches cannot be read. */
    static final int MIN_READABLE = 120;
    /**
     * The platform's own floor for the divider, from its bytecode — and the
     * value the heal gives, never a fraction of the window: the platform
     * saves it as an absolute width, and 40% of a wide monitor would starve
     * the preview in a narrow window later.
     */
    static final int PLATFORM_DEFAULT = 250;
    /** A split this narrow has not had its real layout yet. */
    static final int REAL_WIDTH = 2 * PLATFORM_DEFAULT;

    private static final String CHECKED = "nmox.searchResultsSplitChecked";
    private static final String WATCHED = "nmox.searchResultsWatched";
    private static final String RESULT_VIEW = "org.netbeans.modules.search.ResultView";

    private static final ComponentAdapter ON_RESIZE = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            if (e.getComponent() instanceof JSplitPane split) {
                heal(split);
            }
        }
    };

    private static final ContainerAdapter ON_ADD = new ContainerAdapter() {
        @Override
        public void componentAdded(ContainerEvent e) {
            watch(e.getChild());
        }
    };

    @Override
    public void run() {
        PropertyChangeListener l = e -> {
            if ((TopComponent.Registry.PROP_TC_OPENED.equals(e.getPropertyName())
                    || TopComponent.Registry.PROP_ACTIVATED.equals(e.getPropertyName()))
                    && e.getNewValue() instanceof TopComponent tc
                    && RESULT_VIEW.equals(tc.getClass().getName())) {
                SwingUtilities.invokeLater(() -> watch(tc));
            }
        };
        TopComponent.getRegistry().addPropertyChangeListener(l);
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (RESULT_VIEW.equals(tc.getClass().getName())) {
                watch(tc);
            }
        }
    }

    /**
     * Watches a component of the Search Results window: a results split is
     * healed now and on its resizes; a container is followed for the
     * results tabs a later search adds. Each component once.
     */
    static void watch(Component c) {
        if (!(c instanceof JComponent jc) || jc.getClientProperty(WATCHED) != null) {
            return;
        }
        jc.putClientProperty(WATCHED, Boolean.TRUE);
        if (c instanceof JSplitPane split && inSearchResults(split)) {
            split.addComponentListener(ON_RESIZE);
            heal(split);
            return;   // the panes inside are the platform's tree and preview
        }
        Container container = (Container) c;
        container.addContainerListener(ON_ADD);
        for (Component child : container.getComponents()) {
            watch(child);
        }
    }

    /** Checks one split once it has a real width: gives a tree nobody could read its width. */
    static void heal(JSplitPane split) {
        if (split.getClientProperty(CHECKED) != null || split.getRightComponent() == null
                || split.getWidth() < REAL_WIDTH) {
            return;   // already checked; preview off (the tree has it all); not laid out yet
        }
        split.putClientProperty(CHECKED, Boolean.TRUE);
        boolean ltr = split.getComponentOrientation().isLeftToRight();
        int now = split.getDividerLocation();
        int wanted = divider(split.getWidth(), split.getDividerSize(), now, ltr);
        if (wanted != now) {
            split.setDividerLocation(wanted);
        }
    }

    /**
     * Where the divider belongs: where it is, unless the tree of matches is
     * too narrow to read. Left to right the tree is left of the divider; in
     * a mirrored (Hebrew, Arabic) window it is right of it, so the location
     * is the preview's width and the tree has what is left.
     */
    static int divider(int width, int dividerSize, int location, boolean leftToRight) {
        int tree = leftToRight ? location : width - location - dividerSize;
        if (tree >= MIN_READABLE) {
            return location;
        }
        return leftToRight ? PLATFORM_DEFAULT : width - dividerSize - PLATFORM_DEFAULT;
    }

    /** Whether the split belongs to the platform's search or replace results. */
    static boolean inSearchResults(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p.getClass().getName().startsWith("org.netbeans.modules.search.ui.Basic")) {
                return true;
            }
        }
        return false;
    }
}
