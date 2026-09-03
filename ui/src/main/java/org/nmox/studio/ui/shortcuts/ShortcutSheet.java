package org.nmox.studio.ui.shortcuts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    private static final Map<String, String> NAMED_KEYS = Map.ofEntries(
            Map.entry("SLASH", "/"), Map.entry("PERIOD", "."), Map.entry("COMMA", ","),
            Map.entry("SEMICOLON", ";"), Map.entry("MINUS", "-"), Map.entry("EQUALS", "="),
            Map.entry("BACK_SLASH", "\\"), Map.entry("OPEN_BRACKET", "["), Map.entry("CLOSE_BRACKET", "]"),
            Map.entry("BACK_QUOTE", "`"), Map.entry("QUOTE", "'"), Map.entry("SPACE", "Space"),
            Map.entry("ENTER", "Enter"), Map.entry("ESCAPE", "Esc"), Map.entry("TAB", "Tab"),
            Map.entry("BACK_SPACE", "Backspace"), Map.entry("DELETE", "Delete"),
            Map.entry("UP", "↑"), Map.entry("DOWN", "↓"), Map.entry("LEFT", "←"), Map.entry("RIGHT", "→"),
            Map.entry("HOME", "Home"), Map.entry("END", "End"),
            Map.entry("PAGE_UP", "PageUp"), Map.entry("PAGE_DOWN", "PageDown"));

    private ShortcutSheet() {
    }

    /**
     * "DA-G" → "⌥⌘G" on macOS, "Ctrl+Alt+G" elsewhere. Modifier order on
     * macOS follows the menu-bar convention ⌃ ⌥ ⇧ ⌘; elsewhere Ctrl, Alt,
     * Shift, Meta. A key name without a modifier block renders as the key.
     */
    public static String humanChord(String nbKey, boolean mac) {
        if (nbKey == null || nbKey.isBlank()) {
            return "";
        }
        String mods = "";
        String key = nbKey;
        int dash = nbKey.lastIndexOf('-');
        if (dash > 0 && dash < nbKey.length() - 1) {
            mods = nbKey.substring(0, dash);
            key = nbKey.substring(dash + 1);
        }
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        boolean meta = false;
        for (char c : mods.toCharArray()) {
            switch (c) {
                case 'C' -> ctrl = true;
                case 'A' -> alt = true;
                case 'S' -> shift = true;
                case 'M' -> meta = true;
                case 'D' -> { if (mac) { meta = true; } else { ctrl = true; } }
                case 'O' -> { if (mac) { ctrl = true; } else { alt = true; } }
                default -> { }
            }
        }
        String keyText = NAMED_KEYS.getOrDefault(key, key.length() == 1 ? key.toUpperCase(java.util.Locale.ROOT) : key);
        if (mac) {
            return (ctrl ? "⌃" : "") + (alt ? "⌥" : "") + (shift ? "⇧" : "") + (meta ? "⌘" : "") + keyText;
        }
        List<String> parts = new ArrayList<>();
        if (ctrl) { parts.add("Ctrl"); }
        if (alt) { parts.add("Alt"); }
        if (shift) { parts.add("Shift"); }
        if (meta) { parts.add("Meta"); }
        parts.add(keyText);
        return String.join("+", parts);
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
