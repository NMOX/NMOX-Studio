package org.nmox.studio.editor.classic;

/**
 * Prefix matching for classic API names, which — unlike plain
 * identifiers — carry dots and leading sigils: {@code $.ajax},
 * {@code _.debounce}, {@code ko.observable}, {@code .addClass}. The
 * standard identifier prefix walk stops at a dot, so this matcher has
 * its own walk and two rules:
 *
 * <ul>
 *   <li><b>direct</b>: the entry name starts with the typed prefix,
 *       case-insensitively ({@code $.aj} → {@code $.ajax}); the whole
 *       prefix is replaced on accept.</li>
 *   <li><b>dot-tail</b>: an instance-method entry (leading dot) matches
 *       the prefix's last {@code .segment} ({@code myEl.addC} →
 *       {@code .addClass}); only that tail is replaced, so accepting
 *       yields {@code myEl.addClass}.</li>
 * </ul>
 *
 * Pure functions, no state.
 */
public final class ClassicApiMatcher {

    private ClassicApiMatcher() {
    }

    /**
     * The classic prefix ending at {@code offset}: identifier characters
     * plus dots, walked back from the caret. {@code "x = $(li).addC"}
     * at the end yields {@code ".addC"} (the paren stops the walk);
     * {@code "$.aj"} yields {@code "$.aj"}.
     */
    public static String prefixAt(String text, int offset) {
        int start = Math.min(offset, text.length());
        int i = start;
        while (i > 0 && isPrefixChar(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(i, start);
    }

    private static boolean isPrefixChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    /**
     * How many characters before the caret an accepted {@code entryName}
     * should replace, or {@code -1} when the entry does not match the
     * prefix. An empty prefix matches everything (explicit Ctrl+Space
     * browses the whole detected surface).
     */
    public static int matchLength(String entryName, String prefix) {
        if (prefix.isEmpty()) {
            return 0;
        }
        if (startsWithIgnoreCase(entryName, prefix)) {
            return prefix.length();
        }
        if (entryName.charAt(0) == '.') {
            int lastDot = prefix.lastIndexOf('.');
            if (lastDot >= 0) {
                String tail = prefix.substring(lastDot);
                if (startsWithIgnoreCase(entryName, tail)) {
                    return tail.length();
                }
            }
        }
        return -1;
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
