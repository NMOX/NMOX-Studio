package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The bounded project census every editor-side scan shares (v2.177.0).
 * Born as {@code CssTokens.collectStylesheets} (v1.330.0), promoted when
 * the translation checker became its third consumer — the FilePulse law:
 * promote on the second copy, never grow it. The caps are the whole
 * point: a walk over a project a user just cloned must never read
 * {@code node_modules}, never descend forever, never hold a multi-megabyte
 * bundle in memory, and must SAY when it stopped short, because a
 * report built on half a census ("this key is unused") would accuse a
 * key the walk never reached.
 *
 * <p>Callers run this OFF the EDT; the walk touches disk.
 */
public final class BoundedWalk {

    private BoundedWalk() {
    }

    /** Directories a project scan must never descend into — one home
     *  since ledger 110. The census readers ({@code CssTokens},
     *  {@code CssClasses}, {@code I18nUsage}, {@code Routes}) all inherit
     *  this set, which is why it is {@code public}: a second declaration
     *  beside it is how ledger 110 grew in the first place. */
    public static final Set<String> SKIP_DIRS = org.nmox.studio.core.util.HeavyDirs.NAMES;

    /** A file larger than this is skipped whole (never partially read). */
    public static final long MAX_FILE_BYTES = 256 * 1024;

    /** The deepest directory level read, the root being level 0. */
    public static final int MAX_DEPTH = 6;

    /**
     * The files under {@code root} whose NAME passes {@code accept}, in
     * directory order, at most {@code maxFiles} of them — dot-directories
     * and {@link #SKIP_DIRS} never entered, nothing deeper than
     * {@link #MAX_DEPTH}, nothing over {@link #MAX_FILE_BYTES}. A list
     * exactly {@code maxFiles} long means the cap may have been hit;
     * {@link #complete} says so.
     */
    public static List<File> collect(File root, Predicate<String> accept, int maxFiles) {
        List<File> files = new ArrayList<>();
        if (root == null || !root.isDirectory() || maxFiles <= 0) {
            return files;
        }
        collect(root, accept, maxFiles, files, 0);
        return files;
    }

    /** True when a collect with this cap read everything it was shown. */
    public static boolean complete(List<File> collected, int maxFiles) {
        return collected.size() < maxFiles;
    }

    private static void collect(File dir, Predicate<String> accept, int maxFiles,
            List<File> files, int depth) {
        if (depth > MAX_DEPTH || files.size() >= maxFiles) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (files.size() >= maxFiles) {
                return;
            }
            String name = f.getName();
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                    collect(f, accept, maxFiles, files, depth + 1);
                }
            } else if (accept.test(name) && f.isFile() && f.length() <= MAX_FILE_BYTES) {
                // isFile: a link to a device (/dev/zero reports length 0) is not a file
                files.add(f);
            }
        }
    }
}
