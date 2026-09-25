package org.nmox.studio.editor.conflicts;

import java.util.ArrayList;
import java.util.List;

/**
 * The conflict blocks git writes into a file it could not merge, and what
 * each of VS Code's three choices puts in a block's place (3.2.0). Pure:
 * text in, offsets and replacement text out, so every rule is a unit test.
 *
 * <p><b>What a block is.</b> Git's own shape, one marker per line, each
 * marker at the START of its line and exactly seven characters long,
 * followed by a space and a label or by the end of the line:
 * <pre>
 * &lt;&lt;&lt;&lt;&lt;&lt;&lt; HEAD            (ours — "the current change")
 * ...
 * ||||||| merged common ancestors   (optional: merge.conflictStyle diff3/zdiff3)
 * ...
 * =======
 * ...                               (theirs — "the incoming change")
 * &gt;&gt;&gt;&gt;&gt;&gt;&gt; feature
 * </pre>
 * Outside a block the markers mean nothing: a Markdown heading underlined
 * with {@code =======} is prose, and a {@code "<<<<<<< x"} inside a string
 * is not at a line start.
 *
 * <p><b>Refuse, never guess.</b> A block that is not exactly that shape —
 * a second separator, a second base, a trailer before the separator, a
 * block nested in another (git writes those for a recursive merge's
 * conflicted ancestor), or one the file ends inside — is skipped whole.
 * Offering "Accept Current Change" on a shape we had to interpret could
 * delete the half the user meant to keep; leaving it alone costs them
 * only the typing they would have done anyway.
 */
public final class MergeConflicts {

    private MergeConflicts() {
    }

    /** The marker length git writes (its {@code conflict-marker-size} default). */
    static final int MARKER = 7;

    /** VS Code's three choices, in its order. */
    public enum Resolution {
        /** Keep ours: what the checked-out branch had. */
        CURRENT,
        /** Keep theirs: what the merged or rebased-in commit brought. */
        INCOMING,
        /** Keep both, ours first. */
        BOTH
    }

    /**
     * One well-formed block. Every offset is into the scanned text; a
     * section's end is the start of the marker line that closes it, so a
     * section is whole lines, terminators included.
     *
     * @param start       the {@code <<<<<<<} line's first character
     * @param headerEnd   the end of that line's text, before its terminator
     * @param oursStart   the first character after the header line
     * @param oursEnd     the start of the base or separator line
     * @param baseStart   after the {@code |||||||} line, or -1 without one
     * @param baseEnd     the start of the separator line, or -1
     * @param theirsStart the first character after the {@code =======} line
     * @param theirsEnd   the start of the {@code >>>>>>>} line
     * @param end         after the trailer line's terminator (or the text's end)
     * @param endsAtEof   the trailer line had no terminator: the file ends there
     * @param oursLabel   the header's label ({@code HEAD}), possibly empty
     * @param theirsLabel the trailer's label, possibly empty
     */
    public record Block(int start, int headerEnd, int oursStart, int oursEnd,
            int baseStart, int baseEnd, int theirsStart, int theirsEnd,
            int end, boolean endsAtEof, String oursLabel, String theirsLabel) {

        /** Whether git wrote the common ancestor too (diff3/zdiff3). */
        public boolean hasBase() {
            return baseStart >= 0;
        }
    }

    /** Where a line is: its first character, the end of its text, and past its terminator. */
    private record Line(int start, int textEnd, int next) {
    }

