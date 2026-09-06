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

    /** The four characters that could open or close a tag or an attribute, as entities. */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
