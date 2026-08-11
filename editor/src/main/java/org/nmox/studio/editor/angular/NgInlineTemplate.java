package org.nmox.studio.editor.angular;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the inline templates live (the Angular-top arc, 2026-08-11): a
 * component may carry its markup as {@code template: `...`} instead of
 * a templateUrl, and WebStorm expands Emmet there — ours refused
 * because the pane's mime is TypeScript. This parser answers the one
 * question the Emmet action needs: is the caret inside an inline
 * template's backtick literal, and if so what are the content bounds?
 *
 * <p>Deliberately narrow, recorded limits: BACKTICK literals only (what
 * {@code ng generate --inline-template} emits and what any multi-line
 * template uses — a single-quoted one-liner is usually already-written
 * markup, not an abbreviation site), the {@code template} key only
 * ({@code styles} arrays stay CSS and are not markup), and only in
 * files that carry a {@code @Component} decorator at all. Interpolated
 * {@code ${}} inside the literal doesn't matter here — the caller only
 * needs bounds, and a template literal's backtick still ends it.
 */
public final class NgInlineTemplate {

    private static final int MAX_SCAN_CHARS = 500_000;

    /** {@code template:} followed by an opening backtick. */
    private static final Pattern TEMPLATE_OPEN =
            Pattern.compile("\\btemplate\\s*:\\s*`");

    private NgInlineTemplate() {
    }

    /**
     * The [contentStart, contentEnd] bounds of the inline-template
     * literal containing {@code caret} (caret may sit at either edge —
     * just after the opening backtick or just before the closing one),
     * or null when the caret is anywhere else.
     */
    public static int[] spanAt(String text, int caret) {
        if (text == null || text.length() > MAX_SCAN_CHARS
                || !text.contains("@Component")) {
            return null;
        }
        Matcher m = TEMPLATE_OPEN.matcher(text);
        while (m.find()) {
            int open = m.end();          // first content char
            int close = closingBacktick(text, open);
            if (close < 0) {
                return null;             // unterminated: refuse, never guess
            }
            if (caret >= open && caret <= close) {
                return new int[]{open, close};
            }
            m.region(close + 1, text.length());
        }
        return null;
    }

    /** Index of the next unescaped backtick at or after {@code from}, or -1. */
    private static int closingBacktick(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;                     // skip the escaped char
            } else if (c == '`') {
                return i;
            }
        }
        return -1;
    }
}
