package org.nmox.studio.core.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} whose preferred size reports the WRAPPED height
 * for the container's current width, so a toolbar in a
 * {@code BorderLayout.NORTH} slot flows onto a second row when the
 * window narrows instead of clipping its rightmost controls off the
 * edge (ledger 75 — the class bit DB Studio's console toolbar and the
 * Infra Designer's property panel before this existed).
 *
 * <p>Plain {@code FlowLayout} already wraps its children when it lays
 * them out; the bug is that its {@code preferredLayoutSize} answers as
 * if everything sat on ONE row, so the parent never grants the height
 * the wrap needs and the second row is clipped invisible. This
 * subclass recomputes the preferred height by simulating the flow at
 * the width the container actually has (falling back to one row before
 * the first layout, when the width is still zero).
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return wrappedSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension min = wrappedSize(target, false);
        // FlowLayout's minimum still assumes one row; keep the wrapped
        // height but let the width shrink to the widest single child
        min.width -= getHgap() + 1;
        return min;
    }

    private Dimension wrappedSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE; // pre-layout: one row
            }
            Insets insets = target.getInsets();
            int maxRowWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
            int rowWidth = 0;
            int rowHeight = 0;
            int width = 0;
            int height = insets.top + insets.bottom + getVgap() * 2;
            boolean any = false;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > maxRowWidth) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
                any = true;
            }
            width = Math.max(width, rowWidth);
            if (any) {
                height += rowHeight;
            }
            return new Dimension(width + insets.left + insets.right + getHgap() * 2,
                    height);
        }
    }
}
