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
        List<Path> entries = children(dir);
        for (int i = 0; i < entries.size(); i++) {
            Path p = entries.get(i);
            boolean last = i == entries.size() - 1;
            boolean isDir = Files.isDirectory(p) && !Files.isSymbolicLink(p);
            String name = p.getFileName().toString();
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

    /** Directories first, then files, each case-folded; unreadable dirs read as empty. */
    static List<Path> children(Path dir) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            // the IDE's own workspace files (.nmoxrack.json and its siblings) are the product's,
            // not the project's — README noise the walk found; left out, not counted as elided
            s.filter(p -> !IdeWorkspaceFiles.isOwn(p.getFileName().toString())).forEach(out::add);
        } catch (IOException | RuntimeException ex) {
            return out;
        }
        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT));
        out.sort(Comparator.<Path, Boolean>comparing(p -> !(Files.isDirectory(p) && !Files.isSymbolicLink(p))).thenComparing(byName));
        return out;
    }
}
