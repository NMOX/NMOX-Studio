package org.nmox.studio.editor.symbols;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.nmox.studio.editor.outline.OutlineKind;
import org.nmox.studio.editor.outline.OutlineModel;

/**
 * The project-wide symbol index behind Go to Symbol (and, through the
 * platform's own bridge, ⌘I): every name the Navigator outline can see
 * in any file of the aimed project, found by typing it — the jump from
 * a symbol you REMEMBER, where Go to Declaration only jumps from a
 * usage you can already see (competitive-lens R2).
 *
 * <p>Pure and bounded: a walk capped in breadth ({@link #MAX_FILES})
 * and per-file size ({@link #MAX_FILE_BYTES}), heavy directories
 * skipped by the same list the file tree renders childless, and a
 * per-file cache keyed on path+mtime+size so re-queries only re-read
 * what changed. The mime is resolved by the caller (the provider asks
 * the platform's resolvers); extraction is {@link OutlineModel} — one
 * extractor family for the Navigator, the symbol dialog, and ⌘I, so a
 * language gains all three surfaces the day it gains an outline.
 */
public final class ProjectSymbols {

    /** One symbol: where a name lives. Line is 0-based like the outline's. */
    public record Symbol(String name, String detail, OutlineKind kind,
            Path file, int line) {
    }

    /** Breadth cap: past this many candidate files the walk stops and
     *  says so — a silent partial index would read as a complete one. */
    public static final int MAX_FILES = 2_000;

    /** Per-file ceiling: a generated bundle is not a place symbols live. */
    public static final int MAX_FILE_BYTES = 256 * 1024;

    /** The heavy dirs the file tree already refuses to expand. */
    static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage", "target",
            "out", "vendor", ".next", ".nuxt", ".svelte-kit", "__pycache__");

    private record CacheKey(Path file, long mtime, long size) {
    }

    private final Map<Path, CacheKey> seen = new HashMap<>();
    private final Map<Path, List<Symbol>> byFile = new HashMap<>();
    private boolean truncated;

    /**
     * Rebuilds the index for {@code root}, re-reading only files whose
     * mtime or size moved since the last pass. {@code mimeOf} is the
     * caller's resolver (the provider hands in the platform's);
     * {@code cancelled} lets the dialog abandon a slow walk mid-flight.
     * Returns every symbol currently known, stable-ordered by file.
     */
    public synchronized List<Symbol> refresh(Path root,
            java.util.function.Function<Path, String> mimeOf,
            Predicate<Void> cancelled) {
        truncated = false;
        List<Path> candidates = new ArrayList<>();
        collect(root, candidates, cancelled);
        // drop cache entries for files that vanished
        seen.keySet().retainAll(Set.copyOf(candidates));
        byFile.keySet().retainAll(Set.copyOf(candidates));
        for (Path file : candidates) {
            if (cancelled.test(null)) {
                break;
            }
            index(file, mimeOf);
        }
        List<Symbol> all = new ArrayList<>();
        candidates.forEach(f -> all.addAll(byFile.getOrDefault(f, List.of())));
        return all;
    }

    /** True when the last refresh hit {@link #MAX_FILES} — the caller
     *  must say the index is partial, never let it read as complete. */
    public synchronized boolean wasTruncated() {
        return truncated;
    }

    private void collect(Path dir, List<Path> into, Predicate<Void> cancelled) {
        if (into.size() >= MAX_FILES) {
            truncated = true;
            return;
        }
        if (cancelled.test(null)) {
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
                    collect(child, into, cancelled);
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

    private void index(Path file, java.util.function.Function<Path, String> mimeOf) {
        long mtime;
        long size;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException e) {
            byFile.remove(file);
            seen.remove(file);
            return;
        }
        CacheKey key = new CacheKey(file, mtime, size);
        if (key.equals(seen.get(file))) {
            return; // unchanged since last pass — the cache stands
        }
        seen.put(file, key);
        if (size > MAX_FILE_BYTES) {
            byFile.put(file, List.of()); // over-cap file contributes nothing
            return;
        }
        String mime = mimeOf.apply(file);
        if (mime == null || "content/unknown".equals(mime)) {
            byFile.put(file, List.of());
            return;
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException unreadable) {
            // binary bytes throw MalformedInputException — not a symbol home
            byFile.put(file, List.of());
            return;
        }
        List<Symbol> symbols = new ArrayList<>();
        for (OutlineModel.Item item : OutlineModel.extract(mime, text)) {
            symbols.add(new Symbol(item.name(), item.detail(), item.kind(),
                    file, item.line()));
        }
        byFile.put(file, List.copyOf(symbols));
    }
}
