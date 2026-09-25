package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.queries.SharabilityQuery.Sharability;
import org.netbeans.spi.queries.SharabilityQueryImplementation2;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.GitIgnore;
import org.nmox.studio.core.util.HeavyDirs;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.BaseUtilities;

/**
 * A WebProject's answer to "is this file part of the project a person
 * shares?" — {@code NOT_SHARABLE} for what the repository ignores, so
 * Edit ▸ Find in Projects stops listing every copy of a function name in
 * {@code node_modules} and {@code dist/} (3.2).
 *
 * <p><b>Why this query and not another.</b> Read from the RELEASE310
 * bytecode: the Find dialog's project scopes build their walk with the
 * platform's default filters, and {@code SharabilityFilter} answers
 * {@code DO_NOT_TRAVERSE} for a folder {@code SharabilityQuery} calls
 * {@code NOT_SHARABLE} — unless the dialog's "Search in Generated
 * Sources" is ticked, which drops that filter and is therefore the way
 * back to searching everything. The other default filter reads
 * {@code VisibilityQuery}, and that one HIDES a folder from every tree in
 * the product: {@code node_modules} vanishing from Project Studio would be
 * a regression, so visibility is deliberately not touched.
 *
 * <p><b>Never contradict git.</b> The platform's git module treats
 * {@code NOT_SHARABLE} as ignored ({@code GitUtils.isIgnored}) and, with
 * its default "auto-ignore" preference, writes a folder it thinks ignored
 * into {@code .gitignore} unless git already ignores it
 * ({@code IgnoreAction.filterFolders} skips {@code STATUS_IGNORED}). So
 * inside a git work tree the answer is exactly what the repository's own
 * files say — the {@code .gitignore} chain from the work tree's root down,
 * {@code .git/info/exclude} beneath it, and {@code .git} itself — and a
 * {@code node_modules} a repository does NOT ignore stays searched,
 * because git tracks it there. Only OUTSIDE a work tree, where no git
 * module is watching, do the product's {@link HeavyDirs} names join in —
 * the names every walk in the product already refuses to descend into.
 * Git's global {@code core.excludesFile} is not read: a path only it
 * ignores is still searched, the under-matching side (see
 * {@link GitIgnore} for why every doubt errs that way).
 *
 * <p><b>Cost.</b> The search asks once per folder and once per file. Each
 * {@code .gitignore} is read at most once per change (bounded, 1 MiB),
 * a file's and a directory's facts are trusted for {@link #FRESH_MS}
 * before they are stat'ed again, and both caches are bounded.
 */
final class WebProjectSharability implements SharabilityQueryImplementation2 {

    private static final Logger LOG = Logger.getLogger(WebProjectSharability.class.getName());

    /** A {@code .gitignore} is hand-written text; a mebibyte is absurd. */
    static final long MAX_IGNORE_BYTES = 1024L * 1024;

    /** How long a stat'ed fact is trusted before it is asked again. */
    static final long FRESH_MS = 2_000;

    /** How far up the tree the work-tree root is looked for. */
    private static final int MAX_ASCENT = 64;

    private static final int CACHE_ENTRIES = 2_048;

    private final FileObject projectDir;

    WebProjectSharability(FileObject projectDir) {
        this.projectDir = projectDir;
    }

    @Override
    public Sharability getSharability(URI uri) {
        if (uri == null || !"file".equals(uri.getScheme())) {
            return Sharability.UNKNOWN;
        }
        File projectFile = FileUtil.toFile(projectDir);
        if (projectFile == null) {
            return Sharability.UNKNOWN;
        }
        Path path;
        try {
            path = BaseUtilities.toFile(uri).toPath().toAbsolutePath().normalize();
        } catch (IllegalArgumentException e) { // incl. InvalidPathException
            return Sharability.UNKNOWN;
        }
        boolean isDirectory = uri.getPath() != null && uri.getPath().endsWith("/")
                || path.toFile().isDirectory();
        return ignored(projectFile.toPath().toAbsolutePath().normalize(), path, isDirectory)
                ? Sharability.NOT_SHARABLE : Sharability.UNKNOWN;
    }

