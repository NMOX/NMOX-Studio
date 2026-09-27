package org.nmox.studio.dbstudio.model;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Where a SQLite connection's file is, for everyone who shares the project
 * (3.4, question 1).
 *
 * <p>{@code .nmoxdb.json} is committed, and until 3.4 the connection
 * dialog's file chooser stored an ABSOLUTE path in it
 * ({@code /Users/alice/code/shop/data/dev.db}), while a relative path was
 * handed to the driver as it stood — resolved against the IDE's working
 * directory, not the project. On a teammate's clone the first answered
 * {@code [SQLITE_CANTOPEN]}, and where the folder happened to exist SQLite
 * quietly created an EMPTY database there. A file inside the project is
 * now stored relative to it, and a relative path resolves against the
 * project directory; an absolute path outside the project, and every
 * path an older version stored, still load exactly as they were.
 */
public final class SqlitePaths {

    private SqlitePaths() {
    }

    /**
     * The path to STORE for {@code path}: project-relative (with {@code /}
     * separators, the same on every machine) when it names a file inside
     * {@code projectDir}, otherwise unchanged. SQLite's own non-file names
     * ({@code :memory:}, {@code file:} URIs) are left alone.
     */
    public static String stored(File projectDir, String path) {
        if (projectDir == null || path == null || path.isBlank() || special(path)) {
            return path;
        }
        try {
            Path file = Path.of(path);
            if (!file.isAbsolute()) {
                return path;
            }
            Path root = projectDir.getAbsoluteFile().toPath().normalize();
            Path normalized = file.normalize();
            if (!normalized.startsWith(root) || normalized.equals(root)) {
                // the same test on REAL paths: a project reached through a
                // symlink (macOS's /var is one, to /private/var) and a
                // chooser answering with the other spelling are still one
                // project, and the file inside it must not be stored as an
                // absolute path that means nothing on a teammate's clone
                root = real(root);
                normalized = real(normalized);
                if (!normalized.startsWith(root) || normalized.equals(root)) {
                    return path;
                }
            }
            return root.relativize(normalized).toString().replace(File.separatorChar, '/');
        } catch (InvalidPathException notAPath) {
            return path;
        }
    }

    /**
     * The real path of {@code p}, or of its nearest existing ancestor with
     * the rest appended (a database the chooser names before it exists);
     * {@code p} itself when nothing resolves.
     */
    private static Path real(Path p) {
        Path missing = null;
        for (Path at = p; at != null; at = at.getParent()) {
            try {
                Path resolved = at.toRealPath();
                return missing == null ? resolved : resolved.resolve(missing);
            } catch (java.io.IOException | SecurityException notThere) {
                Path name = at.getFileName();
                if (name == null) {
                    return p;
                }
                missing = missing == null ? name : name.resolve(missing);
            }
        }
        return p;
    }

    /**
     * The path to OPEN for a stored {@code path}: a relative one resolved
     * against {@code projectDir}, an absolute one (or a SQLite special name)
     * as it stands.
     */
    public static String resolved(File projectDir, String path) {
        if (projectDir == null || path == null || path.isBlank() || special(path)) {
            return path;
        }
        try {
            Path file = Path.of(path);
            if (file.isAbsolute()) {
                return path;
            }
            return projectDir.getAbsoluteFile().toPath().resolve(file).normalize().toString();
        } catch (InvalidPathException notAPath) {
            return path;
        }
    }

    /** The spec to OPEN: a SQLite spec with its path resolved; any other spec as it is. */
    public static ConnectionSpec forOpening(File projectDir, ConnectionSpec spec) {
        if (spec == null || spec.engine() != DbEngine.SQLITE) {
            return spec;
        }
        String resolved = resolved(projectDir, spec.filePath());
        return java.util.Objects.equals(resolved, spec.filePath()) ? spec : spec.withFilePath(resolved);
    }

    /** The spec to STORE: a SQLite spec with a project-relative path where it can be. */
    public static ConnectionSpec forStoring(File projectDir, ConnectionSpec spec) {
        if (spec == null || spec.engine() != DbEngine.SQLITE) {
            return spec;
        }
        String stored = stored(projectDir, spec.filePath());
        return java.util.Objects.equals(stored, spec.filePath()) ? spec : spec.withFilePath(stored);
    }

    private static boolean special(String path) {
        return path.startsWith(":") || path.startsWith("file:");
    }
}
