package org.nmox.studio.ui.browser;

import javax.swing.AbstractButton;

/**
 * Back and Forward point the way the reader's lines run.
 *
 * <p>Back goes to where the reader came from, and a line of English comes
 * from the left: its arrow points left. A line of Hebrew or Arabic comes
 * from the right, and every browser those readers use points Back to the
 * right. The Browser's toolbar took its place at the line's start when the
 * window mirrored (3.5.3) and its arrows did not turn: Back sat at the far
 * right, pointing left, at Forward.
 *
 * <p>The glyphs follow the BUTTON's direction and not the locale's, so they
 * turn with the window on a live language switch, and stay as authored
 * inside anything that keeps its own direction.
 */
public final class NavArrows {

    static final String LEFT = "←";
    static final String RIGHT = "→";

    private NavArrows() {
    }

    /** The glyph for Back, in a line that runs left-to-right or not. */
    public static String back(boolean leftToRight) {
        return leftToRight ? LEFT : RIGHT;
    }

    /** The glyph for Forward, in a line that runs left-to-right or not. */
    public static String forward(boolean leftToRight) {
        return leftToRight ? RIGHT : LEFT;
    }

    /** Gives the pair their glyphs now and whenever either one's direction changes. */
    public static void follow(AbstractButton back, AbstractButton forward) {
        Runnable point = () -> {
            back.setText(back(back.getComponentOrientation().isLeftToRight()));
            forward.setText(forward(forward.getComponentOrientation().isLeftToRight()));
        };
        back.addPropertyChangeListener("componentOrientation", e -> point.run());
        forward.addPropertyChangeListener("componentOrientation", e -> point.run());
        point.run();
    }
}
