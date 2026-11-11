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
 *   <li>a line with a character class ({@code [a-z]}) or an escape
 *       ({@code \}) is passed over;</li>
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

    /** Reads a {@code .dockerignore}'s text. Never throws: a line it cannot be sure of is passed over. */
    public static DockerIgnore parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        List<List<String>> excludes = new ArrayList<>();
        List<String> passedOver = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("!")) {
                // one exception makes every earlier line conditional: apply none of the file
                return new DockerIgnore(List.of(), List.of(), true);
            }
            List<String> p = segmentsOf(line);
            if (p == null) {
                passedOver.add(line);
            } else {
                excludes.add(p);
            }
        }
        return new DockerIgnore(List.copyOf(excludes), List.copyOf(passedOver), false);
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
     * One line as its path segments, cleaned the way docker cleans a
     * pattern (no leading or trailing slash, no doubled slashes, no
     * {@code .} segments), or null when the line is of a form that is
     * passed over.
     */
    static List<String> segmentsOf(String line) {
        if (line.length() > MAX_LINE || line.indexOf('[') >= 0 || line.indexOf('\\') >= 0) {
            return null;
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
