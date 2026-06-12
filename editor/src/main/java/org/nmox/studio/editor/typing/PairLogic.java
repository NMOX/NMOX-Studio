package org.nmox.studio.editor.typing;

/**
 * The pure decisions behind bracket/quote intelligence, separated from
 * the editor SPI so they can be unit tested: given what was typed and
 * what surrounds the caret, decide whether to auto-close, type over,
 * or do nothing.
 */
public final class PairLogic {

    private PairLogic() {
    }

    public static boolean isOpener(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    public static boolean isCloser(char c) {
        return c == ')' || c == ']' || c == '}';
    }

    public static boolean isQuote(char c) {
        return c == '\'' || c == '"' || c == '`';
    }

    public static char closerFor(char opener) {
        return switch (opener) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            default -> opener; // quotes close themselves
        };
    }

    /**
     * Auto-close after an opener unless the next character is an
     * identifier/quote start (typing in front of existing code).
     */
    public static boolean shouldAutoClose(char typed, char next) {
        if (!isOpener(typed) && !isQuote(typed)) {
            return false;
        }
        return next == 0 || !(Character.isLetterOrDigit(next) || isQuote(next));
    }

    /**
     * Typing a closer/quote directly before the identical character:
     * step over it instead of inserting a duplicate.
     */
    public static boolean shouldTypeOver(char typed, char next) {
        return (isCloser(typed) || isQuote(typed)) && typed == next;
    }

    /**
     * Backspacing an opener with its closer immediately after: remove
     * both ("()" + backspace leaves nothing, not a dangling ")").
     */
    public static boolean isEmptyPair(char removed, char next) {
        if (isOpener(removed)) {
            return closerFor(removed) == next;
        }
        return isQuote(removed) && removed == next;
    }
}
