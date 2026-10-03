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

    /**
     * Client property naming a component whose TEXT runs left to right in every
     * language: source code, JSON, SQL, a URL, a log line. The orientation
     * sweep mirrors it and the painted-surface pass puts it back, so a Hebrew
     * reader sees {@code {"url": …}} and not its reflection. The walk of the
     * first Hebrew build found the Agent Port's {@code .mcp.json} block with
     * its braces on the right.
     */
    public static final String KEEP_LTR = "nmox.direction.keepLeftToRight";

    private TextDirection() {
    }

    /** Mark {@code c} as left-to-right text in every language; returns it. */
    public static <T extends javax.swing.JComponent> T keepLeftToRight(T c) {
        c.putClientProperty(KEEP_LTR, Boolean.TRUE);
        c.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        return c;
    }

    /**
     * Says that {@code c} holds PROSE: words a person wrote, which run the
     * way that person reads. It changes nothing. It is the decision, written
     * where the next reader looks, and it is what
     * {@code TextInputsChooseADirectionTest} asks every text input for:
     * this, or {@link #keepLeftToRight}. A host name, a path, an address, a
     * command or a line of code is not prose, and mirrored it reads wrong:
     * the right-to-left walk photographed the Browser's address as
     * {@code /http://127.0.0.1:3000}, its last slash leading the line (3.5.3).
     */
    public static <T extends javax.swing.JComponent> T followsReader(T c) {
        return c;
    }

    /**
     * Client property that marks a horizontal split pane whose two sides
     * change places for a right-to-left reader (3.5.13).
     */
    public static final String SIDES_FOLLOW_READER = "nmox.split.sidesFollowReader";

    /**
     * Marks a horizontal split pane of the product's own as one whose sides
     * follow the reader: under Hebrew or Arabic its first side (a list, a
     * tree, a palette) stands on the right, where the line begins.
     *
     * <p>It is a mark and not the default, and 3.5.12 is why: that release
     * exchanged the sides of EVERY horizontal split pane in the JVM, the
     * platform's own included, and the platform addresses a side by its
     * slot. Find in Projects turns its preview off with
     * {@code setRightComponent(null)}, which removed the results tree; the
     * refactoring preview replaced the list of usages; the diff view drew
     * its connectors against the wrong sides; and a divider the platform
     * saves and applies again flipped on every use. Only a pane whose
     * author built it knowing its sides may be exchanged is exchanged, and
     * a gate reads every split pane the product builds so that each one is
     * marked or says why it stays ({@code SplitSidesDecidedGateTest}).
     *
     * <p>A marked pane must be complete (both sides set) before it is added
     * to a window, and must not address its sides by slot afterwards.
     */
    public static <T extends javax.swing.JSplitPane> T sidesFollowReader(T split) {
        split.putClientProperty(SIDES_FOLLOW_READER, Boolean.TRUE);
        return split;
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
