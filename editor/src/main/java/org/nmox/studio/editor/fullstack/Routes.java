package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The client-call → server-route seam (v2.31.0, the full-stack
 * wishlist): a fetch's {@code '/api/users'} and the Express route that
 * serves it live ten files apart in the same project, and the editor
 * that jumps class→rule across three languages could not connect them.
 * ⌘-click the path string in a {@code fetch(}/{@code axios.*} call and
 * land on the route registration.
 *
 * <p>The route rule is the v1.292.0 outline rule, deliberately narrow
 * for the same reason it is there (a wrong jump is worse than none):
 * app/router-shaped receiver, a real HTTP verb, a string path. The
 * CLIENT side is equally narrow — only {@code fetch(} and the axios
 * family, only string literals starting with {@code /} — so an
 * arbitrary string never becomes a jump point. Exact-path match only:
 * {@code /api/users/:id} is not matched by {@code /api/users/123}
 * (recorded limit — param-aware matching waits for a real need).
 */
public final class Routes {

    private Routes() {
    }

    // the v1.292.0 outline rule (OutlineModel.JS_ROUTE), replicated —
    // the outline anchors ^ per line; this one is used with find() on
    // MULTILINE for the project sweep
    static final Pattern SERVER_ROUTE = Pattern.compile(
            "^\\s*(?:module\\.exports\\s*=\\s*)?"
            + "[A-Za-z0-9_$]*(?:app|App|router|Router|server|Server|api|Api)"
            + "\\s*\\.\\s*(get|post|put|patch|delete|head|options|all)"
            + "\\s*\\(\\s*[`'\"]([^`'\"]*)[`'\"]", Pattern.MULTILINE);

    private static final List<String> CLIENT_CALLS = List.of(
            "fetch(", "axios(", "axios.get(", "axios.post(", "axios.put(",
            "axios.patch(", "axios.delete(", "axios.head(");

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "out", "coverage",
            "target", ".next", ".nuxt", ".angular", ".svelte-kit");

    static final int MAX_FILES = 80;
    private static final long MAX_FILE_BYTES = 256 * 1024;

    /** A route registration: verb, path, its file, offset of the path. */
    public record Route(String verb, String path, File file, int offset) {
    }

    // ---- the client side --------------------------------------------------

    /**
     * The path-string span under {@code offset} when it is the string
     * argument of a recognized client call and starts with {@code /} —
     * the ⌘-click subject. Returns {start, end} or null.
     */
    public static int[] clientPathSpanAt(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && isPathChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isPathChar(text.charAt(end))) {
            end++;
        }
        if (end == start || text.charAt(start) != '/') {
            return null;
        }
        if (start == 0) {
            return null;
        }
        char quote = text.charAt(start - 1);
        if (quote != '\'' && quote != '"' && quote != '`') {
            return null;
        }
        String beforeQuote = text.substring(0, start - 1).stripTrailing();
        for (String call : CLIENT_CALLS) {
            if (beforeQuote.endsWith(call)) {
                return new int[] {start, end};
            }
        }
        return null;
    }

    private static boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '/' || c == '-' || c == '_' || c == '.' || c == ':';
    }

    // ---- the server side --------------------------------------------------

    /** Every route registration in one file's text. */
    static List<Route> routesIn(String text, File file) {
        List<Route> out = new ArrayList<>();
        Matcher m = SERVER_ROUTE.matcher(text);
        while (m.find()) {
            out.add(new Route(m.group(1), m.group(2), file, m.start(2)));
        }
        return out;
    }

    /**
     * The first route in the project whose path EQUALS {@code path} —
     * a bounded sweep over the project's JS/TS sources (same caps and
     * skip list as the design scans; uncached, a click-time query).
     * Callers run this OFF the EDT.
     */
    public static Route findRoute(File root, String path) {
        if (root == null || !root.isDirectory() || path == null || path.isEmpty()) {
            return null;
        }
        List<File> sources = new ArrayList<>();
        collect(root, sources, 0);
        for (File f : sources) {
            try {
                for (Route r : routesIn(Files.readString(f.toPath()), f)) {
                    if (r.path().equals(path)) {
                        return r;
                    }
                }
            } catch (IOException | OutOfMemoryError unreadable) {
                // skip the file, keep the sweep
            }
        }
        return null;
    }

    private static void collect(File dir, List<File> sources, int depth) {
        if (depth > 6 || sources.size() >= MAX_FILES) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (sources.size() >= MAX_FILES) {
                return;
            }
            String name = f.getName();
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                    collect(f, sources, depth + 1);
                }
            } else if ((name.endsWith(".js") || name.endsWith(".mjs")
                    || name.endsWith(".cjs") || name.endsWith(".ts"))
                    && f.length() <= MAX_FILE_BYTES) {
                sources.add(f);
            }
        }
    }
}
