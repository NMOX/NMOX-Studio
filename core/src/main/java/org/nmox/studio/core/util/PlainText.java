package org.nmox.studio.core.util;

/**
 * Text for a Swing sink that renders markup, made plain (v2.86.0).
 * Swing's {@code BasicHTML.isHTMLString} decides that a label, a
 * button, a menu item, a tooltip or an option-pane message is markup
 * when its text BEGINS with {@code <html>} — six characters, checked
 * on the head only. A tooltip is the case the component-level
 * {@code html.disable} property cannot reach: the property is read on
 * the {@code JToolTip} Swing creates per hover, not on the component
 * that carries the text (measured on the JDK the product ships on, and
 * pinned by PlainTextTest). So a tooltip whose head is not the
 * product's own literal — a device label, a preset description, a
 * project path, a language server's install command — rides
 * {@link #plain}, which prepends one space when the head would read as
 * markup and leaves every other text untouched. Labels prefer the
 * property at construction ({@link PlainTables#plain}); the status
 * line's {@link PlainStatus#text} is this rule under its older name.
 * A sink that MEANS its markup (a tooltip with a {@code <br>} between
 * two facts) splices every external piece through {@link #escape}
 * instead — the v2.75.0 serving-chip rule, in one home.
 */
public final class PlainText {

    private PlainText() {
    }

    /** The text, with a leading space added when its head would read as markup. */
    public static String plain(String text) {
        if (text == null) {
            return null;
        }
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (text.regionMatches(true, i, "<html", 0, 5)) {
            return " " + text.substring(i);
        }
        return text;
    }

    /**
     * The words a markup-painted label shows, for a screen reader (3.4): the
     * tags removed and the common entities decoded. A renderer that paints
     * {@code <html>} (the platform's node renderer paints a file's git state
     * that way) otherwise gives assistive technology its accessible name AS
     * markup: VoiceOver read {@code <font color="#ff6464">a.txt</font>}.
     * Null answers the empty string.
     */
    public static String words(String html) {
        if (html == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(html.length());
        boolean inTag = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>' && inTag) {
                inTag = false;
            } else if (!inTag) {
                out.append(c);
            }
        }
        return out.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&").strip();
    }

    /**
     * The characters that could open or close a tag, or break out of a
     * double- OR single-quoted attribute, as entities — so a spliced
     * external string can never become markup wherever an authored
     * {@code <html>} places it.
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * A file or folder name as one line of ordinary characters, for a
     * sink that takes names from disk: control characters become a space
     * and a very long name is cut at {@code max} code points with an
     * ellipsis (3.5.13).
     *
     * <p>A notification's text is built into markup by the platform
     * ({@code XMLUtil.toElementContent}), which THROWS for a character
     * below U+0020 other than tab and the line ends. A repository whose
     * folder name held one made its trust notice vanish: the exception was
     * swallowed where the notice was posted, after the notice had been
     * counted as shown.
     */
    public static String oneLine(String name, int max) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int[] points = name.codePoints().toArray();
        int limit = Math.max(1, max);
        for (int i = 0; i < points.length && i < limit; i++) {
            int cp = points[i];
            out.appendCodePoint(Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029 ? ' ' : cp);
        }
        if (points.length > limit) {
            out.append('\u2026');
        }
        return out.toString();
    }
}
