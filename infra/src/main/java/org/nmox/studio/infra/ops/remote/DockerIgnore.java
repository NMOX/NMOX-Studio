package org.nmox.studio.infra.ops.remote;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of a project's {@code .dockerignore} that a deploy can apply
 * to its UPLOAD and be certain about.
 *
 * <p>The server's {@code docker build} reads the same file and is the
 * authority on what reaches the image; this class only keeps the upload
 * from carrying what docker would throw away on arrival — and from being
 * refused for it, because the bundle's caps count every file that
 * travels (a project with a large ignored {@code data/} was refused for
 * 20,000 files that no image would ever have held).
 *
 * <p>So the rule is one-sided on purpose: leaving out LESS than docker
 * would is always safe (docker ignores the rest again), leaving out MORE
 * would build a different image than the user's own {@code docker build}
 * does. Anything this class is not certain about is therefore not
 * applied, and says so:
 * <ul>
 *   <li>a file with ANY exception line ({@code !keep.me}) is not applied
 *       at all — an exception can bring back something an earlier line
 *       excluded, and getting half of that right is getting it wrong;</li>
 *   <li>a line with a character class ({@code [a-z]}), an escape
 *       ({@code \}), one of {@code + ( ) | { } ^} (docker's releases
 *       disagree with each other about those), a control character or
 *       bytes that were not text is passed over;</li>
 *   <li>a line that climbs ({@code ..}) or names the context itself
 *       ({@code .}) is passed over.</li>
 * </ul>
 * What is left is docker's own matching for the forms people write:
 * names and paths anchored at the context root, {@code *} and {@code ?}
 * within one path segment, and {@code **} for any number of segments. A
 * directory that matches takes everything under it.
 *
 * <p>Pure: text in, decisions out.
 */
public final class DockerIgnore {

    /** Applies nothing: no file, or a file this class will not apply. */
    public static final DockerIgnore NONE = new DockerIgnore(List.of(), List.of(), false);

    private final List<List<String>> excludes;
    private final List<String> passedOver;
    private final boolean hasExceptions;

    private DockerIgnore(List<List<String>> excludes, List<String> passedOver, boolean hasExceptions) {
        this.excludes = excludes;
        this.passedOver = passedOver;
        this.hasExceptions = hasExceptions;
    }

