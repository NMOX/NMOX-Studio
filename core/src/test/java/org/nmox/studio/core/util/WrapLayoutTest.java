package org.nmox.studio.core.util;

import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ledger-75 fix: {@link WrapLayout} reports the WRAPPED preferred
 * height for the container's current width, so a toolbar in a NORTH
 * slot grows a second row at narrow widths instead of clipping its
 * rightmost controls. Plain FlowLayout wraps at layout time but LIES
 * in preferredLayoutSize (always one row) — these tests pin the
 * difference that makes the clip class structural.
 */
class WrapLayoutTest {

    private static JPanel panel(int width, int buttons) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        for (int i = 0; i < buttons; i++) {
            JButton b = new JButton("B" + i);
            b.setPreferredSize(new Dimension(100, 24));
            p.add(b);
        }
        p.setSize(width, 30);
        return p;
    }

    @Test
    @DisplayName("at a narrow width the preferred height grows to fit the wrap")
    void narrowWidthWrapsToMoreRows() {
        JPanel wide = panel(1000, 6);   // 6×100px fits one row
        JPanel narrow = panel(320, 6);  // ~3 per row → 2 rows
        int oneRow = wide.getPreferredSize().height;
        int wrapped = narrow.getPreferredSize().height;
        assertThat(wrapped)
                .as("the wrapped height must be taller than one row — this is"
                        + " the exact lie plain FlowLayout tells that clips"
                        + " the second row invisible")
                .isGreaterThan(oneRow);
    }

    @Test
    @DisplayName("before the first layout (width 0) it answers as one row")
    void zeroWidthFallsBackToOneRow() {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        JButton b = new JButton("B");
        b.setPreferredSize(new Dimension(100, 24));
        p.add(b);
        p.add(b);
        // no setSize: width is 0, the pre-layout state
        assertThat(p.getPreferredSize().height)
                .as("width 0 must not divide into infinite rows")
                .isLessThan(60);
    }

    @Test
    @DisplayName("every child stays inside the container after a real layout")
    void layoutKeepsChildrenInsideTheWidth() {
        JPanel p = panel(320, 6);
        p.setSize(320, p.getPreferredSize().height);
        p.doLayout();
        for (java.awt.Component c : p.getComponents()) {
            assertThat(c.getX() + c.getWidth())
                    .as("no child may extend past the right edge — extending"
                            + " past it IS the ledger-75 clip")
                    .isLessThanOrEqualTo(320);
        }
    }
}
