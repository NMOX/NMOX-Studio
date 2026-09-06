package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One chord vocabulary for the whole product: the platform's keystroke
 * notation — or a key event's modifiers and key name — turned into what a
 * person reads on their keyboard. Promoted from the Keyboard Shortcuts
 * sheet (v2.64.0) the moment a second reader arrived (the presenter's
 * keystroke display, v2.87.0), before a second copy could grow. The
 * notation law (measured v2.61.0): {@code D} is the default modifier — ⌘
 * on macOS, Ctrl elsewhere; {@code O} is the OTHER one — ⌃ on macOS, Alt
 * elsewhere; {@code A} is Alt (⌥); {@code S} is Shift; {@code C} is Ctrl;
 * {@code M} is Meta. Modifier order on macOS follows the menu-bar
 * convention ⌃ ⌥ ⇧ ⌘; elsewhere Ctrl, Alt, Shift, Meta.
 */
public final class Chords {

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

    private Chords() {
    }

    /** "DA-G" → "⌥⌘G" on macOS, "Ctrl+Alt+G" elsewhere. A key name without a modifier block renders as the key. */
    public static String human(String nbKey, boolean mac) {
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
        return human(ctrl, alt, shift, meta, key, mac);
    }

    /**
     * The same rendering from a key event's parts: which modifiers are
     * down and the key's platform name ({@code G}, {@code SLASH}, {@code F5}
     * — the {@code VK_} constant without its prefix).
     */
    public static String human(boolean ctrl, boolean alt, boolean shift, boolean meta, String keyName, boolean mac) {
        String key = keyName == null ? "" : keyName;
        String keyText = NAMED_KEYS.getOrDefault(key, key.length() == 1 ? key.toUpperCase(Locale.ROOT) : key);
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
}