    /**
     * The decision, with the platform left out: is {@code path} (inside or
     * beneath {@code project}) something the repository — or, with no
     * repository, the product — keeps out of what a person shares?
     */
    static boolean ignored(Path project, Path path, boolean isDirectory) {
        Path root = workTreeRoot(isDirectory ? path : path.getParent());
        Path base = root != null ? root : project;
        if (!path.startsWith(base) || path.equals(base)) {
            return false;
        }
        String rel = base.relativize(path).toString().replace(File.separatorChar, '/');
        for (String seg : rel.split("/")) {
            if (".git".equals(seg)) {
                return true; // git's own store: never part of the work
            }
        }
        if (root == null && path.startsWith(project) && !path.equals(project)) {
            for (String seg : project.relativize(path).toString()
                    .replace(File.separatorChar, '/').split("/")) {
                if (HeavyDirs.isHeavy(seg)) {
                    return true;
                }
            }
        }
        GitIgnore exclude = root == null ? GitIgnore.empty()
                : rules(root.resolve(".git").resolve("info").resolve("exclude"));
        Map<String, GitIgnore> seen = new HashMap<>();
        return GitIgnore.isIgnored(rel, isDirectory, exclude,
                dir -> seen.computeIfAbsent(dir, d -> rules(
                        (d.isEmpty() ? base : base.resolve(d)).resolve(".gitignore"))));
    }

    // ---- the work-tree root, by directory ----------------------------------

    private record RootFact(Path root, long checkedAt) {
    }

    private static final Map<Path, RootFact> ROOTS = lru();

    /**
     * The nearest directory at or above {@code dir} holding a {@code .git}
     * entry (a directory, or the file a worktree or submodule carries), or
     * null outside a work tree.
     */
    static Path workTreeRoot(Path dir) {
        if (dir == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        synchronized (ROOTS) {
            RootFact f = ROOTS.get(dir);
            if (f != null && now - f.checkedAt() < FRESH_MS) {
                return f.root();
            }
        }
        Path found = null;
        Path d = dir;
        for (int i = 0; d != null && i < MAX_ASCENT; i++, d = d.getParent()) {
            if (d.resolve(".git").toFile().exists()) {
                found = d;
                break;
            }
        }
        synchronized (ROOTS) {
            ROOTS.put(dir, new RootFact(found, now));
        }
        return found;
    }

    // ---- one ignore file, read once per change -----------------------------

    private record RulesFact(long stamp, long size, long checkedAt, GitIgnore rules) {
    }

    private static final Map<Path, RulesFact> RULES = lru();

    /** The rules one file states; {@link GitIgnore#empty()} when it has none. */
    static GitIgnore rules(Path file) {
        long now = System.currentTimeMillis();
        RulesFact known;
        synchronized (RULES) {
            known = RULES.get(file);
        }
        if (known != null && now - known.checkedAt() < FRESH_MS) {
            return known.rules();
        }
        File f = file.toFile();
        long stamp = f.lastModified();
        long size = f.length();
        GitIgnore parsed;
        if (stamp == 0L || !f.isFile()) {
            parsed = GitIgnore.empty();
        } else if (known != null && known.stamp() == stamp && known.size() == size) {
            parsed = known.rules();
        } else {
            parsed = read(file);
        }
        synchronized (RULES) {
            RULES.put(file, new RulesFact(stamp, size, now, parsed));
        }
        return parsed;
    }

    private static GitIgnore read(Path file) {
        try {
            return GitIgnore.parse(BoundedReads.read(file, MAX_IGNORE_BYTES));
        } catch (BoundedReads.TooLarge tl) {
            // a refusal speaks: this file's rules are not applied, so a
            // search lists what it would have skipped — said once per change
            LOG.log(Level.INFO, "{0} not read for Find in Projects: {1}",
                    new Object[]{file, tl.getMessage()});
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "{0} not read for Find in Projects: {1}",
                    new Object[]{file, e.toString()});
        }
        return GitIgnore.empty();
    }

    /** Test seam: forget every cached fact. */
    static void forgetForTest() {
        synchronized (ROOTS) {
            ROOTS.clear();
        }
        synchronized (RULES) {
            RULES.clear();
        }
    }

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_ENTRIES;
            }
        };
    }
}
