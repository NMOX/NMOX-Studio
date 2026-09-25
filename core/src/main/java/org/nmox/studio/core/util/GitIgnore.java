package org.nmox.studio.core.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The rules one {@code .gitignore} file states, and the question git asks
 * of them: is this path ignored? Pure — no disk, no platform — so every
 * rule of git's documented semantics is a unit test, and so the reader
 * that feeds it (a project's sharability answer) owns all of the IO.
 *
 * <p><b>Why a subset, and which way it errs.</b> This is read by the
 * platform's {@code SharabilityQuery}, and the platform's git module
 * treats {@code NOT_SHARABLE} as ignored — for a path git itself still
 * tracks it would hide the change from a commit and may write the path
 * into the user's {@code .gitignore} ({@code GitUtils.isIgnored}, read
 * from the RELEASE310 bytecode). So a rule this class cannot read EXACTLY
 * as git reads it is dropped rather than approximated: an under-match
 * only means a search lists a file git ignores, an over-match changes
 * what the user's repository does. What is supported is git's own list —
 * blank lines and {@code #} comments, {@code \#} and {@code \!} escapes,
 * trailing spaces unless escaped, {@code !} negation, a trailing {@code /}
 * for directories only, a slash anywhere else anchoring the pattern to
 * this file's directory, {@code *} {@code ?} and {@code [...]} (with
 * {@code !}/{@code ^} and ranges) inside one path segment, and
 * {@code **} as a whole segment (leading, trailing or between).
 *
 * <p><b>Where a dropped line could matter, the file says so.</b> Dropping
 * a rule under-matches only when the rule EXCLUDES; a dropped {@code !}
 * rule would have brought a path back, so dropping it ignores MORE. A
 * file that lost a negation — unreadable, over-long, past the rule cap —
 * is therefore {@linkplain #doubtful() doubtful}, and a chain holding a
 * doubtful file never answers "ignored" (3.2 review: {@code *.log} plus
 * {@code !*[[:digit:]].log} hid {@code a1.log}, which git keeps).
 *
 * <p><b>Bytes, and case.</b> Git matches {@code ?} and {@code [...]} one
 * BYTE at a time, so both sides are compared as their UTF-8 bytes
 * ({@code ?x} does not match {@code éx}; git agrees). Git folds case
 * where {@code core.ignorecase} is set — every fresh repository on macOS
 * and Windows — and not elsewhere; this class never reads the config and
 * instead lets a NEGATION match either way while an exclusion matches
 * exactly, so both settings err toward searching more.
 *
 * <p><b>Bounded.</b> A file this reads arrives with a clone. The reader
 * caps the bytes; this caps the rules ({@link #MAX_RULES}) and the line
 * ({@link #MAX_LINE}), and matching never backtracks exponentially:
 * a segment is matched by the classic single-star-backtrack scan
 * (O(pattern × name)) and {@code **} by a table over segments
 * (O(pattern segments × path segments)) — no regular expression is
 * built from a stranger's text.
 */
public final class GitIgnore {

    /** Rules past this many are not read; a real file has dozens. */
    public static final int MAX_RULES = 5_000;

    /** A line longer than this is not a pattern anyone wrote by hand. */
    public static final int MAX_LINE = 4_096;

    /** Paths deeper than this are not judged (the chain costs depth squared). */
    public static final int MAX_DEPTH = 64;

    /** What one file says about one path. */
    public enum Verdict {
        /** No rule in this file matched: the verdict above it stands. */
        NONE,
        /** The last matching rule excludes the path. */
        IGNORED,
        /** The last matching rule is a negation: the path is back in. */
        INCLUDED
    }

    private static final GitIgnore EMPTY = new GitIgnore(List.of(), false);

    private static final GitIgnore UNKNOWN = new GitIgnore(List.of(), true);

    private final List<Rule> rules;

    /** A line that might have been a negation was not read. */
    private final boolean doubtful;

    private GitIgnore(List<Rule> rules, boolean doubtful) {
        this.rules = rules;
        this.doubtful = doubtful;
    }

    /** A file with no rules — what an ABSENT file states. */
    public static GitIgnore empty() {
        return EMPTY;
    }

    /**
     * A file that exists but could not be read (too large, unreadable):
     * nothing it says is known, including what it re-includes, so no path
     * beneath it is answered "ignored".
     */
    public static GitIgnore unknown() {
        return UNKNOWN;
    }

    /**
     * Whether a line that could have re-included a path ({@code !...}) was
     * dropped, or the file was not read at all.
     */
    public boolean doubtful() {
        return doubtful;
    }

    /** How many rules were kept (dropped lines are not counted). */
    public int size() {
        return rules.size();
    }

    /**
     * Reads the rules of one file's text, in file order. A line this
     * class cannot read exactly as git does is dropped, alone.
     */
    public static GitIgnore parse(String text) {
        if (text == null || text.isEmpty()) {
            return EMPTY;
        }
        List<Rule> out = new ArrayList<>();
        boolean doubt = false;
        int start = 0;
        int n = text.length();
        while (start <= n) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = n;
            }
            if (out.size() >= MAX_RULES) {
                // past the cap: nothing more is read, and if a negation
                // lies in what was not read, the file can no longer say
                if (start < n && text.charAt(start) == '!' || text.indexOf("\n!", start) >= 0) {
                    doubt = true;
                }
                break;
            }
            if (end - start <= MAX_LINE) {
                String line = text.substring(start, end);
                Rule r = Rule.of(line);
                if (r != null) {
                    out.add(r);
                } else if (line.startsWith("!")) {
                    doubt = true; // a re-include this class could not read
                }
            } else if (text.charAt(start) == '!') {
                doubt = true;
            }
            start = end + 1;
        }
        if (out.isEmpty()) {
            return doubt ? UNKNOWN : EMPTY;
        }
        return new GitIgnore(Collections.unmodifiableList(out), doubt);
    }

    /**
     * What this file says about {@code relPath}: the path relative to the
     * directory holding this file, {@code /}-separated, no leading slash.
     * The LAST matching rule decides, as in git.
     */
    public Verdict match(String relPath, boolean isDirectory) {
        String[] segments = relPath.split("/", -1);
        String[] folded = new String[segments.length];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = bytes(segments[i]);
            folded[i] = fold(segments[i]);
        }
        Verdict v = Verdict.NONE;
        for (Rule r : rules) {
            if (r.matches(segments, isDirectory)
                    || r.negated && r.foldedMatches(folded, isDirectory)) {
                v = r.negated ? Verdict.INCLUDED : Verdict.IGNORED;
            }
        }
        return v;
    }

    /**
     * Git's whole question over a chain of files: is {@code relPath}
     * (relative to the work tree's root) ignored?
     *
     * <p>Every leading directory is asked first, root-most first, because
     * git never looks inside an ignored directory — "it is not possible to
     * re-include a file if a parent directory of that file is excluded".
     * For each prefix the files are consulted from lowest precedence to
     * highest: {@code base} (the repository's {@code info/exclude}), then
     * the {@code .gitignore} of the root, then of each deeper directory
     * down to the prefix's parent; the last one with an opinion decides.
     *
     * @param relPath    the path under the root, {@code /}-separated
     * @param isDirectory whether the path itself is a directory
     * @param base       the lowest-precedence rules, rooted at the root
     *                   (never null; {@link #empty()} when there are none)
     * @param rulesInDir the {@code .gitignore} of a directory given its
     *                   path under the root ({@code ""} for the root);
     *                   {@link #empty()} when it has none
     */
    public static boolean isIgnored(String relPath, boolean isDirectory,
            GitIgnore base, Function<String, GitIgnore> rulesInDir) {
        if (relPath == null || relPath.isEmpty()) {
            return false;
        }
        String[] s = relPath.split("/");
        if (s.length > MAX_DEPTH) {
            return false; // no real tree is this deep: under-match, never guess
        }
        for (int k = 1; k <= s.length; k++) {
            boolean prefixIsDir = k < s.length || isDirectory;
            String prefix = String.join("/", java.util.Arrays.copyOfRange(s, 0, k));
            Verdict v = base.match(prefix, prefixIsDir);
            boolean doubt = base.doubtful;
            for (int j = 0; j < k; j++) {
                String dir = String.join("/", java.util.Arrays.copyOfRange(s, 0, j));
                GitIgnore g = rulesInDir.apply(dir);
                if (g == null) {
                    continue;
                }
                doubt |= g.doubtful;
                if (g.rules.isEmpty()) {
                    continue;
                }
                String under = String.join("/", java.util.Arrays.copyOfRange(s, j, k));
                Verdict w = g.match(under, prefixIsDir);
                if (w != Verdict.NONE) {
                    v = w;
                }
            }
            if (v == Verdict.IGNORED) {
                // a doubtful file might have re-included this prefix; and
                // past an ignored directory git looks no further, so
                // neither does this: under-match, never guess
                return !doubt;
            }
        }
        return false;
    }

    /** A name as git sees it: its UTF-8 bytes, one char per byte. */
    static String bytes(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) {
                return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            }
        }
        return s; // ASCII: already one char per byte
    }

    /** ASCII case folding, as git's {@code WM_CASEFOLD} does it. */
    static String fold(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (b == null) {
                    b = new StringBuilder(s);
                }
                b.setCharAt(i, (char) (c + ('a' - 'A')));
            }
        }
        return b == null ? s : b.toString();
    }

    /** One line of a file, read. */
    private static final class Rule {

        final boolean negated;
        final boolean dirOnly;
        /** Anchored rules match the whole path; the rest match a name at any depth. */
        final boolean anchored;
        final String[] segments;
        /** The segments folded, for a negation's case-blind second try. */
        final String[] folded;

        private Rule(boolean negated, boolean dirOnly, boolean anchored, String[] segments) {
            this.negated = negated;
            this.dirOnly = dirOnly;
            this.anchored = anchored;
            this.segments = segments;
            this.folded = new String[segments.length];
            for (int i = 0; i < segments.length; i++) {
                folded[i] = fold(segments[i]);
            }
        }

        static Rule of(String rawLine) {
            String line = rawLine;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            // trailing spaces are dropped unless the last is escaped — by
            // an ODD run of backslashes: "foo\\ " is an escaped backslash
            // followed by a space git trims
            int end = line.length();
            while (end > 0 && line.charAt(end - 1) == ' ') {
                int slashes = 0;
                while (end - 2 - slashes >= 0 && line.charAt(end - 2 - slashes) == '\\') {
                    slashes++;
                }
                if ((slashes & 1) != 0) {
                    break;
                }
                end--;
            }
            line = line.substring(0, end);
            if (line.isEmpty() || line.charAt(0) == '#') {
                return null;
            }
            boolean negated = false;
            if (line.charAt(0) == '!') {
                negated = true;
                line = line.substring(1);
            }
            boolean dirOnly = false;
            if (line.endsWith("/") && !line.endsWith("\\/")) {
                dirOnly = true;
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                return null;
            }
            boolean anchored = line.indexOf('/') >= 0;
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (line.isEmpty() || line.contains("//")) {
                return null;
            }
            String[] segs = line.split("/", -1);
            for (int i = 0; i < segs.length; i++) {
                if (segs[i].isEmpty() || !wellFormed(segs[i])) {
                    return null;
                }
                segs[i] = bytes(segs[i]);
            }
            return new Rule(negated, dirOnly, anchored, segs);
        }

        /** Brackets closed and no dangling escape: git's grammar, exactly. */
        private static boolean wellFormed(String seg) {
            int i = 0;
            int n = seg.length();
            while (i < n) {
                char c = seg.charAt(i);
                if (c == '\\') {
                    if (i + 1 >= n) {
                        return false;
                    }
                    i += 2;
                } else if (c == '[') {
                    int close = bracketEnd(seg, i);
                    // [:alpha:] and its kin are git's too, and read wrong
                    // here would match a different set: drop, never guess
                    if (close < 0 || seg.substring(i, close).contains("[:")) {
                        return false;
                    }
                    i = close + 1;
                } else {
                    i++;
                }
            }
            return true;
        }

        boolean matches(String[] path, boolean isDirectory) {
            return matches(segments, path, isDirectory);
        }

        boolean foldedMatches(String[] foldedPath, boolean isDirectory) {
            return matches(folded, foldedPath, isDirectory);
        }

        private boolean matches(String[] pat, String[] path, boolean isDirectory) {
            if (dirOnly && !isDirectory) {
                return false;
            }
            if (!anchored) {
                // a name rule: the path's last segment, at any depth
                return segmentMatches(pat[0], path[path.length - 1]);
            }
            return segmentsMatch(pat, path);
        }
    }

    /**
     * Pattern segments against path segments, {@code **} as a whole
     * segment spanning any number of path segments — except trailing,
     * where git's "everything inside" means at least one.
     */
    static boolean segmentsMatch(String[] p, String[] s) {
        int pn = p.length;
        int sn = s.length;
        // ok[i][j]: p[i..] matches s[j..]
        boolean[][] ok = new boolean[pn + 1][sn + 1];
        ok[pn][sn] = true;
        for (int i = pn - 1; i >= 0; i--) {
            boolean dstar = "**".equals(p[i]);
            for (int j = sn; j >= 0; j--) {
                if (dstar) {
                    if (i == pn - 1) {
                        ok[i][j] = j < sn; // trailing: one or more
                    } else {
                        ok[i][j] = ok[i + 1][j] || (j < sn && ok[i][j + 1]);
                    }
                } else {
                    ok[i][j] = j < sn && segmentMatches(p[i], s[j]) && ok[i + 1][j + 1];
                }
            }
        }
        return ok[0][0];
    }

    /**
     * One pattern segment against one name: {@code *} any run,
     * {@code ?} one character, {@code [...]} a class, {@code \x} a
     * literal. The single-star backtrack: on a mismatch return to the
     * last star and let it swallow one more character — linear in
     * practice, O(pattern × name) at worst, never exponential.
     */
    static boolean segmentMatches(String pat, String name) {
        int p = 0;
        int s = 0;
        int starP = -1;
        int starS = -1;
        int pn = pat.length();
        int sn = name.length();
        while (s < sn) {
            if (p < pn) {
                char c = pat.charAt(p);
                if (c == '*') {
                    while (p < pn && pat.charAt(p) == '*') {
                        p++;
                    }
                    starP = p;
                    starS = s;
                    continue;
                }
                int step = singleMatch(pat, p, name.charAt(s));
                if (step > 0) {
                    p += step;
                    s++;
                    continue;
                }
            }
            if (starP < 0) {
                return false;
            }
            p = starP;
            s = ++starS;
        }
        while (p < pn && pat.charAt(p) == '*') {
            p++;
        }
        return p == pn;
    }

    /**
     * Does the pattern element at {@code p} match {@code ch}? Returns how
     * many pattern characters the element spans, or 0 on a mismatch.
     */
    private static int singleMatch(String pat, int p, char ch) {
        char c = pat.charAt(p);
        if (c == '?') {
            return 1;
        }
        if (c == '\\') {
            return pat.charAt(p + 1) == ch ? 2 : 0;
        }
        if (c == '[') {
            int close = bracketEnd(pat, p);
            return inClass(pat, p + 1, close, ch) ? close - p + 1 : 0;
        }
        return c == ch ? 1 : 0;
    }

    /** Index of the {@code ]} closing the class opened at {@code open}, or -1. */
    private static int bracketEnd(String s, int open) {
        int i = open + 1;
        if (i < s.length() && (s.charAt(i) == '!' || s.charAt(i) == '^')) {
            i++;
        }
        if (i < s.length() && s.charAt(i) == ']') {
            i++; // a leading ] is a member
        }
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == ']') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static boolean inClass(String s, int from, int close, char ch) {
        int i = from;
        boolean negate = false;
        if (s.charAt(i) == '!' || s.charAt(i) == '^') {
            negate = true;
            i++;
        }
        boolean hit = false;
        boolean first = true;
        while (i < close) {
            char lo = s.charAt(i);
            if (lo == '\\' && i + 1 < close) {
                lo = s.charAt(++i);
            } else if (lo == ']' && !first) {
                break;
            }
            first = false;
            if (i + 2 < close && s.charAt(i + 1) == '-') {
                char hi = s.charAt(i + 2);
                if (hi == '\\' && i + 3 < close) {
                    hi = s.charAt(i + 3);
                    i++;
                }
                if (lo <= ch && ch <= hi) {
                    hit = true;
                }
                i += 3;
            } else {
                if (lo == ch) {
                    hit = true;
                }
                i++;
            }
        }
        return hit != negate;
    }
}
