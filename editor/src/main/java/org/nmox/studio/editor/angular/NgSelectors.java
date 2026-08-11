package org.nmox.studio.editor.angular;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The project's component selectors (the Angular-top arc, 2026-08-11):
 * where is {@code <app-hero>} declared? WebStorm answers this always;
 * we answered it only when the Angular Language Service was installed.
 * This index answers it from the source alone — a bounded scan of the
 * workspace's {@code .ts} files for {@code @Component} decorators and
 * their {@code selector:} strings, cached per file by (mtime, size)
 * exactly like {@link NgTemplates} and CssTokens, so a scan storm pays
 * each read once.
 *
 * <p>Selector strings are stored RAW and matched per comma-part: a
 * component may declare {@code selector: 'app-a, app-b'} or an
 * attribute form {@code [appThing]} — an element lookup matches any
 * trimmed element part. Recorded limit: selector expressions beyond
 * comma-lists (combinators, {@code :not()}) match only on exact text.
 */
public final class NgSelectors {

    /** A component declaration: where the selector's own text sits. */
    public record Decl(File file, String selector, int offset) {
    }

    static final int SNIFF_BYTES = 16 * 1024;
    private static final int MAX_FILES = 400;
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage",
            ".angular", "out", "target");

    /** {@code selector: 'app-hero'} — either quote, decorator files only. */
    private static final Pattern SELECTOR =
            Pattern.compile("selector\\s*:\\s*['\"]([^'\"]+)['\"]");

    private record Cached(long mtime, long size, List<Decl> decls) {
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_CAP = 1024;

    private NgSelectors() {
    }

    /** Every component selector under {@code root}; empty when none. */
    public static List<Decl> scanProject(File root) {
        List<Decl> out = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        int[] budget = {MAX_FILES};
        walk(root, out, budget, 0);
        return out;
    }

    /** The declaration whose selector list contains {@code tag}, or null. */
    public static Decl find(File root, String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        for (Decl d : scanProject(root)) {
            for (String part : d.selector().split(",")) {
                if (part.trim().equals(tag)) {
                    return d;
                }
            }
        }
        return null;
    }

    private static void walk(File dir, List<Decl> out, int[] budget, int depth) {
        if (depth > 12 || budget[0] <= 0) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (budget[0] <= 0) {
                return;
            }
            String name = f.getName();
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                    walk(f, out, budget, depth + 1);
                }
            } else if (name.endsWith(".ts") && !name.endsWith(".spec.ts")
                    && !name.endsWith(".d.ts")) {
                budget[0]--;
                out.addAll(declsOf(f));
            }
        }
    }

    private static List<Decl> declsOf(File ts) {
        long mtime = ts.lastModified();
        long size = ts.length();
        String key = ts.getAbsolutePath();
        Cached c = CACHE.get(key);
        if (c != null && c.mtime() == mtime && c.size() == size) {
            return c.decls();
        }
        List<Decl> decls = parse(ts);
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear(); // bounded by wholesale clear (NgTemplates idiom)
        }
        CACHE.put(key, new Cached(mtime, size, decls));
        return decls;
    }

    private static List<Decl> parse(File ts) {
        String head = readHead(ts);
        // decorator-gated: a selector: key in arbitrary TS (a test
        // fixture, a config literal) must not become a jump target
        if (!head.contains("@Component") && !head.contains("@Directive")) {
            return List.of();
        }
        List<Decl> decls = new ArrayList<>(1);
        Matcher m = SELECTOR.matcher(head);
        while (m.find()) {
            decls.add(new Decl(ts, m.group(1), m.start(1)));
        }
        return List.copyOf(decls);
    }

    private static String readHead(File ts) {
        try (InputStream in = Files.newInputStream(ts.toPath())) {
            return new String(in.readNBytes(SNIFF_BYTES), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }

    /** Test seam: forget cached verdicts. */
    static void clearCacheForTest() {
        CACHE.clear();
    }
}
