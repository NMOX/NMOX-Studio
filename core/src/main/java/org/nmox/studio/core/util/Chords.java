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

    /** {@link #forOs} for the machine this is running on. */
    public static String forThisOs(String text) {
        return forOs(text, org.openide.util.BaseUtilities.isMac());
    }

    /**
     * A sentence that names its chords the way a Mac writes them, read on
     * {@code mac ? this : any other} keyboard: {@code ⌥⌘K} becomes
     * {@code Ctrl+Alt+K}, {@code ⌘-click} becomes {@code Ctrl-click}, and a
     * modifier named on its own becomes its name.
     *
     * <p>The product's strings are written once, in fifteen languages, in
     * the Mac notation — compact, and the one the chord vocabulary above
     * already speaks. Every walk before 3.5 was taken on a Mac, so nobody
     * saw that the Welcome told a Windows user to press ⌥⌘7 on a keyboard
     * that has neither key. The first Windows and Linux walks photographed
     * it. The strings stay as written; whoever shows one passes it through
     * here, and {@code MacChordsReachOnlyMacsGateTest} holds every consumer
     * to that.
     *
     * <p>The mapping is the notation law read backwards: ⌘ is the default
     * modifier (Ctrl elsewhere), ⌥ is Alt, ⇧ is Shift. ⌃ alone is Ctrl on
     * every keyboard; ⌃ beside ⌘ is the platform's other modifier, which is
     * Alt off a Mac, so {@code ⌃⌘G} reads {@code Ctrl+Alt+G}.
     * What follows the run decides its shape: a hyphen keeps the hyphen
     * ({@code Ctrl-click}); sentence punctuation, a space or the end leaves
     * a bare modifier name; anything else is the key ({@code Ctrl+Z}).
     */
    public static String forOs(String text, boolean mac) {
        if (mac || text == null || !hasMacGlyph(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!isMacGlyph(c)) {
                out.append(c);
                i++;
                continue;
            }
            boolean command = false;
            boolean control = false;
            boolean alt = false;
            boolean shift = false;
            while (i < text.length() && isMacGlyph(text.charAt(i))) {
                switch (text.charAt(i)) {
                    case '\u2318' -> command = true;
                    case '\u2303' -> control = true;
                    case '\u2325' -> alt = true;
                    default -> shift = true;
                }
                i++;
            }
            // ⌃ beside ⌘ is the platform's OTHER modifier (O), which is Alt
            // off a Mac; ⌃ alone is Ctrl on every keyboard
            boolean ctrl = command || control;
            alt = alt || (command && control);
            List<String> parts = new ArrayList<>(3);
            if (ctrl) { parts.add("Ctrl"); }
            if (alt) { parts.add("Alt"); }
            if (shift) { parts.add("Shift"); }
            out.append(String.join("+", parts));
            if (i < text.length() && isKeyStart(text.charAt(i))) {
                out.append('+');
            }
        }
        return out.toString();
    }

    /** ⌘ ⌥ ⇧ ⌃ — the four modifier glyphs a Mac keyboard prints. */
    private static boolean isMacGlyph(char c) {
        return c == '\u2318' || c == '\u2325' || c == '\u21E7' || c == '\u2303';
    }

    private static boolean hasMacGlyph(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isMacGlyph(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the character after a run of modifiers is the chord's key, rather than the sentence going on. */
    private static boolean isKeyStart(char c) {
        if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
            return false;
        }
        // a hyphen keeps its own shape; these end a clause in the scripts the product ships
        return "-,.;:!?)]\u3001\u3002\uFF0C\uFF09\u060C\u061B\u061F\u200E\u200F\u202C".indexOf(c) < 0;
    }
}
