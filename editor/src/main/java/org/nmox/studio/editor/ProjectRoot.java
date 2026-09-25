package org.nmox.studio.editor;

import java.io.File;
import javax.swing.text.Document;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Where a file's project starts, for every editor surface that resolves
 * something project-wide: class completion and the class jumps, design
 * tokens, env keys, import maps, Rename Class, the Angular selector
 * jump.
 *
 * <p>The rule is a bounded climb from the file's own directory looking
 * for a project marker — {@code package.json}, {@code angular.json}, or
 * a {@code .git} entry — falling back to that directory when the walk
 * finds none, so a scratch file outside any project still resolves
 * against its neighbours instead of returning null.
 *
 * <p>It lives here because the marker triple IS the definition of
 * "project root" for those surfaces, and it had eleven byte-identical
 * private copies plus a twelfth with its own depth. They all agreed,
 * which is exactly the v2.131.0 defect: the second home, not the
 * disagreement. Adding a marker — a {@code deno.json} workspace, a
 * {@code pnpm-workspace.yaml} monorepo — meant twelve edits, and the
 * one that was missed would not fail: it would silently resolve
 * against the wrong directory and offer a stranger's classes.
 * {@code ProjectRootSingleHomeTest} fails the build on a thirteenth.
 */
public final class ProjectRoot {

    /**
     * How far the climb goes by default. Six levels covers the deepest
     * ordinary source layout ({@code src/app/feature/sub/…}) without
     * walking a user's whole home directory when a file sits outside a
     * project — the v1.33.1 hazard, which is why this is bounded at all.
     */
    public static final int DEFAULT_DEPTH = 6;

    private ProjectRoot() {
    }

    /** The project root for the file behind {@code doc}, or null when it has none on disk. */
    public static File of(Document doc) {
        return of(org.nmox.studio.core.util.EditedFile.of(doc));
    }

    /** The project root for {@code fo}, or null when it is not a real file. */
    public static File of(FileObject fo) {
        File f = fo == null ? null : FileUtil.toFile(fo);
        return f == null ? null : above(f.getParentFile());
    }

    /** The project root at or above {@code dir}, or {@code dir} itself when no marker is found. */
    public static File above(File dir) {
        return above(dir, DEFAULT_DEPTH);
    }

    /**
     * The project root at or above {@code dir} within {@code depth}
     * levels. Angular's selector jump climbs further (a component sits
     * deeper than a stylesheet), which is the only reason the bound is
     * a parameter rather than a constant.
     */
    public static File above(File dir, int depth) {
        File cursor = dir;
        for (int up = 0; cursor != null && up < depth; up++, cursor = cursor.getParentFile()) {
            if (isRoot(cursor)) {
                return cursor;
            }
        }
        return dir;
    }

    /** The marker triple, in one place: change a project's definition here or nowhere. */
    private static boolean isRoot(File dir) {
        return new File(dir, "package.json").isFile()
                || new File(dir, "angular.json").isFile()
                || new File(dir, ".git").exists();
    }
}
