package org.nmox.studio.editor.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The discovery half of the Tests window (competitive-lens R3: "tests
 * are invisible until they run"): every test declaration the focused
 * runner already knows how to RUN, found by scanning the aimed
 * project's files with the same per-mime patterns
 * {@link RunFocusedTestAction} anchors on — one vocabulary, two
 * surfaces, so a framework gains the window the day it gains the
 * runner, and the window can never list a test the runner cannot run.
 * Bounded by the ProjectSymbols laws: heavy dirs skipped, breadth cap
 * that SPEAKS via {@link #wasTruncated} (a silent partial listing
 * reads as a complete one), depth cap against symlink cycles, per-file
 * size ceiling, and a path+mtime+size cache so refreshes re-read only
 * what changed.
 */
public final class TestIndex {

    /** One discovered test. Line is 1-based — the convention the
     *  runners themselves speak ({@code mix test path:LINE}). */
    public record DiscoveredTest(String name, Path file, int line) {
    }

    public static final int MAX_FILES = 2_000;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    static final int MAX_DEPTH = 32;

    static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage", "target",
            "out", "vendor", ".next", ".nuxt", ".svelte-kit", "__pycache__");

    private record CacheKey(long mtime, long size) {
    }

    private final Map<Path, CacheKey> seen = new LinkedHashMap<>();
    private final Map<Path, List<DiscoveredTest>> byFile = new LinkedHashMap<>();
    private boolean truncated;

    /**
     * Rebuilds the index, re-reading only changed files; returns files
     * that hold at least one test, in stable walk order. {@code mimeOf}
     * is the caller's resolver; a mime with no focused-test pattern
     * contributes nothing — the honest boundary.
     */
    public synchronized Map<Path, List<DiscoveredTest>> refresh(Path root,
            Function<Path, String> mimeOf, Predicate<Void> cancelled) {
        truncated = false;
        List<Path> candidates = new ArrayList<>();
        collect(root, candidates, 0, cancelled);
        seen.keySet().retainAll(Set.copyOf(candidates));
        byFile.keySet().retainAll(Set.copyOf(candidates));
        for (Path file : candidates) {
            if (cancelled.test(null)) {
                break;
            }
            index(file, mimeOf);
        }
        Map<Path, List<DiscoveredTest>> out = new LinkedHashMap<>();
        for (Path file : candidates) {
            List<DiscoveredTest> tests = byFile.get(file);
            if (tests != null && !tests.isEmpty()) {
                out.put(file, tests);
            }
        }
        return out;
    }

    /** True when a cap clipped the walk — the window must say so. */
    public synchronized boolean wasTruncated() {
        return truncated;
    }

    private void collect(Path dir, List<Path> into, int depth,
            Predicate<Void> cancelled) {
        if (depth > MAX_DEPTH) {
            // a symlink cycle or a pathological tree: stop AND say so —
            // same law as the breadth cap
            truncated = true;
            return;
        }
        if (into.size() >= MAX_FILES || cancelled.test(null)) {
            return;
        }
        List<Path> children;
        try (var stream = Files.list(dir)) {
            children = stream.sorted().toList();
        } catch (IOException e) {
            return; // an unreadable dir contributes nothing, quietly
        }
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (Files.isDirectory(child)) {
                if (!name.startsWith(".") && !SKIP_DIRS.contains(name)) {
                    collect(child, into, depth + 1, cancelled);
                }
            } else if (!name.startsWith(".")) {
                if (into.size() >= MAX_FILES) {
                    truncated = true;
                    return;
                }
                into.add(child);
            }
        }
    }

    private void index(Path file, Function<Path, String> mimeOf) {
        long mtime;
        long size;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException e) {
            seen.remove(file);
            byFile.remove(file);
            return;
        }
        CacheKey key = new CacheKey(mtime, size);
        if (key.equals(seen.get(file))) {
            return;
        }
        seen.put(file, key);
        if (size > MAX_FILE_BYTES) {
            byFile.put(file, List.of());
            return;
        }
        String mime = mimeOf.apply(file);
        Pattern pattern = mime == null ? null
                : RunFocusedTestAction.patternFor(mime);
        if (pattern == null) {
            byFile.put(file, List.of());
            return;
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException unreadable) {
            byFile.put(file, List.of()); // binary bytes are not a test home
            return;
        }
        List<DiscoveredTest> tests = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        int line = 1;
        int scanned = 0; // running newline scan — one pass, not O(n²)
        while (m.find()) {
            for (int i = scanned; i < m.start(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            scanned = m.start();
            tests.add(new DiscoveredTest(m.group(1), file, line));
        }
        byFile.put(file, List.copyOf(tests));
    }
}
