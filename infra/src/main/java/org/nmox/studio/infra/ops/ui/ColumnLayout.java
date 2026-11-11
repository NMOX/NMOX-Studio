package org.nmox.studio.infra.ops.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A column whose every row is exactly as wide as the column and as tall
 * as it needs to be AT THAT WIDTH.
 *
 * <p>The second half is the whole reason this is not a BoxLayout. A row
 * of buttons that wraps and a sentence that wraps are both taller when
 * the column is narrower, and a layout that asks a row for its height
 * before telling it its width gets the one-line answer: the inspector's
 * action row ran past the pane's right edge (walked in 3.11.0 and again
 * in the build after it: Probe and Destroy… on a host, and Roll Back… on
 * a service, were behind a horizontal scrollbar), and the evidence under
 * Relationships was cut mid-sentence. Here each row is given its width
 * first and asked second.
 *
 * <p>Used inside a scroll pane by a panel that tracks the viewport's
 * width, so the column is the pane's width and only ever scrolls down.
 */
final class ColumnLayout implements LayoutManager {

    private final int gap;

    ColumnLayout(int gap) {
        this.gap = gap;
    }

    /** The column's size when it is {@code width} wide; a width of zero or less asks for the natural one. */
    Dimension sizeFor(Container parent, int width) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            int natural = 0;
            for (Component c : parent.getComponents()) {
                if (c.isVisible()) {
                    natural = Math.max(natural, c.getMinimumSize().width);
                }
            }
            int room = width > 0 ? Math.max(1, width - in.left - in.right) : Math.max(1, natural);
            int height = in.top + in.bottom;
            boolean any = false;
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                height += (any ? gap : 0) + heightAt(c, room);
                any = true;
            }
            return new Dimension(room + in.left + in.right, height);
        }
    }

    /** A row's height once it is {@code width} wide: the width goes in first, the question after. */
    private static int heightAt(Component c, int width) {
        if (c.getWidth() != width) {
            c.setSize(width, Math.max(1, c.getHeight()));
        }
        return c.getPreferredSize().height;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return sizeFor(parent, parent.getWidth());
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Dimension d = sizeFor(parent, parent.getWidth());
        d.width = 1;
        return d;
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets in = parent.getInsets();
            int room = Math.max(1, parent.getWidth() - in.left - in.right);
            int y = in.top;
            boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
            for (Component c : parent.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                int h = heightAt(c, room);
                // every row spans the column, so there is one x for both reading directions
                c.setBounds(leftToRight ? in.left : in.right, y, room, h);
                y += h + gap;
            }
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // rows carry no constraints
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // rows carry no constraints
    }
}