    /**
     * Every well-formed block in {@code text}, in order. Cheap when there
     * is none: a text without {@code <<<<<<<} is answered without walking
     * its lines.
     */
    public static List<Block> scan(String text) {
        List<Block> out = new ArrayList<>();
        if (text == null || text.indexOf("<<<<<<<") < 0) {
            return out;
        }
        // the block under construction; depth > 0 means a marker opened
        // inside it, and the whole outer block is refused when it closes
        int start = -1;
        int headerEnd = -1;
        int oursStart = -1;
        int oursEnd = -1;
        int baseStart = -1;
        int baseEnd = -1;
        int theirsStart = -1;
        String oursLabel = "";
        boolean malformed = false;
        int depth = 0;
        State state = State.OUTSIDE;
        int pos = 0;
        int n = text.length();
        while (pos < n) {
            Line line = lineAt(text, pos);
            pos = line.next();
            char m = markerOf(text, line);
            if (m == 0) {
                continue;
            }
            if (state == State.OUTSIDE) {
                if (m == '<') {
                    state = State.OURS;
                    start = line.start();
                    headerEnd = line.textEnd();
                    oursStart = line.next();
                    oursEnd = baseStart = baseEnd = theirsStart = -1;
                    oursLabel = label(text, line);
                    malformed = false;
                    depth = 0;
                }
                // a lone separator, base or trailer outside a block is
                // prose (a setext heading) or somebody else's business
                continue;
            }
            if (m == '<') {
                depth++;
                malformed = true;
                continue;
            }
            if (depth > 0) {
                if (m == '>') {
                    depth--;
                }
                continue;
            }
            switch (m) {
                case '|' -> {
                    if (state == State.OURS) {
                        oursEnd = line.start();
                        baseStart = line.next();
                        state = State.BASE;
                    } else {
                        malformed = true;
                    }
                }
                case '=' -> {
                    if (state == State.OURS) {
                        oursEnd = line.start();
                        theirsStart = line.next();
                        state = State.THEIRS;
                    } else if (state == State.BASE) {
                        baseEnd = line.start();
                        theirsStart = line.next();
                        state = State.THEIRS;
                    } else {
                        malformed = true;
                    }
                }
                default -> { // '>'
                    if (state == State.THEIRS && !malformed) {
                        boolean eof = line.next() == line.textEnd();
                        out.add(new Block(start, headerEnd, oursStart, oursEnd,
                                baseStart, baseEnd, theirsStart, line.start(),
                                line.next(), eof, oursLabel, label(text, line)));
                    }
                    // a trailer before the separator, or a malformed
                    // block, ends here without an offer
                    state = State.OUTSIDE;
                }
            }
        }
        // a block the text ends inside was never closed: nothing to offer
        return out;
    }

    private enum State {
        OUTSIDE, OURS, BASE, THEIRS
    }

    private static Line lineAt(String text, int from) {
        int nl = text.indexOf('\n', from);
        if (nl < 0) {
            int end = text.length();
            int textEnd = end > from && text.charAt(end - 1) == '\r' ? end - 1 : end;
            return new Line(from, textEnd, end);
        }
        int textEnd = nl > from && text.charAt(nl - 1) == '\r' ? nl - 1 : nl;
        return new Line(from, textEnd, nl + 1);
    }

    /** The marker character a line opens with, or 0 when it is not a marker line. */
    private static char markerOf(String text, Line line) {
        int len = line.textEnd() - line.start();
        if (len < MARKER) {
            return 0;
        }
        char c = text.charAt(line.start());
        if (c != '<' && c != '|' && c != '=' && c != '>') {
            return 0;
        }
        for (int i = 1; i < MARKER; i++) {
            if (text.charAt(line.start() + i) != c) {
                return 0;
            }
        }
        // exactly seven: an eighth marker character, or any other glued
        // character, makes it some other line (a longer setext rule, a
        // shift operator, a diff of a conflicted file)
        return len == MARKER || text.charAt(line.start() + MARKER) == ' ' ? c : 0;
    }

    private static String label(String text, Line line) {
        return line.textEnd() - line.start() > MARKER
                ? text.substring(line.start() + MARKER + 1, line.textEnd()).strip()
                : "";
    }

    /**
     * What replaces {@code [block.start(), block.end())} for a choice. A
     * block that ends the file without a final newline keeps the file
     * that way: the kept text loses its one trailing terminator.
     */
    public static String replacement(String text, Block block, Resolution choice) {
        String ours = text.substring(block.oursStart(), block.oursEnd());
        String theirs = text.substring(block.theirsStart(), block.theirsEnd());
        String kept = switch (choice) {
            case CURRENT -> ours;
            case INCOMING -> theirs;
            case BOTH -> ours + theirs;
        };
        if (block.endsAtEof()) {
            if (kept.endsWith("\r\n")) {
                kept = kept.substring(0, kept.length() - 2);
            } else if (kept.endsWith("\n")) {
                kept = kept.substring(0, kept.length() - 1);
            }
        }
        return kept;
    }

    /** The whole text with one block resolved; the unit tests' view of a fix. */
    public static String apply(String text, Block block, Resolution choice) {
        return text.substring(0, block.start()) + replacement(text, block, choice)
                + text.substring(block.end());
    }
}
