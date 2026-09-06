package org.nmox.studio.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A project's layout as the box-drawing tree a README shows (v2.87.0, the
 * developer evangelist's post motion): directories first, case-folded
 * order, {@link HeavyDirs} named but never entered ({@code node_modules/ …}),
 * and two caps that keep a monorepo from becoming a wall — a depth
 * ({@code name/ …} at the floor) and an entry count, with the elided
 * remainder counted honestly rather than dropped. Every read is bounded:
 * the walk never follows a symlinked directory (a loop would be a
 * bounded-read violation) and stops the moment the entry cap is hit.
 * The IDE's own workspace files ({@link IdeWorkspaceFiles}) are left out:
 * they are the product's, not the project's, and a README tree that
 * listed them would be edited by hand every time (the walk's find).
 */
public final class TreeText {

    /** The tree's text and how many entries the caps left out (0 = complete). */
    public record Result(String text, int elided) {
    }

    /** The most entries one directory contributes before the rest are counted, not listed (the bounded-read law). */
    static final int LIST_CAP = 2000;

    private TreeText() {
    }

    public static Result render(Path root, int maxDepth, int maxEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append(root.getFileName() == null ? root.toString() : root.getFileName().toString()).append("/\n");
        int[] budget = {maxEntries};
        int[] elided = {0};
        walk(root, "", 1, maxDepth, budget, elided, sb);
        if (elided[0] > 0) {
            sb.append("… (").append(Plural.of(elided[0], "more entry", "more entries")).append(" not shown)\n");
        }
        return new Result(sb.toString(), elided[0]);
    }

    private static void walk(Path dir, String prefix, int depth, int maxDepth, int[] budget, int[] elided, StringBuilder sb) {
        Listing listing = children(dir);
        List<Path> entries = listing.entries();
        elided[0] += listing.beyondCap();
        for (int i = 0; i < entries.size(); i++) {
            Path p = entries.get(i);
            boolean last = i == entries.size() - 1;
            boolean isDir = Files.isDirectory(p) && !Files.isSymbolicLink(p);
            String name = safeName(p.getFileName().toString());
            if (budget[0] <= 0) {
                elided[0] += entries.size() - i;
                return;
            }
            budget[0]--;
            sb.append(prefix).append(last ? "└── " : "├── ").append(name);
            if (isDir) {
                sb.append('/');
                if (HeavyDirs.isHeavy(name) || depth >= maxDepth) {
                    sb.append(" …");
                    sb.append('\n');
                    continue;
                }
                sb.append('\n');
                walk(p, prefix + (last ? "    " : "│   "), depth + 1, maxDepth, budget, elided, sb);
            } else {
                sb.append('\n');
            }
        }
    }

    /**
     * A file name is external text and a tree is line-structured: a name
     * carrying a newline (legal on every Unix filesystem) would forge
     * extra tree lines, and other control characters would corrupt the
     * box drawing — each becomes {@code ?}. The hostile-input lens.
     */
    static String safeName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> sb.appendCodePoint(Character.isISOControl(cp) ? '?' : cp));
        return sb.toString();
    }

    /** One directory's listing: at most {@link #LIST_CAP} entries kept, the rest counted. */
    record Listing(List<Path> entries, int beyondCap) {
    }

    /**
     * Directories first, then files, each case-folded; unreadable dirs read
     * as empty. The listing is bounded: past {@link #LIST_CAP} entries the
     * rest are only counted (names iterated, never held or stat'ed), so a
     * directory of a hundred thousand files costs a bounded list.
     */
    static Listing children(Path dir) {
        List<Path> out = new ArrayList<>();
        int[] beyond = {0};
        try (Stream<Path> s = Files.list(dir)) {
            // the IDE's own workspace files (.nmoxrack.json and its siblings) are the product's,
            // not the project's — README noise the walk found; left out, not counted as elided
            s.filter(p -> !IdeWorkspaceFiles.isOwn(p.getFileName().toString())).forEach(p -> {
                if (out.size() < LIST_CAP) {
                    out.add(p);
                } else {
                    beyond[0]++;
                }
            });
        } catch (IOException | RuntimeException ex) {
            return new Listing(out, beyond[0]);
        }
        // one stat per entry, never one per comparison: a comparator that calls
        // Files.isDirectory sorts a 2,000-entry directory with ~22,000 stats,
        // which on a network mount is the bounded-read spirit unmet
        java.util.Map<Path, Boolean> isDir = new java.util.HashMap<>();
        for (Path p : out) {
            isDir.put(p, Files.isDirectory(p) && !Files.isSymbolicLink(p));
        }
        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT));
        out.sort(Comparator.<Path, Boolean>comparing(p -> !isDir.get(p)).thenComparing(byName));
        return new Listing(out, beyond[0]);
    }
}
