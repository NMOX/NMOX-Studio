package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
 * <p><b>Never a stale answer.</b> The git module REMEMBERS a
 * {@code NOT_SHARABLE} answer for the rest of the session
 * ({@code GitUtils.addNotSharable} fills a static map nothing clears), so
 * one wrong answer hides a file from Commit until the IDE restarts. Every
 * question therefore re-reads the facts it rests on: the work-tree root
 * is looked for again (a {@code git init} a second ago counts) and each
 * {@code .gitignore} in the chain is stat'ed, its RULES reused only while
 * its modification time and size are unchanged (3.2 review: the first cut
 * trusted both for two seconds, and an edit that stopped ignoring
 * {@code dist/} could be answered with the old rules exactly when the
 * git module refreshed).
 *
 * <p><b>A symbolic link is a file.</b> Git records a link as a link and
 * never follows it, so {@code node_modules/} does not match a
 * {@code node_modules} that is a link to a folder; neither does this.
 * An in-tree {@code .gitignore} that is itself a link is not read,
 * because git refuses to read one.
 *
 * <p><b>Cost.</b> The search asks once per folder and once per file: a
 * stat per level up to the work-tree root, a stat per {@code .gitignore}
 * in the chain, and a read only when one changed (bounded, 1 MiB). The
 * rules cache is bounded.
 */
final class WebProjectSharability implements SharabilityQueryImplementation2 {

    private static final Logger LOG = Logger.getLogger(WebProjectSharability.class.getName());

    /** A {@code .gitignore} is hand-written text; a mebibyte is absurd. */
    static final long MAX_IGNORE_BYTES = 1024L * 1024;

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
        // a link is never a directory to git, whatever it points at, and a
        // folder URI's trailing slash says nothing about that
        boolean isDirectory = !Files.isSymbolicLink(path)
                && (uri.getPath() != null && uri.getPath().endsWith("/")
                        || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
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

    // ---- the work-tree root, looked for on every question -----------------

    /**
     * The nearest directory at or above {@code dir} holding a {@code .git}
     * entry (a directory, or the file a worktree or submodule carries), or
     * null outside a work tree. Not cached: a stat per level is cheap, and
     * a remembered "no repository here" would outlive a {@code git init}.
     */
    static Path workTreeRoot(Path dir) {
        Path d = dir;
        for (int i = 0; d != null && i < MAX_ASCENT; i++, d = d.getParent()) {
            if (Files.exists(d.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                return d;
            }
        }
        return null;
    }

    // ---- one ignore file, read once per change -----------------------------

    /**
     * What one read saw: the full modification time (APFS keeps nanoseconds;
     * {@code toMillis()} threw them away and a same-size edit in the same
     * millisecond served the old rules 1,057 times in 2,000 — 4th review),
     * the size, the file's identity (an atomic save is a new inode), and
     * whether the file had SETTLED — its time more than {@link #SETTLE_MS}
     * behind the clock — when it was read. An unsettled read is never
     * reused: a file system counting in whole seconds (HFS+, FAT, many
     * network mounts) can give the next edit the same time.
     */
    private record RulesFact(java.nio.file.attribute.FileTime stamp, long size, Object key,
            boolean settled, GitIgnore rules) {
    }

    /** How far behind the clock a modification time must be before a read of it is reused. */
    static final long SETTLE_MS = 2_000;

    private static final Map<Path, RulesFact> RULES = lru();

    /**
     * The rules one file states: {@link GitIgnore#empty()} when there is no
     * such file (or it is a link git will not read), {@link
     * GitIgnore#unknown()} when it exists but cannot be read. Stat'ed on
     * every call; parsed again only when its time or size moved.
     */
    static GitIgnore rules(Path file) {
        BasicFileAttributes a;
        try {
            a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | RuntimeException e) {
            return GitIgnore.empty(); // absent: states nothing
        }
        if (a.isSymbolicLink()) {
            // git opens an in-tree .gitignore without following links; the
            // repository's own info/exclude is outside the tree and followed
            if (".gitignore".equals(String.valueOf(file.getFileName()))) {
                return GitIgnore.empty();
            }
            try {
                a = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException | RuntimeException e) {
                return GitIgnore.empty();
            }
        }
        if (!a.isRegularFile()) {
            return GitIgnore.empty();
        }
        java.nio.file.attribute.FileTime stamp = a.lastModifiedTime();
        long size = a.size();
        Object key = a.fileKey();
        RulesFact known;
        synchronized (RULES) {
            known = RULES.get(file);
        }
        if (known != null && known.settled() && known.stamp().equals(stamp)
                && known.size() == size && java.util.Objects.equals(known.key(), key)) {
            return known.rules();
        }
        boolean settled = System.currentTimeMillis() - stamp.toMillis() > SETTLE_MS;
        GitIgnore parsed = read(file);
        synchronized (RULES) {
            RULES.put(file, new RulesFact(stamp, size, key, settled, parsed));
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
        // unread is not "no rules": what it would re-include is unknown too
        return GitIgnore.unknown();
    }

    /** Test seam: forget every cached fact. */
    static void forgetForTest() {
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
