package org.nmox.studio.editor.standards;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The right-margin half of what a project says about a file, as the
 * editor's own preference key: EditorConfig's {@code max_line_length} -
 * and, translated into it, the first of VS Code's {@code editor.rulers}
 * ({@link VsCodeSettings}).
 *
 * <p>The platform editor draws ONE vertical line, its "text limit", at
 * the column {@code text-limit-width} names; an editor document reads
 * that column through {@code CodeStylePreferences} (RELEASE310's
 * {@code NbEditorDocument} puts a lazy {@code text-limit-width} property
 * on every document, and {@code DocumentViewOp.updateTextLimitLine} reads
 * the property), which is the per-document seam
 * {@link EditorConfigCodeStyle} already answers indentation through. So a
 * project's column reaches the files it is stated for and no other, and
 * nothing is written to the user's own right-margin preference.
 *
 * <ul>
 * <li>{@code max_line_length = N}: the line is drawn at column N;</li>
 * <li>{@code max_line_length = off} - EditorConfig's "no limit", and what
 *     an empty {@code "editor.rulers": []} is translated to: no line is
 *     drawn (the view draws nothing for a width of zero);</li>
 * <li>anything else - {@code unset}, a word, zero, a negative or an
 *     absurd number - leaves the editor's own setting in place.</li>
 * </ul>
 * Whether the line is drawn at all stays the user's own preference
 * ({@code text-limit-line-visible}): a project moves the line, it does
 * not switch it back on for someone who turned it off. Pure.
 */
public final class EditorConfigMargin {

    /** The editor's preference key (SimpleValueNames.TEXT_LIMIT_WIDTH spells the same string). */
    public static final String TEXT_LIMIT_WIDTH = "text-limit-width";

    /** Wider than any real line limit, narrow enough that a typo cannot make one. */
    static final int MAX_COLUMN = 1_000;

    private EditorConfigMargin() {
    }

    /**
     * The editor preference a set of EditorConfig properties overrides.
     * Empty when the properties say nothing about the line length.
     *
     * @param props lowercase keys and values, as {@link ProjectFormatting#propertiesFor} returns them
     */
    public static Map<String, String> overrides(Map<String, String> props) {
        Map<String, String> out = new LinkedHashMap<>();
        String value = props.get("max_line_length");
        if ("off".equals(value)) {
            out.put(TEXT_LIMIT_WIDTH, "0");
        } else {
            Integer column = column(value);
            if (column != null) {
                out.put(TEXT_LIMIT_WIDTH, Integer.toString(column));
            }
        }
        return out;
    }

    /** A column from 1 to {@link #MAX_COLUMN}, or null for anything else. */
    static Integer column(String value) {
        if (value == null || value.isEmpty() || value.length() > 4) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return null;
            }
        }
        int n = Integer.parseInt(value);
        return n >= 1 && n <= MAX_COLUMN ? n : null;
    }
}
