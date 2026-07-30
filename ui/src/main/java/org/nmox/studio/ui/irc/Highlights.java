package org.nmox.studio.ui.irc;

import java.util.List;
import java.util.Locale;

/**
 * "Did someone say my name?" — the highlight matcher behind mention
 * badges and platform notifications. A message highlights when it
 * contains the user's nick (or any user-defined extra keyword from the
 * IRC options) as a WHOLE WORD, case-insensitively: {@code "dave: hi"}
 * and {@code "thanks Dave!"} match a user named dave, but
 * {@code "davenport"} must not — a substring match would page half the
 * channel every time a longer name scrolls by.
 *
 * <p>Word boundaries here are IRC-shaped: a nick may itself contain
 * {@code [] {} \ | ^ _ -} (all legal nick characters), so the boundary
 * test is "the neighbouring character could not be part of a nick",
 * not Java's {@code \b}. Pure text-in/boolean-out, exhaustively
 * unit-tested.
 */
public final class Highlights {

    private Highlights() {
    }

    /** True when {@code text} mentions {@code nick} or any extra keyword. */
    public static boolean matches(String nick, List<String> extraKeywords, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (nick != null && !nick.isEmpty() && containsWord(text, nick)) {
            return true;
        }
        if (extraKeywords != null) {
            for (String kw : extraKeywords) {
                if (kw != null && !kw.isEmpty() && containsWord(text, kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Case-insensitive whole-word containment with IRC-nick boundaries. */
    static boolean containsWord(String text, String word) {
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = word.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            boolean startOk = at == 0 || !isNickChar(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean endOk = end == haystack.length() || !isNickChar(haystack.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
    }

    /** Characters that can be PART of an IRC nick — no boundary there. */
    private static boolean isNickChar(char c) {
        return Character.isLetterOrDigit(c) || "[]{}\\|^_-`".indexOf(c) >= 0;
    }
}
