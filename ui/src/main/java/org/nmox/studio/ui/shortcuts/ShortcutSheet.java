package org.nmox.studio.ui.shortcuts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The keyboard-shortcut sheet, the pure half: the platform's keystroke
 * notation turned into what a person reads on their keyboard, and the
 * sheet's rendering. The notation law (measured v2.61.0): {@code D} is the
 * default modifier — ⌘ on macOS, Ctrl elsewhere; {@code O} is the OTHER
 * one — ⌃ on macOS, Alt elsewhere; {@code A} is Alt (⌥); {@code S} is
 * Shift; {@code C} is Ctrl; {@code M} is Meta. The sheet is derived from
 * the running keymap, never a hand-kept list, so it cannot rot.
 */
public final class ShortcutSheet {

    /** One row: the chord as a person reads it and the action it fires. */
    public record Row(String chord, String action) {
    }

    private ShortcutSheet() {
    }

    /**
     * "DA-G" → "⌥⌘G" on macOS, "Ctrl+Alt+G" elsewhere. Modifier order on
     * macOS follows the menu-bar convention ⌃ ⌥ ⇧ ⌘; elsewhere Ctrl, Alt,
     * Shift, Meta. A key name without a modifier block renders as the key.
     */
    public static String humanChord(String nbKey, boolean mac) {
        return org.nmox.studio.core.util.Chords.human(nbKey, mac); // one vocabulary, in core since v2.87.0
    }

    /** Rows sorted by action name (case-insensitive), chords already human. */
    public static List<Row> sorted(List<Row> rows) {
        List<Row> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing((Row r) -> r.action().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(Row::chord));
        return out;
    }

    /** A markdown table for the clipboard. */
    public static String renderMarkdown(List<Row> rows, String profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Shortcut | Action |\n|---|---|\n");
        for (Row r : sorted(rows)) {
            sb.append("| `").append(r.chord()).append("` | ").append(r.action().replace("|", "\\|")).append(" |\n");
        }
        sb.append("\n_NMOX Studio shortcuts, keymap profile: ").append(profile).append("_\n");
        return sb.toString();
    }
}
