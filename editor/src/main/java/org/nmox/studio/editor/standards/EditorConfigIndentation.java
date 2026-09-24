package org.nmox.studio.editor.standards;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The indentation half of EditorConfig, as the editor's own preference
 * keys. {@link EditorConfig#propertiesFor} answers what a project's
 * {@code .editorconfig} says about a file; this class says what that
 * means to the NetBeans editor, whose typing, Tab key and re-indent all
 * read {@code expand-tabs}, {@code indent-shift-width},
 * {@code spaces-per-tab} and {@code tab-size} through
 * {@code CodeStylePreferences} (measured in RELEASE310's
 * {@code IndentUtils.indentLevelSize} and the Tab action in
 * {@code BaseKit}).
 *
 * <p>The mapping follows the EditorConfig specification:
 * <ul>
 * <li>{@code indent_style = tab | space} sets whether indentation is
 *     written with tabs or spaces;</li>
 * <li>{@code indent_size = N} is the width of one indentation level;
 *     {@code indent_size = tab} means "the tab width";</li>
 * <li>{@code tab_width = N} is the width of a tab, and defaults to
 *     {@code indent_size} when that is a number.</li>
 * </ul>
 * Anything else - {@code unset}, a word, zero, a negative or an absurd
 * number - leaves the editor's own setting in place rather than
 * guessing. Pure: the caller supplies the editor's tab size for the one
 * case the file defers to it.
 */
public final class EditorConfigIndentation {

    /** The editor's preference keys (SimpleValueNames spells the same strings). */
    public static final String EXPAND_TABS = "expand-tabs";
    public static final String INDENT_SHIFT_WIDTH = "indent-shift-width";
    public static final String SPACES_PER_TAB = "spaces-per-tab";
    public static final String TAB_SIZE = "tab-size";

    /** Wider than any real indentation, narrow enough that a typo cannot make one. */
    static final int MAX_WIDTH = 32;

    private EditorConfigIndentation() {
    }

    /**
     * The editor preferences a set of EditorConfig properties overrides.
     * Empty when the properties say nothing about indentation, which is
     * how a caller knows to leave the document alone.
     *
     * @param props lowercase keys and values, as {@link EditorConfig#propertiesFor} returns them
     * @param editorTabSize the editor's tab size, asked only when
     *        {@code indent_size = tab} and no {@code tab_width} says otherwise
     */
    public static Map<String, String> overrides(Map<String, String> props, IntSupplier editorTabSize) {
        Map<String, String> out = new LinkedHashMap<>();
        String style = props.get("indent_style");
        if ("tab".equals(style)) {
            out.put(EXPAND_TABS, "false");
        } else if ("space".equals(style)) {
            out.put(EXPAND_TABS, "true");
        }

        Integer tabWidth = width(props.get("tab_width"));
        String sizeValue = props.get("indent_size");
        Integer indentSize = width(sizeValue);
        boolean sizeIsTab = "tab".equals(sizeValue);

        if (tabWidth == null && indentSize != null) {
            tabWidth = indentSize; // the spec's default for tab_width
        }
        if (sizeIsTab) {
            indentSize = tabWidth != null ? tabWidth : editorTabSize.getAsInt();
        }
        if (indentSize != null) {
            out.put(INDENT_SHIFT_WIDTH, Integer.toString(indentSize));
            out.put(SPACES_PER_TAB, Integer.toString(indentSize));
        }
        if (tabWidth != null) {
            out.put(TAB_SIZE, Integer.toString(tabWidth));
        }
        return out;
    }

    /** A positive width the editor can use, or null for anything else. */
    static Integer width(String value) {
        if (value == null || value.isEmpty() || value.length() > 3) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return null;
            }
        }
        int n = Integer.parseInt(value);
        return n >= 1 && n <= MAX_WIDTH ? n : null;
    }
}
