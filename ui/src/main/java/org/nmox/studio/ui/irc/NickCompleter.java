package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab nick completion, the WeeChat way: type a prefix, press Tab, get
 * the first matching nick from the channel — press Tab again and cycle
 * through the rest. At the start of the line the completion is an
 * address ({@code "nick: "}), mid-line it is just the nick plus a space.
 * Any other keystroke {@link #reset() resets} the cycle so the next Tab
 * starts fresh from whatever prefix is now under the caret.
 *
 * <p>Pure and stateful-by-design (the cycle IS state), no Swing — the
 * window feeds it the input field's text/caret and the channel's nick
 * list, and applies the returned replacement. Matching is
 * case-insensitive prefix match; candidates keep the nick list's order
 * so cycling is stable.
 */
public final class NickCompleter {

    /** One completion: the full replacement text and where the caret lands. */
    public record Result(String text, int caret) {
    }

    private List<String> candidates = List.of();
    private int index = -1;
    private int wordStart = -1;
    private String lastText;
    private int lastCaret = -1;

    /**
     * Completes (or, on a repeated Tab, cycles) the word ending at
     * {@code caret}. Returns {@code null} when nothing matches — the
     * caller leaves the field alone.
     */
    public Result complete(String text, int caret, List<String> nicks) {
        if (text == null || caret < 0 || caret > text.length()) {
            return null;
        }
        boolean cycling = candidates.size() > 0
                && text.equals(lastText) && caret == lastCaret;
        if (!cycling) {
            int start = caret;
            while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
                start--;
            }
            String prefix = text.substring(start, caret).toLowerCase(Locale.ROOT);
            if (prefix.isEmpty()) {
                reset();
                return null;
            }
            List<String> found = new ArrayList<>();
            for (String nick : nicks) {
                if (nick.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    found.add(nick);
                }
            }
            if (found.isEmpty()) {
                reset();
                return null;
            }
            candidates = found;
            index = 0;
            wordStart = start;
        } else {
            index = (index + 1) % candidates.size();
        }
        String nick = candidates.get(index);
        String suffix = wordStart == 0 ? ": " : " ";
        String before = text.substring(0, wordStart);
        // on a cycle, the text still holds the PREVIOUS completion; the
        // tail after the caret is whatever the user had after the word
        String after = text.substring(caret);
        String replaced = before + nick + suffix + after;
        int newCaret = before.length() + nick.length() + suffix.length();
        lastText = replaced;
        lastCaret = newCaret;
        return new Result(replaced, newCaret);
    }

    /** Forgets the cycle; called on any non-Tab keystroke. */
    public void reset() {
        candidates = List.of();
        index = -1;
        wordStart = -1;
        lastText = null;
        lastCaret = -1;
    }
}
