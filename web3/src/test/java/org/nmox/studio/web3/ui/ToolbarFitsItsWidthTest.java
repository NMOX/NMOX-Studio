package org.nmox.studio.web3.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract Studio's toolbar keeps every control inside the window, at any
 * width.
 *
 * <p>It was one row: a network combo, four buttons, a sentence saying whether
 * a chain is connected, Compile, Rescan and the artifact count. On a Mac at
 * the default window size that row fits with a few pixels to spare. The first
 * Linux walk (3.5) photographed it with the system's wider font: Rescan cut
 * in half at the window's edge and the artifact count gone, with nothing to
 * say they were there. A one-row toolbar reports a preferred width and is
 * given less; what does not fit is simply not painted.
 *
 * <p>This asks the real toolbar, built by the real window, to lay itself out
 * at three widths and checks where every control landed.
 */
class ToolbarFitsItsWidthTest {

    @Test
    @DisplayName("at any width every control of the toolbar lies inside it")
    void everyControlIsInside() throws Exception {
        List<String> outside = new ArrayList<>();
        int[] rowsAtNarrow = {0};
        SwingUtilities.invokeAndWait(() -> {
            Web3StudioTopComponent window = new Web3StudioTopComponent();
            Component north = ((BorderLayout) window.getLayout()).getLayoutComponent(BorderLayout.NORTH);
            assertThat(north).as("the window's toolbar").isInstanceOf(JToolBar.class);
            JToolBar bar = (JToolBar) north;
            for (int width : new int[] {1400, 900, 480}) {
                bar.setSize(width, 10);
                Dimension pref = bar.getPreferredSize();
                bar.setSize(width, pref.height);
                bar.doLayout();
                java.util.Set<Integer> rows = new java.util.TreeSet<>();
                for (Component c : bar.getComponents()) {
                    if (!c.isVisible() || c.getWidth() == 0) {
                        continue;
                    }
                    rows.add(c.getY() + c.getHeight() / 2);
                    if (c.getX() < 0 || c.getX() + c.getWidth() > width
                            || c.getY() < 0 || c.getY() + c.getHeight() > pref.height) {
                        outside.add("at " + width + "px: " + c.getClass().getSimpleName() + " " + c.getBounds()
                                + " in a bar " + width + "x" + pref.height);
                    }
                }
                if (width == 480) {
                    rowsAtNarrow[0] = rows.size();
                }
            }
        });
        assertThat(outside).as("a control laid out past the toolbar's edge is not painted").isEmpty();
        assertThat(rowsAtNarrow[0]).as("at 480px the toolbar's controls take more than one row").isGreaterThan(1);
    }
}
