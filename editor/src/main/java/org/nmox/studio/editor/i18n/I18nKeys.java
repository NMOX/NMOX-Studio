package org.nmox.studio.editor.i18n;

import java.util.Set;

/**
 * Where a translation KEY sits in source text (v2.177.0) — the pure
 * predicates behind key completion and the ⌘-click jump, mirroring
 * {@code CssClasses.jsClassPrefix}: the caret is inside a string that
 * is the first argument of a lookup call ({@code t(}, {@code $t(},
 * {@code i18n.t(}, {@code $tc(}, {@code $_(}, {@code _(},
 * {@code $format(}), the value of a key attribute ({@code data-i18n=},
 * {@code i18nKey=}, {@code keypath=}, {@code v-t=}), the {@code id=} of
 * a {@code <FormattedMessage}, the {@code id:} field of a
 * {@code formatMessage({…})} / {@code defineMessages({…})} object — or
 * after Paraglide's {@code m.}, where the key is an identifier and
 * there is no quote at all. An arbitrary string never qualifies:
 * {@code fetch('/api')} stays a URL, a template with {@code ${} is
 * dynamic and refused, and a bare {@code t(} with no quote yet offers
 * nothing.
 *
 * <p>A key is code syntax: the catalog dialect decides what it may
 * contain (letters, digits, {@code _ . - :}), and the reader's script
 * does not change it — the word-boundary ledger classifies this file so.
 */
public final class I18nKeys {

    private I18nKeys() {
    }

    /** The call names whose first string argument is a key. */
    private static final Set<String> CALLS =
            Set.of("t", "tc", "$t", "$tc", "$_", "_", "$format");

    /** The attributes whose value is a key. */
    private static final Set<String> ATTRIBUTES =
            Set.of("data-i18n", "i18nKey", "keypath");

    /**
     * The partial key typed so far when the caret sits in a key position
     * (empty right after the opening quote or after {@code m.}); null
     * anywhere else.
     */
    public static String keyPrefix(String beforeCaret) {
        int nameStart = beforeCaret.length();
        while (nameStart > 0 && isKeyChar(beforeCaret.charAt(nameStart - 1))) {
            nameStart--;
        }
        String partial = beforeCaret.substring(nameStart);
        char quote = nameStart == 0 ? ' ' : beforeCaret.charAt(nameStart - 1);
        if (quote == '\'' || quote == '"' || quote == '`') {
            String head = beforeCaret.substring(0, nameStart - 1).stripTrailing();
            return opensKey(head, quote) ? partial : null;
        }
        // Paraglide: m.<identifier>, no quote — the whole token was scanned
        // (the dot is a key char), so the sigil is inside the partial
        if (partial.startsWith("m.") && partial.indexOf('.', 2) < 0 && partial.indexOf(':') < 0
                && partial.indexOf('-') < 0) {
            return partial.substring(2);
        }
        return null;
    }

    /**
     * True when the text before an opening quote is a key position: a
     * lookup call's open paren, a key attribute's {@code =}, the
     * {@code v-t="'} pair, a FormattedMessage's {@code id=}, or an
     * {@code id:} inside an unclosed message-descriptor call.
     */
    static boolean opensKey(String head, char quote) {
        if (head.endsWith("(")) {
            String chain = head.substring(0, head.length() - 1).stripTrailing();
            int start = chain.length();
            while (start > 0 && (isIdentChar(chain.charAt(start - 1)) || chain.charAt(start - 1) == '.')) {
                start--;
            }
            String call = chain.substring(start);
            String last = call.substring(call.lastIndexOf('.') + 1);
            return CALLS.contains(last) && (start == 0 || !isIdentChar(chain.charAt(start - 1)));
        }
        if (head.endsWith("=")) {
            String name = head.substring(0, head.length() - 1).stripTrailing();
            for (String attr : ATTRIBUTES) {
                if (name.endsWith(attr)) {
                    int at = name.length() - attr.length();
                    return at == 0 || !isIdentChar(name.charAt(at - 1));
                }
            }
            if (name.endsWith("id")) {
                int lt = head.lastIndexOf('<');
                return lt >= 0 && head.startsWith("<FormattedMessage", lt) && head.indexOf('>', lt) < 0
                        && (name.length() == 2 || !isIdentChar(name.charAt(name.length() - 3)));
            }
            return false;
        }
        if (quote == '\'' && head.endsWith("v-t=\"")) {
            return true;
        }
        if (head.endsWith(":")) {
            String name = head.substring(0, head.length() - 1).stripTrailing();
            if (!name.endsWith("id") || (name.length() > 2 && isIdentChar(name.charAt(name.length() - 3)))) {
                return false;
            }
            return insideMessageDescriptor(name);
        }
        return false;
    }

    /** True when the nearest formatMessage/defineMessage(s) call is still open. */
    private static boolean insideMessageDescriptor(String head) {
        int at = Math.max(head.lastIndexOf("formatMessage("),
                Math.max(head.lastIndexOf("defineMessages("), head.lastIndexOf("defineMessage(")));
        if (at < 0) {
            return false;
        }
        int depth = 0;
        for (int i = head.indexOf('(', at); i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return depth > 0;
    }

    /**
     * The key span under {@code offset} when it sits in a key position
     * and is complete (the closing quote follows it, or a paren follows a
     * Paraglide identifier) — the ⌘-click subject. Returns {start, end}
     * or null.
     */
    public static int[] keySpanAt(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && isKeyChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isKeyChar(text.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        String prefix = keyPrefix(text.substring(0, end));
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        String span = text.substring(start, end);
        if (span.equals("m." + prefix)) {
            return new int[] {start + 2, end};
        }
        if (!span.equals(prefix) || start == 0) {
            return null;
        }
        char quote = text.charAt(start - 1);
        return end < text.length() && text.charAt(end) == quote ? new int[] {start, end} : null;
    }

    /** A namespace-prefixed key split as {namespace or null, key}. */
    public static String[] split(String key) {
        int colon = key.indexOf(':');
        if (colon > 0 && colon < key.length() - 1) {
            return new String[] {key.substring(0, colon), key.substring(colon + 1)};
        }
        return new String[] {null, key};
    }

    static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == ':';
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
