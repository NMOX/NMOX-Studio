package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The first question after a failure: "what changed since it last
 * worked?" Answered by mtime sweep - no git required, dependency
 * directories skipped, capped so a mass-change never floods the UI.
 */
public final class ChangedSince {

    private static final Set<String> SKIP = Set.of(
            "node_modules", ".git", "dist", "build", "target", "out",
            "vendor", "coverage", "__pycache__", ".venv", ".nmox");
    public static final int CAP = 25;

    private ChangedSince() {
    }

    /** Files under root modified after the timestamp, newest first, capped. */
    public static List<File> scan(File root, long sinceMillis) {
        List<File> changed = new ArrayList<>();
        walk(root, sinceMillis, changed, 0);
        changed.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return changed.size() > CAP ? changed.subList(0, CAP) : changed;
    }

    private static void walk(File dir, long since, List<File> out, int depth) {
        if (depth > 8 || out.size() > CAP * 4) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") && f.isDirectory()) {
                continue;
            }
            if (f.isDirectory()) {
                if (!SKIP.contains(name)) {
                    walk(f, since, out, depth + 1);
                }
            } else if (f.lastModified() > since) {
                out.add(f);
            }
        }
    }
}