    /**
     * Reads a {@code .dockerignore}'s text the way docker's own reader
     * does, because where the two would differ this class would be
     * applying a file docker reads differently. Never throws: a line it
     * cannot be sure of is passed over.
     *
     * <p>Docker's reader (moby, {@code ignorefile.ReadAll}): lines end at
     * a line feed and nowhere else; a byte-order mark is taken off the
     * first line only; a line whose FIRST character is {@code #} is a
     * comment; then the line is trimmed of Unicode space, and a line that
     * then begins with {@code !} is an exception. Java's own notions
     * differ in each place (a {@code \R} split also ends a line at a form
     * feed or U+2028; {@code strip()} leaves a no-break space and takes
     * the separator controls U+001C to U+001F; neither knows a byte-order
     * mark), and each difference could hide an exception line or split
     * one name into two.
     */
    public static DockerIgnore parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        if (text.charAt(0) == BYTE_ORDER_MARK) {
            text = text.substring(1);
        }
        List<List<String>> excludes = new ArrayList<>();
        List<String> passedOver = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            // a carriage return before the line feed needs no step of its own: it is space, and trimmed below
            if (raw.startsWith("#")) {
                continue;
            }
            String line = trimAsDockerDoes(raw);
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("!")) {
                // one exception makes every earlier line conditional: apply none of the file
                return new DockerIgnore(List.of(), List.of(), true);
            }
            if (line.startsWith("#")) {
                // "  #name": docker reads a pattern for a file called #name; the line's author meant a
                // comment. Applying neither reading is the safe one.
                continue;
            }
            List<String> p = segmentsOf(line);
            if (p == null) {
                passedOver.add(printable(line));
            } else {
                excludes.add(p);
            }
        }
        return new DockerIgnore(List.copyOf(excludes), List.copyOf(passedOver), false);
    }

    private static final char BYTE_ORDER_MARK = '\uFEFF';
    /** What a decoder leaves where the file's bytes were not text. */
    private static final char REPLACEMENT = '\uFFFD';
    /** How much of a passed-over line is repeated in the log. */
    private static final int MAX_SHOWN = 80;

    /** Go's {@code strings.TrimSpace}: {@code unicode.IsSpace} from both ends. */
    static String trimAsDockerDoes(String s) {
        int from = 0;
        int to = s.length();
        while (from < to && goSpace(s.charAt(from))) {
            from++;
        }
        while (to > from && goSpace(s.charAt(to - 1))) {
            to--;
        }
        return s.substring(from, to);
    }

    /** {@code unicode.IsSpace}: tab to carriage return, NEL, and the Z categories (space and no-break space among them). */
    private static boolean goSpace(char c) {
        return (c >= '\t' && c <= '\r') || c == '\u0085' || Character.isSpaceChar(c);
    }

    /** A passed-over line as the log may repeat it: one line, no control characters, not longer than a path. */
    private static String printable(String line) {
        int shown = Math.min(line.length(), MAX_SHOWN);
        if (shown < line.length() && Character.isHighSurrogate(line.charAt(shown - 1))) {
            shown--;
        }
        StringBuilder b = new StringBuilder(shown + 1);
        for (int i = 0; i < shown; i++) {
            char c = line.charAt(i);
            b.append(Character.isISOControl(c) || c == REPLACEMENT ? '?' : c);
        }
        return shown < line.length() ? b.append('\u2026').toString() : b.toString();
    }

    /** True when the file has an exception line and was therefore not applied at all. */
    public boolean hasExceptions() {
        return hasExceptions;
    }

    /** The lines that were not applied because their form is not one this class is certain about. */
    public List<String> passedOver() {
        return passedOver;
    }

    /** True when nothing would ever be excluded. */
    public boolean isEmpty() {
        return excludes.isEmpty();
    }

    /**
     * Whether docker would leave this path out of the build context.
     * The Dockerfile and the ignore file themselves always travel:
     * docker sends them to the daemon whatever the file says.
     *
     * @param relative the path from the context root, {@code /}-separated, no leading slash
     */
    public boolean excludes(String relative) {
        if (relative.equals("Dockerfile") || relative.equals(".dockerignore")) {
            return false;
        }
        String[] path = relative.split("/");
        for (List<String> p : excludes) {
            if (matches(p, path)) {
                return true;
            }
        }
        return false;
    }

    /** The marker for a {@code **} segment in a parsed line. */
    private static final String ANY_DEPTH = "**";
    /** The longest line and the most segments that are applied; a longer one is passed over, not searched. */
    private static final int MAX_LINE = 512;
    private static final int MAX_SEGMENTS = 32;

    /**
     * Characters whose line is passed over. A class ({@code [}) and an
     * escape (a backslash) are docker features this class does not
     * implement. The rest are ordinary characters of a NAME that docker
     * turns into a regular expression together with the line's stars:
     * its newer releases escape {@code + ( ) | { }} first and its older
     * ones did not ({@code c++*} was "one or more c" there), and none
     * escapes {@code ^}. Which release builds on the host is not known
     * here, so a line holding one is the host's to apply.
     */
    private static final String UNCERTAIN = "[]\\+()|{}^";

    /**
     * One line as its path segments, cleaned the way docker cleans a
     * pattern (no leading or trailing slash, no doubled slashes, no
     * {@code .} segments), or null when the line is of a form that is
     * passed over.
     */
    static List<String> segmentsOf(String line) {
        if (line.length() > MAX_LINE) {
            return null;
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (UNCERTAIN.indexOf(c) >= 0 || Character.isISOControl(c) || c == REPLACEMENT) {
                return null;
            }
        }
        List<String> segments = new ArrayList<>();
        for (String seg : line.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                return null;
            }
            if (seg.contains(ANY_DEPTH) && !seg.equals(ANY_DEPTH)) {
                return null; // "a**b": docker has a reading for it; this class does not vouch for its own
            }
            segments.add(seg);
        }
        if (segments.isEmpty() || segments.size() > MAX_SEGMENTS) {
            return null; // "." or "/" is the context itself
        }
        return List.copyOf(segments);
    }

    /**
     * Docker's matching, segment by segment and without a regular
     * expression (a line is a stranger's text; a pattern of many stars
     * compiled to one would be a way to make a deploy think forever).
     * {@code **} between segments is any number of them, none included;
     * {@code **} at the end is at least one — docker reads {@code a/**}
     * as "everything under a", not a itself.
     */
    private static boolean matches(List<String> pattern, String[] path) {
        // every (segment of the line, segment of the path) pair is tried at most once: a line of many **
        // would otherwise be tried in every combination
        return matches(pattern, 0, path, 0, new boolean[pattern.size() + 1][path.length + 1]);
    }

    private static boolean matches(List<String> pattern, int i, String[] path, int j, boolean[][] failed) {
        if (failed[i][j]) {
            return false;
        }
        boolean ok;
        if (i == pattern.size()) {
            ok = j == path.length;
        } else if (pattern.get(i).equals(ANY_DEPTH)) {
            if (i == pattern.size() - 1) {
                ok = j < path.length;
            } else {
                ok = false;
                for (int k = j; k <= path.length && !ok; k++) {
                    ok = matches(pattern, i + 1, path, k, failed);
                }
            }
        } else {
            ok = j < path.length && segmentMatches(pattern.get(i), path[j])
                    && matches(pattern, i + 1, path, j + 1, failed);
        }
        if (!ok) {
            failed[i][j] = true;
        }
        return ok;
    }

    /** {@code *} is any run of characters and {@code ?} is one, both within a single name; linear with one backtrack point. */
    static boolean segmentMatches(String glob, String name) {
        int g = 0;
        int n = 0;
        int star = -1;
        int mark = 0;
        while (n < name.length()) {
            if (g < glob.length() && glob.charAt(g) == '*') {
                star = g++;
                mark = n;
            } else if (g < glob.length() && (glob.charAt(g) == '?' || glob.charAt(g) == name.charAt(n))) {
                g++;
                n++;
            } else if (star >= 0) {
                g = star + 1;
                n = ++mark;
            } else {
                return false;
            }
        }
        while (g < glob.length() && glob.charAt(g) == '*') {
            g++;
        }
        return g == glob.length();
    }
}
