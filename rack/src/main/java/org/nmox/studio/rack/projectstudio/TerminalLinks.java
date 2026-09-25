package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The places a terminal line points at (3.2): {@code src/app.ts:42:7} from
 * tsc, {@code /abs/x.js:10:5} inside a node or jest stack frame,
 * {@code file:///abs/x.mjs:3:1} from an ES module's frame,
 * {@code tests/test_x.py:12:} from pytest, {@code main.go:3:5:} from go,
 * {@code --> src/main.rs:3:5} from rustc, {@code src/app.ts(42,7)} from
 * tsc's older form, and {@code File "x.py", line 12} from a Python
 * traceback. Pure: this class reads a string and names spans; whether a
 * span is a FILE is the resolver's question, asked off the paint thread.
 *
 * <p>A hand scanner, not a regular expression: every character is looked at
 * a bounded number of times, so a hostile line cannot make a click slow
 * (the ReDoS class find-sec-bugs looks for), and only the first
 * {@link #MAX_LINE} characters are read at all.
 *
 * <p>What must NOT be a link is as much the contract as what must:
 * {@code http://host:8080} and {@code https://x.com/a.js:3} (a path after a
 * scheme is a URL's, not the disk's), {@code localhost:3000} (no file
 * extension), {@code 12:30:45} and {@code 127.0.0.1:8080} (an extension is
 * letters). {@code example.com:443} is syntactically a file and line; the
 * resolver refuses it because no such file exists, and says so.
 */
final class TerminalLinks {

    /** Characters read from one line; a longer line is scanned only this far. */
    static final int MAX_LINE = 4096;
    private static final int MAX_LINE_DIGITS = 7;
    private static final int MAX_COL_DIGITS = 5;
    private static final int MAX_EXTENSION = 12;
    private static final String PY_FILE = "File \"";
    private static final String PY_LINE = "\", line ";
    private static final String FILE_URL = "file://";

    private TerminalLinks() {
    }

    /**
     * One place: the {@code path} as printed (a {@code file://} prefix
     * removed), its 1-based {@code line}, its 1-based {@code column} or 0,
     * and the span [{@code start}, {@code end}) of the line a click may land
     * on to follow it.
     */
    record Link(int start, int end, String path, int line, int column) {
    }

    /** Every link on {@code text}, left to right. */
    static List<Link> find(String text) {
        List<Link> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String s = text.length() > MAX_LINE ? text.substring(0, MAX_LINE) : text;
        int n = s.length();
        int i = 0;
        while (i < n) {
            if (s.startsWith(PY_FILE, i)) {
                Link py = python(s, i);
                if (py != null) {
                    out.add(py);
                    i = py.end();
                    continue;
                }
            }
            int spanStart = i;
            int pathStart = i;
            if (s.regionMatches(true, i, FILE_URL, 0, FILE_URL.length())
                    && (i == 0 || !isPathChar(s.charAt(i - 1)))) {
                pathStart = i + FILE_URL.length();
            } else if (!isPathChar(s.charAt(i)) || (i > 0 && isPathChar(s.charAt(i - 1)))) {
                i++;
                continue;
            }
            int tokenEnd = tokenEnd(s, pathStart);
            if (tokenEnd == pathStart) {
                i = Math.max(i + 1, tokenEnd);
                continue;
            }
            Link link = located(s, spanStart, pathStart, tokenEnd);
            if (link != null) {
                out.add(link);
                i = link.end();
            } else {
                i = Math.max(i + 1, tokenEnd);
            }
        }
        return out;
    }

    /**
     * The link a click at {@code column} of {@code text} lands on; with a
     * negative column (where on the line is not known) the line's only link,
     * or null when it has none or several - a guess between two files is
     * not made.
     */
    static Link at(String text, int column) {
        List<Link> links = find(text);
        if (column < 0) {
            return links.size() == 1 ? links.get(0) : null;
        }
        for (Link l : links) {
            if (column >= l.start() && column < l.end()) {
                return l;
            }
        }
        return null;
    }

    /**
     * The file a link's path names, before anything is asked of the disk:
     * absolute paths as printed, {@code ~/} under {@code home}, and every
     * other path under the aimed {@code projectDir} - the folder a terminal
     * opened from the product starts in. Null when the path is relative and
     * there is no project (the caller says so).
     * A path relative to wherever the shell has since {@code cd}'d is not
     * known to the product and is never guessed at.
     */
    static File candidate(String path, File projectDir, File home) {
        String p = path;
        if ((p.startsWith("~/") || p.startsWith("~\\")) && home != null) {
            return new File(home, p.substring(2)).toPath().normalize().toFile();
        }
        File f = new File(p);
        if (f.isAbsolute() || isDriveAbsolute(p)) {
            return f.toPath().normalize().toFile();
        }
        if (projectDir == null) {
            return null;
        }
        return new File(projectDir, p).toPath().normalize().toFile();
    }

    private static boolean isDriveAbsolute(String p) {
        return p.length() >= 3 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':'
                && (p.charAt(2) == '\\' || p.charAt(2) == '/');
    }

    /** {@code File "x.py", line 12} - the path may hold spaces, it is quoted. */
    private static Link python(String s, int at) {
        int pathStart = at + PY_FILE.length();
        int close = s.indexOf('"', pathStart);
        if (close <= pathStart || !s.startsWith(PY_LINE, close)) {
            return null;
        }
        String path = s.substring(pathStart, close);
        if (path.startsWith("<") || !hasExtension(path)) {
            return null; // <stdin>, <frozen importlib._bootstrap>
        }
        int digits = close + PY_LINE.length();
        int end = digitsEnd(s, digits, MAX_LINE_DIGITS);
        if (end == digits || followedByWordChar(s, end)) {
            return null;
        }
        int line = Integer.parseInt(s.substring(digits, end));
        return line < 1 ? null : new Link(at, end, path, line, 0);
    }

    /** A path token followed by {@code :line[:col]} or {@code (line[,col])}, or null. */
    private static Link located(String s, int spanStart, int pathStart, int tokenEnd) {
        String path = s.substring(pathStart, tokenEnd);
        boolean fileUrl = pathStart != spanStart;
        if (!fileUrl && pathStart > 0 && s.charAt(pathStart - 1) == ':') {
            return null; // the rest of a scheme or a host:port - a URL's path, not the disk's
        }
        if (!fileUrl && path.startsWith("//")) {
            return null;
        }
        if (path.startsWith("[") && path.indexOf(']') < 0) {
            path = path.substring(1);
            spanStart++;
        }
        if (!hasExtension(path)) {
            return null;
        }
        int n = s.length();
        if (tokenEnd >= n) {
            return null;
        }
        char sep = s.charAt(tokenEnd);
        if (sep == ':') {
            int d = tokenEnd + 1;
            int e = digitsEnd(s, d, MAX_LINE_DIGITS);
            if (e == d || followedByWordChar(s, e)) {
                return null;
            }
            int line = Integer.parseInt(s.substring(d, e));
            int col = 0;
            int end = e;
            if (e < n && s.charAt(e) == ':') {
                int ce = digitsEnd(s, e + 1, MAX_COL_DIGITS);
                if (ce > e + 1 && !followedByWordChar(s, ce)) {
                    col = Integer.parseInt(s.substring(e + 1, ce));
                    end = ce;
                }
            }
            return line < 1 ? null : new Link(spanStart, end, path, line, col);
        }
        if (sep == '(') {
            int d = tokenEnd + 1;
            int e = digitsEnd(s, d, MAX_LINE_DIGITS);
            if (e == d || e >= n) {
                return null;
            }
            int line = Integer.parseInt(s.substring(d, e));
            int col = 0;
            int close = e;
            if (s.charAt(e) == ',') {
                int ce = digitsEnd(s, e + 1, MAX_COL_DIGITS);
                if (ce == e + 1) {
                    return null;
                }
                col = Integer.parseInt(s.substring(e + 1, ce));
                close = ce;
            }
            if (close >= n || s.charAt(close) != ')') {
                return null;
            }
            return line < 1 ? null : new Link(spanStart, close + 1, path, line, col);
        }
        return null;
    }

    /**
     * The end of the path token starting at {@code from}: letters, digits and
     * the punctuation paths are made of, plus a leading Windows drive
     * ({@code C:\}). A colon ends it, so {@code a.ts:3} is token {@code a.ts}.
     */
    private static int tokenEnd(String s, int from) {
        int n = s.length();
        int i = from;
        if (from + 2 < n && Character.isLetter(s.charAt(from)) && s.charAt(from + 1) == ':'
                && (s.charAt(from + 2) == '\\' || s.charAt(from + 2) == '/')
                && (from == 0 || !isPathChar(s.charAt(from - 1)))) {
            i = from + 2;
        }
        while (i < n && isPathChar(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * A file name is a person's word: a combining mark belongs to the letter
     * in front of it (NON_SPACING_MARK, COMBINING_SPACING_MARK, ENCLOSING_MARK),
     * so a Hindi file name is one token, not three (the v2.114.0 search bug,
     * not repeated here). The punctuation is what paths are made of; a colon,
     * a quote, a parenthesis, a space or {@code =} ends a path.
     */
    private static boolean isPathChar(char c) {
        if (Character.isLetterOrDigit(c)) {
            return true;
        }
        int type = Character.getType(c);
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
            return true;
        }
        switch (c) {
            case '.', '/', '\\', '_', '-', '~', '@', '+', '%', '[', ']':
                return true;
            default:
                return false;
        }
    }

    /**
     * The last segment carries an extension of letters and digits with at
     * least one letter: {@code app.ts}, {@code x.test.js}, {@code x.d.ts} -
     * not {@code 12}, {@code 45.123}, {@code 127.0.0.1} or {@code localhost}.
     */
    static boolean hasExtension(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String seg = path.substring(slash + 1);
        int dot = seg.lastIndexOf('.');
        if (dot <= 0 && !(dot == 0 && seg.length() > 1 && seg.indexOf('.', 1) < 0)) {
            return false;
        }
        String ext = seg.substring(dot + 1);
        if (ext.isEmpty() || ext.length() > MAX_EXTENSION) {
            return false;
        }
        boolean letter = false;
        for (int k = 0; k < ext.length(); k++) {
            char c = ext.charAt(k);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
            letter |= Character.isLetter(c);
        }
        return letter;
    }

    private static int digitsEnd(String s, int from, int max) {
        int i = from;
        int n = s.length();
        while (i < n && i - from < max && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        if (i < n && i - from == max && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            return from; // too many digits to be a line number
        }
        return i;
    }

    /** {@code a.ts:12abc} or {@code a.ts:12.5} is not a line number. */
    private static boolean followedByWordChar(String s, int at) {
        if (at >= s.length()) {
            return false;
        }
        char c = s.charAt(at);
        return Character.isLetterOrDigit(c) || c == '_'
                || (c == '.' && at + 1 < s.length() && Character.isDigit(s.charAt(at + 1)));
    }
}
