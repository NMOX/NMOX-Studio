package org.nmox.studio.core.util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.ToIntFunction;
import javax.swing.JLabel;

/**
 * A label that shows as much of its text as its width allows, and never
 * decides how wide its window is.
 *
 * <p>Two ways of cutting, because the right one depends on what the text
 * is (the v2.119.0 lesson). A path keeps its ENDS: {@link Cut#MIDDLE}. A
 * sentence keeps its BEGINNING, and stops at the end of a word when one is
 * near: {@link Cut#END}.
 *
 * <p>The Workbench used to cut its subtitles at 38 characters whatever the
 * pane's width, so at the default size they ended mid-word with room to
 * spare ({@code Tab flip…}, {@code Cloudflare f…}; the 3.5 walks, on all
 * three systems). A character budget cannot know the width; a label can.
 *
 * <p>The label asks for at most its cap, shrinks to nothing when squeezed,
 * and can be told to take whatever width is left over. Whatever is cut is
 * on the tooltip, and the whole text is always the accessible description.
 */
public class FitLabel extends JLabel {

    /** Which part of a text survives a cut. */
    public enum Cut {
        /** The ends survive: a path. */
        MIDDLE,
        /** The beginning survives: a sentence. */
        END
    }

    static final String ELLIPSIS = "…";

    private Cut cut;
    private final int cap;
    private final boolean grows;
    private String full = "";
    private boolean cutNow;

    /**
     * @param cut   which part of the text survives
     * @param cap   the most width, in pixels, this label asks its container for
     * @param grows whether it takes more than that when the container has it
     */
    public FitLabel(Cut cut, int cap, boolean grows) {
        this.cut = cut;
        this.cap = cap;
        this.grows = grows;
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refit();
            }
        });
    }

    /** Shows {@code text}, rendered as text and never as markup. */
    public void setFull(String text) {
        full = text == null ? "" : text;
        getAccessibleContext().setAccessibleDescription(full);
        refit();
    }

    /** Shows text of another kind in the same place: a path's label that now holds a list. */
    public void setFull(String text, Cut how) {
        cut = how;
        setFull(text);
    }

    /** The whole text, however much of it is on screen. */
    public String getFull() {
        return full;
    }

    /** Whether the text on screen is shorter than the whole. */
    public boolean isCut() {
        return cutNow;
    }

    /** A new font (Presentation Mode, a look-and-feel change) re-measures the cut. */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (full != null) {
            refit();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        d.width = Math.min(fm.stringWidth(PlainText.plain(full)) + in.left + in.right, cap);
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.width = 0;
        return d;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension d = super.getMaximumSize();
        if (grows) {
            d.width = Short.MAX_VALUE;
        }
        return d;
    }

    /**
     * What the tooltip says. By default the whole text when it is cut and
     * nothing when it is not: a tooltip repeating what is on screen is
     * noise, and it also takes the pointer away from whatever the label
     * sits on.
     */
    protected String tooltipFor(String whole, boolean isCut) {
        return isCut ? whole : null;
    }

    private void refit() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        int room = getWidth() - in.left - in.right;
        // the measure includes what PlainText adds, so what is measured is what is painted
        ToIntFunction<String> width = s -> fm.stringWidth(PlainText.plain(s));
        String shown = room <= 0 ? full
                : cut == Cut.MIDDLE ? fitMiddle(full, width, room) : fitEnd(full, width, room);
        cutNow = !shown.equals(full);
        super.setText(PlainText.plain(shown));
        String tip = tooltipFor(full, cutNow);
        setToolTipText(tip == null || tip.isEmpty() ? null : PlainText.plain(tip));
    }

    /**
     * The longest middle-elided form of {@code text} whose width fits
     * {@code room}: a head and a tail joined by an ellipsis, the tail given
     * the larger share because it names the thing, cut on code points so a
     * surrogate pair is never split. The text itself when it fits.
     */
    public static String fitMiddle(String text, ToIntFunction<String> width, int room) {
        if (text == null || text.isEmpty() || width.applyAsInt(text) <= room) {
            return text == null ? "" : text;
        }
        int[] cps = text.codePoints().toArray();
        int lo = 0;
        int hi = cps.length;
        String best = ELLIPSIS;
        while (lo <= hi) {
            int keep = (lo + hi) >>> 1;
            int head = keep * 2 / 5;
            int tail = keep - head;
            String candidate = new String(cps, 0, head) + ELLIPSIS + new String(cps, cps.length - tail, tail);
            if (width.applyAsInt(candidate) <= room) {
                best = candidate;
                lo = keep + 1;
            } else {
                hi = keep - 1;
            }
        }
        return best;
    }

    /**
     * The longest beginning of {@code text} that fits {@code room} with an
     * ellipsis after it. It ends at the end of a word when that costs less
     * than half of what would fit, and otherwise at a letter: a language
     * written without spaces has no word ends to offer, and one long word
     * should not become an ellipsis alone. Trailing separators are dropped
     * ({@code "cables, …"} reads as a list about to continue; it is not
     * going to), a combining mark is never parted from its letter and a
     * surrogate pair never split. The text itself when it fits.
     */
    public static String fitEnd(String text, ToIntFunction<String> width, int room) {
        if (text == null || text.isEmpty() || width.applyAsInt(text) <= room) {
            return text == null ? "" : text;
        }
        int[] cps = text.codePoints().toArray();
        int lo = 0;
        int hi = cps.length - 1;
        int fits = 0; // the most code points that fit before the ellipsis
        while (lo <= hi) {
            int keep = (lo + hi) >>> 1;
            if (width.applyAsInt(new String(cps, 0, keep) + ELLIPSIS) <= room) {
                fits = keep;
                lo = keep + 1;
            } else {
                hi = keep - 1;
            }
        }
        // never part a mark from the letter it sits on
        while (fits > 0 && fits < cps.length && joinsThePrevious(cps[fits])) {
            fits--;
        }
        int word = fits;
        // back to the end of a word, unless the cut already falls on one
        if (fits < cps.length && !Character.isWhitespace(cps[fits])) {
            while (word > 0 && !Character.isWhitespace(cps[word - 1])) {
                word--;
            }
        }
        int kept = trimSeparators(cps, word);
        if (kept * 2 < fits) {
            kept = trimSeparators(cps, fits); // no word end near: cut at the letter
        }
        return new String(cps, 0, kept) + ELLIPSIS;
    }

    private static boolean joinsThePrevious(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || cp == 0x200D || cp == 0x200C;
    }

    /** The length without trailing spaces, dashes, commas, dots between items and opening brackets. */
    private static int trimSeparators(int[] cps, int length) {
        int end = length;
        while (end > 0) {
            int cp = cps[end - 1];
            int type = Character.getType(cp);
            boolean separator = Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                    || type == Character.DASH_PUNCTUATION || type == Character.START_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || cp == ',' || cp == ';' || cp == ':' || cp == '·' || cp == '•'
                    || cp == '、' || cp == '，' || cp == '،' || cp == '/' || cp == '|';
            if (!separator) {
                break;
            }
            end--;
        }
        return end;
    }
}
