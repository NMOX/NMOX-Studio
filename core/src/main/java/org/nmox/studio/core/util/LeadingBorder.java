package org.nmox.studio.core.util;

import java.awt.Component;
import java.awt.Insets;
import javax.swing.border.AbstractBorder;

/**
 * Empty space around a component, with its two side margins named for the
 * reader and not for the screen: LEADING is where a line starts, the left
 * for English and the right for Hebrew and Arabic.
 *
 * <p>{@code BorderFactory.createEmptyBorder(top, left, bottom, right)} names
 * screen sides, and an indent written with it stays on the left when the
 * window around it mirrors: the Workbench's hints were indented at the line's
 * END for a right-to-left reader (3.5.3). The insets are read from the
 * component's direction each time they are asked for, so a live language
 * switch moves them with everything else.
 */
public final class LeadingBorder extends AbstractBorder {

    private final int top;
    private final int leading;
    private final int bottom;
    private final int trailing;

    public LeadingBorder(int top, int leading, int bottom, int trailing) {
        this.top = top;
        this.leading = leading;
        this.bottom = bottom;
        this.trailing = trailing;
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        boolean leftToRight = c == null || c.getComponentOrientation().isLeftToRight();
        insets.set(top, leftToRight ? leading : trailing, bottom, leftToRight ? trailing : leading);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
