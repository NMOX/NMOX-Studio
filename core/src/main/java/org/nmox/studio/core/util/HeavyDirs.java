package org.nmox.studio.core.util;

import java.util.Set;

/**
 * The directories no walk descends into: generated or vendored trees
 * whose thousands of files would swamp a tree, a search or a click. One
 * home (promoted from Project Studio's file tree in v2.87.0 the moment a
 * second reader — the Markdown tree — arrived) so the file tree and every
 * later walk elide the same names.
 */
public final class HeavyDirs {

    public static final Set<String> NAMES = Set.of("node_modules", ".git", "dist", "build", "coverage");

    private HeavyDirs() {
    }

    public static boolean isHeavy(String dirName) {
        return NAMES.contains(dirName);
    }
}
