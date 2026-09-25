package org.nmox.studio.ui.search;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import javax.swing.JSplitPane;
import org.openide.windows.OnShowing;

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
 * out at a real width, a divider too narrow to read is moved to the
 * platform's own 250 pixels (or 40% of a wide window), and the platform's
 * listener saves that instead. Once per split; a user who drags it narrow
 * afterwards is not overruled.
 */
@OnShowing
public final class SearchResultsSplit implements Runnable {

    /** Narrower than this, the tree of matches cannot be read. */
    static final int MIN_READABLE = 120;
    /** The platform's own floor for the divider, from its bytecode. */
    static final int PLATFORM_DEFAULT = 250;
    /** A split this narrow has not had its real layout yet. */
    static final int REAL_WIDTH = 2 * PLATFORM_DEFAULT;

    private static final String CHECKED = "nmox.searchResultsSplitChecked";

    private static final AWTEventListener HEAL = event -> {
        if (event.getID() == ComponentEvent.COMPONENT_RESIZED
                && event.getSource() instanceof JSplitPane split
                && split.getClientProperty(CHECKED) == null
                && inSearchResults(split)) {
            heal(split);
        }
    };

    @Override
    public void run() {
        Toolkit.getDefaultToolkit().addAWTEventListener(HEAL, AWTEvent.COMPONENT_EVENT_MASK);
    }

    /** Checks one split once it has a real width: moves a divider nobody could read. */
    static void heal(JSplitPane split) {
        if (split.getClientProperty(CHECKED) != null || split.getRightComponent() == null
                || split.getWidth() < REAL_WIDTH) {
            return;   // already checked; preview off (the tree has it all); not laid out yet
        }
        split.putClientProperty(CHECKED, Boolean.TRUE);
        int now = split.getDividerLocation();
        int wanted = divider(split.getWidth(), now);
        if (wanted != now) {
            split.setDividerLocation(wanted);
        }
    }

    /** Where the divider belongs: where it is, unless that is too narrow to read. */
    static int divider(int width, int location) {
        return location >= MIN_READABLE ? location : Math.max(PLATFORM_DEFAULT, (int) (width * 0.4));
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
