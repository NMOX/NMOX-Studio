package org.nmox.studio.editor.editing;

/**
 * Expand Line Selection, as a rule over line boundaries: the selection
 * grows to whole lines, the last one's terminator included, and pressed
 * again it takes the next line.
 *
 * <p>The second press needs no memory. After the first, the selection ends
 * at the start of the following line; "the line that holds the end" is then
 * that following line, so the same rule extends by exactly one. On the last
 * line there is no terminator to take and the selection stops at the end of
 * the text, where a further press changes nothing.
 *
 * <p>Pure: the caller says where lines are ({@link Lines}), so an editor
 * hands over its own line table and never copies the document.
 */
public final class LineSelection {

    /** Where the lines of a text are. */
    public interface Lines {

        /** The length of the text. */
        int length();

        /** The offset at which the line holding {@code offset} starts. */
        int lineStart(int offset);

        /**
         * The offset just past the terminator of the line holding
         * {@code offset}. A Swing document answers one past the end of the
         * text for its last line (it models a final newline that is not
         * there); {@link LineSelection#expand} never selects past the text.
         */
        int lineEnd(int offset);
    }

    /** A selection: {@code start} is where it was anchored, {@code end} is where the caret sits. */
    public record Range(int start, int end) {
    }

    private LineSelection() {
    }

    /** The selection after one press, given the selection before it (an empty one is the caret). */
    public static Range expand(Lines lines, int selStart, int selEnd) {
        int length = lines.length();
        int s = Math.clamp(Math.min(selStart, selEnd), 0, length);
        int e = Math.clamp(Math.max(selStart, selEnd), 0, length);
        int start = lines.lineStart(s);
        int end = Math.min(lines.lineEnd(e), length);
        return new Range(start, end);
    }

    /** The lines of a plain text, {@code \n} and {@code \r\n} alike; for tests and small texts. */
    public static Lines of(CharSequence text) {
        return new Lines() {
            @Override
            public int length() {
                return text.length();
            }

            @Override
            public int lineStart(int offset) {
                int i = offset;
                while (i > 0 && text.charAt(i - 1) != '\n') {
                    i--;
                }
                return i;
            }

            @Override
            public int lineEnd(int offset) {
                int i = offset;
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                return i < text.length() ? i + 1 : i;
            }
        };
    }
}
