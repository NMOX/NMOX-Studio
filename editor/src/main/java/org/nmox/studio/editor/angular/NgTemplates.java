package org.nmox.studio.editor.angular;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Is this {@code .html} file an Angular template? (Ledger 73, David's
 * call 2026-08-11: the Angular bet means ALL Angular repos, not just
 * ones our generators made.)
 *
 * <p>Angular 21's CLI generates SUFFIXLESS files by default —
 * {@code widget.ts} beside {@code widget.html} — while the v1.217.0
 * declarative resolver keys on the {@code .component.html} spelling
 * our own generators pin. The honest discriminator for the suffixless
 * world is CONTENT: an {@code .html} file is a template when a
 * same-basename {@code .ts} sibling carries the {@code @Component}
 * decorator. A web-components demo's {@code foo.ts} has no such
 * decorator; an Angular component always does.
 *
 * <p>This runs inside MIME resolution — background scans call it for
 * every unclaimed {@code .html} — so the shape is fast-path first:
 * one sibling existence check (cheap stat) before any read, the read
 * capped at a small prefix, and the verdict cached by the sibling's
 * (path, mtime, size) so a scan storm pays the read once. The cache
 * is bounded by wholesale clear — simpler than LRU and correct, since
 * a re-read after a rare clear is just one capped read.
 */
public final class NgTemplates {

    /** The decorator only ever appears this early in a component file. */
    static final int SNIFF_BYTES = 8 * 1024;

    private static final int CACHE_CAP = 512;

    private record Verdict(long mtime, long size, boolean component) {
    }

    private static final Map<String, Verdict> CACHE = new ConcurrentHashMap<>();

    private NgTemplates() {
    }

    /**
     * True when {@code html} has a same-basename {@code .ts} sibling
     * whose head carries {@code @Component}. Null-safe; never throws.
     */
    public static boolean isAngularTemplate(File html) {
        if (html == null) {
            return false;
        }
        String name = html.getName();
        if (!name.endsWith(".html")) {
            return false;
        }
        File dir = html.getParentFile();
        if (dir == null) {
            return false;
        }
        String base = name.substring(0, name.length() - ".html".length());
        if (base.isEmpty()) {
            return false;
        }
        File sibling = new File(dir, base + ".ts");
        if (!sibling.isFile()) {
            return false; // the common non-Angular case: one stat, no read
        }
        return siblingHasComponent(sibling);
    }

    private static boolean siblingHasComponent(File ts) {
        long mtime = ts.lastModified();
        long size = ts.length();
        String key = ts.getAbsolutePath();
        Verdict v = CACHE.get(key);
        if (v != null && v.mtime() == mtime && v.size() == size) {
            return v.component();
        }
        boolean component = readHead(ts).contains("@Component");
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear(); // bounded by wholesale clear — see class javadoc
        }
        CACHE.put(key, new Verdict(mtime, size, component));
        return component;
    }

    private static String readHead(File ts) {
        try (InputStream in = Files.newInputStream(ts.toPath())) {
            return new String(in.readNBytes(SNIFF_BYTES), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return ""; // an unreadable sibling is honestly not evidence
        }
    }

    /** Test seam: forget cached verdicts. */
    static void clearCacheForTest() {
        CACHE.clear();
    }
}
