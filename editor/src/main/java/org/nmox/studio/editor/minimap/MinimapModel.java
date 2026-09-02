package org.nmox.studio.editor.minimap;

/**
 * The pure geometry of a minimap: a document's line shapes (indent and
 * length per line) laid onto a strip of pixels, and the two mappings the
 * widget needs — a line's y and the line under a y.
 *
 * <p>Everything here is a total function of ints so every rule is a unit
 * test: rows are {@value #PREFERRED_ROW} px tall until the document is
 * taller than the strip, then the whole document is scaled to fit (never
 * scrolled — a minimap that scrolls is a second scrollbar); a line's bar
 * runs from its indent to its length, capped at {@value #MAX_COLS} columns
 * so one long line cannot flatten the silhouette; and only the first
 * {@value #MAX_LINES} lines are shaped (the bounded-read law — a 500k-line
 * generated file gets a truncated silhouette, not a frozen paint thread).
 */
public final class MinimapModel {

    /** Preferred pixel height of one document line. */
    public static final double PREFERRED_ROW = 2.0;
    /** Columns past which a line's bar stops growing. */
    public static final int MAX_COLS = 120;
    /** Lines past which the silhouette is truncated, never scanned. */
    public static final int MAX_LINES = 40_000;

    /**
     * Per-line silhouette: {@code indents[i]} is the column of the first
     * non-blank character of line {@code i} (the line's length when blank),
     * {@code lengths[i]} its length in characters; {@code truncated} says
     * the document had more lines than {@link #MAX_LINES}.
     */
    public record Shapes(int[] indents, int[] lengths, boolean truncated) {
        public int lines() {
            return lengths.length;
        }
    }

    private MinimapModel() {
    }

    /** Shapes every line of {@code text} (\n-separated) up to the cap. */
    public static Shapes shape(CharSequence text) {
        int n = text.length();
        int count = 0;
        for (int i = 0; i < n && count < MAX_LINES; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        boolean truncated = count == MAX_LINES && hasMoreLinesAfter(text, count);
        int lines = truncated ? MAX_LINES : count + (n == 0 || text.charAt(n - 1) == '\n' ? 0 : 1);
        if (!truncated && lines == 0) {
            lines = 1; // an empty document is one empty line
        }
        int[] indents = new int[lines];
        int[] lengths = new int[lines];
        int line = 0;
        int start = 0;
        for (int i = 0; i <= n && line < lines; i++) {
            if (i == n || text.charAt(i) == '\n') {
                int len = i - start;
                int indent = 0;
                while (indent < len && Character.isWhitespace(text.charAt(start + indent))) {
                    indent++;
                }
                indents[line] = indent;
                lengths[line] = len;
                line++;
                start = i + 1;
            }
        }
        return new Shapes(indents, lengths, truncated);
    }

    private static boolean hasMoreLinesAfter(CharSequence text, int newlines) {
        int seen = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++seen == newlines) {
                return i + 1 < text.length();
            }
        }
        return false;
    }

    /**
     * Pixel height of one line: the preferred row while the document fits,
     * else the strip height spread over every line (a document twice the
     * strip is drawn at half a pixel per line — rows overlap, density shows).
     */
    public static double rowHeight(int lines, int stripHeight) {
        if (lines <= 0 || stripHeight <= 0) {
            return PREFERRED_ROW;
        }
        return Math.min(PREFERRED_ROW, (double) stripHeight / lines);
    }

    /** Top pixel of {@code line}. */
    public static int yOf(int line, double rowHeight) {
        return (int) Math.floor(Math.max(0, line) * rowHeight);
    }

    /** The document line under pixel {@code y}, clamped into the document. */
    public static int lineAt(int y, double rowHeight, int lines) {
        if (lines <= 0) {
            return 0;
        }
        int line = (int) Math.floor(Math.max(0, y) / rowHeight);
        return Math.min(line, lines - 1);
    }

    /**
     * Horizontal span of a line's bar in a strip {@code stripWidth} px
     * wide: {@code {x, width}} in pixels, the column axis scaled so
     * {@link #MAX_COLS} columns fill the strip; a blank line is empty.
     */
    public static int[] bar(int indent, int length, int stripWidth) {
        int len = Math.min(Math.max(0, length), MAX_COLS);
        int ind = Math.min(Math.max(0, indent), len);
        if (len - ind <= 0) {
            return new int[] {0, 0};
        }
        int x = ind * stripWidth / MAX_COLS;
        int right = len * stripWidth / MAX_COLS;
        return new int[] {x, Math.max(1, right - x)};
    }
}
