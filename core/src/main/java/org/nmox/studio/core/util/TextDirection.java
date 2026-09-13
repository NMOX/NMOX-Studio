package org.nmox.studio.core.util;

import java.awt.ComponentOrientation;
import java.util.Locale;

/**
 * Which way the interface runs.
 *
 * <p>Swing does NOT take this from the locale. A component built while the
 * default locale is Hebrew still reports left-to-right — measured, before a
 * line of this was written: {@code new JPanel()} under {@code Locale("he")}
 * answers {@code ComponentOrientation.LEFT_TO_RIGHT}. The text inside a label
 * shapes and reorders correctly, because bidi lives in the text layout engine;
 * the LAYOUT — which side a label sits on, which way a tree indents, the order
 * of a toolbar — stays as authored until something calls
 * {@code applyComponentOrientation}. Nothing in the product ever did.
 *
 * <p>So direction is a decision the product makes, from one place, and this is
 * the place. {@link java.awt.ComponentOrientation#getOrientation(Locale)} knows
 * the right answer for every language the JDK knows — including ones we do not
 * ship — so the rule is asked, never listed.
 *
 * <p>The {@code nmox.rtl} property forces an answer. That is not a debugging
 * convenience: the mechanics land before any right-to-left LANGUAGE does, and
 * a walk of a mirrored English build is the only way to see whether the layout
 * mirrors without also asking whether the words are right. Two questions, two
 * instruments.
 */
public final class TextDirection {

    /** {@code -J-Dnmox.rtl=true|false} forces direction; absent, the locale decides. */
    public static final String FORCE = "nmox.rtl";

    private TextDirection() {
    }

    /** Does the interface run right-to-left for this locale? */
    public static boolean isRightToLeft(Locale locale) {
        String forced = System.getProperty(FORCE);
        if (forced != null && !forced.isBlank()) {
            return Boolean.parseBoolean(forced.trim());
        }
        return locale != null
                && !ComponentOrientation.getOrientation(locale).isLeftToRight();
    }

    /** The orientation to apply, for the locale the product is running in. */
    public static ComponentOrientation orientation(Locale locale) {
        return isRightToLeft(locale)
                ? ComponentOrientation.RIGHT_TO_LEFT
                : ComponentOrientation.LEFT_TO_RIGHT;
    }
}
