package org.nmox.studio.ui.browser.devtools;

import java.util.Locale;

/**
 * Style write-back (v1.358.0, the elevation arc's tranche 2): a
 * declaration edited in the DevTools DOM tab lands in the SOURCE
 * stylesheet, so a tweak made where you can SEE it is never re-typed
 * by hand into the file.
 *
 * <p>The rule is found by its CSSOM selector text — the page itself
 * says which rules matched the element ({@code el.matches}), so this
 * class never guesses specificity; it only has to find that exact
 * rule in the file. Selector comparison is whitespace-normalized
 * (CSSOM normalizes; hand-written sources vary), comments are
 * neutralized first (a selector inside {@code /* … *}{@code /} is not
 * a rule), and an existing declaration is REPLACED in place while a
 * missing one is inserted before the closing brace with the block's
 * own indentation.
 *
 * <p>Refusals return null with the reason in {@link Result#reason}:
 * a selector the file doesn't contain means the cascade got it from
 * somewhere else, and writing anywhere else would be a guess.
 */
public final class StyleWriteback {

    private StyleWriteback() {
    }

    /** The rewritten stylesheet, or a refusal with its reason. */
    public record Result(String css, String reason) {

        public boolean ok() {
            return css != null;
        }

        static Result refused(String reason) {
            return new Result(null, reason);
        }
    }

    /**
     * Sets {@code property: value} inside the rule whose selector
     * matches {@code selectorText} (CSSOM-normalized comparison).
     */
    public static Result apply(String css, String selectorText, String property, String value) {
        if (css == null || css.isEmpty()) {
            return Result.refused("stylesheet is empty");
        }
        if (selectorText == null || selectorText.isBlank()
                || property == null || property.isBlank()
                || value == null || value.isBlank()) {
            return Result.refused("selector, property and value are all required");
        }
        // values ending in a brace or containing one would corrupt the
        // block structure — refuse rather than write a broken file
        if (value.indexOf('{') >= 0 || value.indexOf('}') >= 0
                || property.indexOf('{') >= 0 || property.indexOf('}') >= 0
                || property.indexOf(':') >= 0 || property.indexOf(';') >= 0
                || value.indexOf(';') >= 0) {
            return Result.refused("property/value must not contain { } : ;");
        }
        String neutral = neutralizeComments(css);
        int[] block = findRuleBlock(neutral, selectorText);
        if (block == null) {
            return Result.refused("selector \"" + selectorText + "\" not found in this file");
        }
        int open = block[0];
        int close = block[1];
        String body = css.substring(open + 1, close);
        String neutralBody = neutral.substring(open + 1, close);
        int[] decl = findDeclaration(neutralBody, property);
        if (decl != null) {
            String replaced = body.substring(0, decl[0])
                    + property + ": " + value
                    + body.substring(decl[1]);
            return new Result(css.substring(0, open + 1) + replaced + css.substring(close), null);
        }
        String indent = blockIndent(body);
        String needsSemi = body.stripTrailing().endsWith(";") || body.isBlank() ? "" : ";";
        String beforeClose = body.stripTrailing();
        String closeIndent = closingIndent(css, open);
        String inserted = beforeClose + needsSemi + "\n" + indent + property + ": " + value + ";\n" + closeIndent;
        return new Result(css.substring(0, open + 1) + inserted + css.substring(close), null);
    }

    /**
     * Locates the {@code {…}} block whose selector normalizes to
     * {@code selectorText}; returns {open brace offset, close brace
     * offset} in the ORIGINAL text, or null. Nested blocks (media
     * queries) are handled by tracking depth: selectors are read at
     * any depth, and the matching close brace is found by counting.
     *
     * <p>When the SAME selector appears twice in one file the LAST
     * block wins — that is the rule the cascade actually applies, so
     * an edit to the first one would land in dead CSS and the page
     * would never change (the v1.359.0 review's find).
     */
    static int[] findRuleBlock(String neutral, String selectorText) {
        String wanted = normalizeSelector(selectorText);
        int[] last = null;
        int segStart = 0;
        for (int i = 0; i < neutral.length(); i++) {
            char c = neutral.charAt(i);
            if (c == '{') {
                String sel = normalizeSelector(neutral.substring(segStart, i));
                if (sel.equals(wanted)) {
                    int close = matchingClose(neutral, i);
                    if (close > i) {
                        last = new int[]{i, close};
                    }
                }
                segStart = i + 1;
            } else if (c == '}' || c == ';') {
                segStart = i + 1;
            }
        }
        return last;
    }

    private static int matchingClose(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Collapses whitespace runs and lowercases — the CSSOM comparison. */
    static String normalizeSelector(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * {start, end} of the {@code prop: value} declaration (without its
     * semicolon) inside the block body, or null. Property match is a
     * whole word at a declaration boundary — {@code color} must not
     * match {@code background-color}.
     */
    static int[] findDeclaration(String neutralBody, String property) {
        String hay = neutralBody.toLowerCase(Locale.ROOT);
        String needle = property.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = hay.indexOf(needle, from);
            if (at < 0) {
                return null;
            }
            int before = at - 1;
            boolean startOk = before < 0 || hay.charAt(before) == ';' || hay.charAt(before) == '\n'
                    || Character.isWhitespace(hay.charAt(before));
            // walk back further: only whitespace between a ; } { or start
            int b = at - 1;
            while (b >= 0 && Character.isWhitespace(hay.charAt(b))) {
                b--;
            }
            boolean boundary = b < 0 || hay.charAt(b) == ';' || hay.charAt(b) == '{' || hay.charAt(b) == '}';
            int colon = at + needle.length();
            while (colon < hay.length() && Character.isWhitespace(hay.charAt(colon))) {
                colon++;
            }
            if (startOk && boundary && colon < hay.length() && hay.charAt(colon) == ':') {
                int end = hay.indexOf(';', colon);
                if (end < 0) {
                    end = hay.length();
                }
                return new int[]{at, end};
            }
            from = at + needle.length();
        }
    }

    /** The indentation of the block's first declaration line, or four spaces. */
    static String blockIndent(String body) {
        for (String line : body.split("\n", -1)) {
            if (!line.isBlank()) {
                int i = 0;
                while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                    i++;
                }
                if (i > 0) {
                    return line.substring(0, i);
                }
            }
        }
        return "    ";
    }

    /** The indentation of the line holding the OPEN brace (for the close). */
    private static String closingIndent(String css, int openBrace) {
        int lineStart = css.lastIndexOf('\n', openBrace) + 1;
        int i = lineStart;
        StringBuilder sb = new StringBuilder();
        while (i < css.length() && (css.charAt(i) == ' ' || css.charAt(i) == '\t')) {
            sb.append(css.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** CSS comments blanked character-for-character, newlines kept. */
    static String neutralizeComments(String css) {
        StringBuilder out = new StringBuilder(css);
        int from = 0;
        while (true) {
            int start = out.indexOf("/*", from);
            if (start < 0) {
                return out.toString();
            }
            int end = out.indexOf("*/", start + 2);
            int stop = end < 0 ? out.length() : end + 2;
            for (int i = start; i < stop; i++) {
                char c = out.charAt(i);
                if (c != '\n' && c != '\r') {
                    out.setCharAt(i, ' ');
                }
            }
            from = stop;
        }
    }
}
