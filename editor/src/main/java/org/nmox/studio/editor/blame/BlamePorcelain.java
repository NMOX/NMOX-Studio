package org.nmox.studio.editor.blame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code git blame --porcelain} read into one answer per line of the file
 * (3.2.0). Pure: the process that produced the text is somebody else's.
 *
 * <p>The format is a record per line: a header {@code <sha> <orig> <final>
 * [<count>]}, then {@code key value} lines, then the line's content after a
 * TAB. The {@code key value} block ({@code author}, {@code author-time},
 * {@code summary}, …) is written only the FIRST time a commit appears; every
 * later line of the same commit carries the bare header and its content.
 * So the facts are kept per commit, and each line points at its commit.
 *
 * <p>A line git has not committed yet carries the all-zero sha and the author
 * {@code Not Committed Yet}; {@link Line#uncommitted()} says so, and the
 * status line says it in the reader's language rather than repeating git's
 * English.
 *
 * <p>Everything here is repository data a stranger may have written: an
 * author called {@code <html><img src=…>} is kept exactly as written, and
 * the painter renders it as text.
 */
public final class BlamePorcelain {

    /** Past this many lines a header is ignored: a line number is not a size. */
    static final int MAX_LINE = 5_000_000;

    private BlamePorcelain() {
    }

    /**
     * What one commit says about a line. One instance per commit, shared by
     * every line it wrote.
     *
     * @param sha        the full commit id, or all zeros for an uncommitted line
     * @param author     the author's name as the commit records it
     * @param authorTime the author time in epoch SECONDS (git's own unit); 0 if absent
     * @param summary    the commit's first line; empty if absent
     */
    public record Line(String sha, String author, long authorTime, String summary) {

        /** True for git's all-zero id: the working tree's own change. */
        public boolean uncommitted() {
            if (sha.isEmpty()) {
                return false;
            }
            for (int i = 0; i < sha.length(); i++) {
                if (sha.charAt(i) != '0') {
                    return false;
                }
            }
            return true;
        }

        /** The first eight characters, the way people quote a commit. */
        public String shortSha() {
            return sha.length() <= 8 ? sha : sha.substring(0, 8);
        }
    }

    /** The whole file's answer; {@link #at(int)} reads one line. */
    public static final class Blame {

        private final List<Line> lines;

        Blame(List<Line> lines) {
            this.lines = Collections.unmodifiableList(lines);
        }

        /** The 1-based line's commit, or null for a line the output did not cover. */
        public Line at(int line) {
            return line >= 1 && line <= lines.size() ? lines.get(line - 1) : null;
        }

        /** How many lines the output covered (the highest final line seen). */
        public int size() {
            return lines.size();
        }
    }

    /** Mutable facts while a commit's key/value block is read. */
    private static final class Facts {
        String author = "";
        long time;
        String summary = "";
        Line built;

        Line line(String sha) {
            if (built == null) {
                built = new Line(sha, author, time, summary);
            }
            return built;
        }
    }

    /**
     * Reads porcelain output. Tolerant by construction: a header that is not
     * one (a non-hex sha, a non-number) is skipped, an unknown key is
     * ignored, and a truncated tail simply covers fewer lines. Null or empty
     * input is an empty answer.
     */
    public static Blame parse(String porcelain) {
        List<Line> lines = new ArrayList<>();
        if (porcelain == null || porcelain.isEmpty()) {
            return new Blame(lines);
        }
        Map<String, Facts> commits = new HashMap<>();
        Facts current = null;
        String currentSha = null;
        int currentLine = 0;
        int start = 0;
        int len = porcelain.length();
        while (start < len) {
            int nl = porcelain.indexOf('\n', start);
            int end = nl < 0 ? len : nl;
            String row = porcelain.substring(start, end);
            start = end + 1;
            if (row.startsWith("\t")) {
                // the line's content closes its record: the commit's facts are complete now
                if (current != null && currentLine >= 1) {
                    while (lines.size() < currentLine) {
                        lines.add(null);
                    }
                    lines.set(currentLine - 1, current.line(currentSha));
                }
                current = null;
                currentSha = null;
                currentLine = 0;
                continue;
            }
            if (row.endsWith("\r")) {
                row = row.substring(0, row.length() - 1); // a CRLF pipe on Windows
            }
            if (current == null) {
                String[] header = row.split(" ");
                if (header.length < 3 || !isSha(header[0])) {
                    continue;
                }
                int finalLine = number(header[2]);
                if (finalLine < 1 || finalLine > MAX_LINE) {
                    continue;
                }
                currentSha = header[0];
                currentLine = finalLine;
                // a sha seen before brings its facts along: the shorthand record
                current = commits.computeIfAbsent(currentSha, k -> new Facts());
                continue;
            }
            // a key/value line of the record being read
            if (current.built != null) {
                continue; // the facts were frozen by an earlier line of this commit
            }
            if (row.startsWith("author ")) {
                current.author = row.substring("author ".length());
            } else if (row.startsWith("author-time ")) {
                current.time = Math.max(0, epochSeconds(row.substring("author-time ".length()).trim()));
            } else if (row.startsWith("summary ")) {
                current.summary = row.substring("summary ".length());
            }
        }
        return new Blame(lines);
    }

    /** Epoch seconds, or -1: a long so the year 2038 is not a parse failure. */
    private static long epochSeconds(String s) {
        if (s.isEmpty() || s.length() > 15) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v;
    }

    private static boolean isSha(String s) {
        if (s.length() != 40 && s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /** A non-negative decimal, or -1; never throws on hostile input. */
    private static int number(String s) {
        if (s.isEmpty() || s.length() > 10) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v > Integer.MAX_VALUE ? -1 : (int) v;
    }
}
