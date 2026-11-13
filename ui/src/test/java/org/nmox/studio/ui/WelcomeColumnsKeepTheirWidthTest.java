package org.nmox.studio.ui;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Welcome's four columns are each as wide as their own rows.
 *
 * <p>They were a {@code GridLayout(1, 4)}, which makes every column as wide
 * as the widest. Walked in German at the default window size (3.13.0): the
 * checklist's longest row set the width of all four, the row of columns
 * asked for more than the tab holds, and FIRST STEPS ran off the right edge
 * cut mid-word. English hid it, because its columns are much alike. The
 * population is read from the window, so a fifth column is in it.
 */
class WelcomeColumnsKeepTheirWidthTest {

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    /** The panel whose children are the launchpad's columns. */
    private static JPanel row(MainWindow window) {
        List<Component> all = new ArrayList<>();
        collect(window, all);
        for (Component c : all) {
            if (c instanceof JPanel p && p.getComponentCount() >= 4) {
                boolean columns = true;
                for (Component child : p.getComponents()) {
                    columns &= child instanceof JPanel col && col.getLayout() instanceof BoxLayout;
                }
                if (columns) {
                    return p;
                }
            }
        }
        throw new AssertionError("the Welcome has no row of columns");
    }

    @Test
    @DisplayName("a column is as wide as its own rows, not as wide as the widest column")
    void eachColumnKeepsItsOwnWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel row = row(new MainWindow());
            row.setSize(row.getPreferredSize());
            row.getLayout().layoutContainer(row);
            int sum = 0;
            int widest = 0;
            int narrowest = Integer.MAX_VALUE;
            for (Component col : row.getComponents()) {
                int pref = col.getPreferredSize().width;
                assertThat(col.getWidth()).as("a column's laid-out width is its own").isEqualTo(pref);
                assertThat(col.getY()).as("columns start at the top").isEqualTo(row.getComponent(0).getY());
                sum += pref;
                widest = Math.max(widest, pref);
                narrowest = Math.min(narrowest, pref);
            }
            int n = row.getComponentCount();
            assertThat(narrowest).as("the fixture: the columns are not all alike").isLessThan(widest);
            assertThat(row.getPreferredSize().width)
                    .as("the row asks for the columns' own widths and the gaps")
                    .isEqualTo(sum + n * 36)
                    .isLessThan(n * widest + (n - 1) * 36);
        });
    }

    @Test
    @DisplayName("in a right-to-left window the first column stands at the right")
    void theRowFollowsTheReader() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel row = row(new MainWindow());
            row.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            row.setSize(row.getPreferredSize());
            row.getLayout().layoutContainer(row);
            assertThat(row.getComponent(0).getX())
                    .as("START is the reader's first column")
                    .isGreaterThan(row.getComponent(row.getComponentCount() - 1).getX());
        });
    }
}
