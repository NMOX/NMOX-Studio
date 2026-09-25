package org.nmox.studio.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/**
 * The workspace packages a package depends on (3.3, the big-project
 * survey). {@link ProjectRoot} stops at the nearest {@code package.json},
 * which in a monorepo is the file's own package — right for everything
 * that package declares, and blind to the one place a monorepo keeps its
 * design tokens and shared styles: a sibling package the file's package
 * lists as a dependency ({@code "@acme/tokens": "workspace:*"}). The
 * design scans read the file's own package first and then these, so a
 * token declared in {@code packages/tokens} completes and ⌘-clicks from
 * {@code packages/web}, and one declared in an unrelated sibling does not.
 *
 * <p>Following the declared dependencies rather than every sibling is the
 * point: the dependency list is the package's own statement of what it
 * uses, so the answer is exactly what the build would see, and it stays
 * a handful of small packages on a repository of two hundred.
 *
 * <p>A dependency is found where the package manager put it — a link in
 * {@code node_modules} (npm, yarn and pnpm all link workspace packages) —
 * and otherwise, before an install, among the packages the workspace
 * declares. Either way the directory must lie inside the workspace and
 * outside every {@code node_modules}: a third-party install is somebody
 * else's code and a heavy directory, and a link that leaves the
 * repository is a path a clone chose. Bounded throughout: a climb of
 * {@value #MAX_CLIMB} levels that stops at a repository's own {@code .git},
 * {@value #MAX_DEPENDENCIES} dependencies. Off the EDT; this touches disk.
 */
public final class WorkspaceDependencies {

    /** Levels above the package the workspace root may be. */
    static final int MAX_CLIMB = 6;
    /** Workspace packages followed per package — the first ones declared. */
    static final int MAX_DEPENDENCIES = 8;
    /** How long an answer is reused before the manifests are read again. */
    static final long FRESH_MS = 10_000;


    private record Entry(long stamp, long at, List<File> dirs) {
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private WorkspaceDependencies() {
    }

    /**
     * The directories of the workspace packages {@code pkg} depends on, in
     * the order its {@code package.json} declares them; empty when {@code pkg}
     * is not a package inside a workspace.
     */
    public static List<File> of(File pkg) {
        if (pkg == null || !new File(pkg, "package.json").isFile()) {
            return List.of();
        }
        File manifest = new File(pkg, "package.json");
        long stamp = manifest.lastModified() ^ (manifest.length() << 32);
        long now = System.currentTimeMillis();
        String key = pkg.getAbsolutePath();
        Entry e = CACHE.get(key);
        if (e != null && e.stamp() == stamp && now - e.at() < FRESH_MS) {
            return e.dirs();
        }
        List<File> dirs = List.copyOf(resolve(pkg));
        if (CACHE.size() > 256) {
            CACHE.clear();   // a bound, not a policy: the answer is cheap to rebuild
        }
        CACHE.put(key, new Entry(stamp, now, dirs));
        return dirs;
    }

    /** {@code root} followed by the workspace packages it depends on: every package a design scan reads. */
    public static List<File> withDependencies(File root) {
        if (root == null) {
            return List.of();
        }
        List<File> all = new ArrayList<>();
        all.add(root);
        all.addAll(of(root));
        return all;
    }

    static List<File> resolve(File pkg) {
        File workspace = workspaceAbove(pkg);
        if (workspace == null) {
            return List.of();
        }
        Path wsReal;
        try {
            wsReal = workspace.toPath().toRealPath();
        } catch (IOException ex) {
            return List.of();
        }
        Set<File> out = new LinkedHashSet<>();
        Map<String, File> declared = null;   // read only when a link is missing
        for (String name : dependencyNames(pkg)) {
            if (out.size() >= MAX_DEPENDENCIES) {
                break;
            }
            File dir = linked(pkg, workspace, name, wsReal);
            if (dir == null) {
                if (declared == null) {
                    declared = byName(workspace);
                }
                File candidate = declared.get(name);
                dir = candidate == null ? null : inside(candidate.toPath(), wsReal);
            }
            if (dir != null && !dir.equals(pkg)) {
                out.add(dir);
            }
        }
        return new ArrayList<>(out);
    }

    /** The workspace root above {@code pkg}: a manifest declaring workspaces, never past a repository's root. */
    static File workspaceAbove(File pkg) {
        if (new File(pkg, ".git").exists()) {
            return null;   // the package is its own repository
        }
        File cursor = pkg.getParentFile();
        for (int up = 0; cursor != null && up < MAX_CLIMB; up++, cursor = cursor.getParentFile()) {
            if (declaresWorkspaces(cursor)) {
                return cursor;
            }
            if (new File(cursor, ".git").exists()) {
                return null;   // the repository ends here
            }
        }
        return null;
    }

    private static boolean declaresWorkspaces(File dir) {
        if (new File(dir, "pnpm-workspace.yaml").isFile()) {
            return true;
        }
        JSONObject o = manifest(new File(dir, "package.json"));
        return o != null && o.has("workspaces");
    }

    /** Every dependency name the package declares, runtime first, in file order. */
    static List<String> dependencyNames(File pkg) {
        JSONObject o = manifest(new File(pkg, "package.json"));
        List<String> names = new ArrayList<>();
        if (o == null) {
            return names;
        }
        for (String section : List.of("dependencies", "devDependencies", "peerDependencies")) {
            JSONObject deps = o.optJSONObject(section);
            if (deps == null) {
                continue;
            }
            for (String name : deps.keySet()) {
                if (isPackageName(name) && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * An npm package name, scoped or not — {@code [@scope/]name}, each
     * part starting with a lower-case letter or digit and continuing in
     * {@code [a-z0-9._~-]}, at most 214 characters (npm's own limit) — so
     * nothing that could walk a path ({@code ..}, a separator) is a name.
     * A hand scanner, because the ReDoS detector reads the regex's two
     * optional-then-repeated groups as a hazard.
     */
    static boolean isPackageName(String name) {
        if (name == null || name.isEmpty() || name.length() > 214) {
            return false;
        }
        String rest = name;
        if (rest.charAt(0) == '@') {
            int slash = rest.indexOf('/');
            if (slash < 0 || !isPart(rest.substring(1, slash))) {
                return false;
            }
            rest = rest.substring(slash + 1);
        }
        return isPart(rest);
    }

    private static boolean isPart(String part) {
        if (part.isEmpty() || !isLowerAlnum(part.charAt(0))) {
            return false;
        }
        for (int i = 1; i < part.length(); i++) {
            char c = part.charAt(i);
            if (!isLowerAlnum(c) && c != '.' && c != '_' && c != '~' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowerAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** Where the package manager linked {@code name}, if that is a workspace package. */
    private static File linked(File pkg, File workspace, String name, Path wsReal) {
        for (File base : List.of(pkg, workspace)) {
            Path link = new File(new File(base, "node_modules"), name).toPath();
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    File dir = inside(link.toRealPath(), wsReal);
                    if (dir != null) {
                        return dir;
                    }
                } catch (IOException ex) {
                    // a dangling link names nothing
                }
            }
        }
        return null;
    }

    /**
     * {@code dir} when it is a package inside the workspace and outside
     * every {@code node_modules}; null otherwise.
     */
    static File inside(Path dir, Path wsReal) {
        Path real;
        try {
            real = dir.toRealPath();
        } catch (IOException ex) {
            return null;
        }
        if (!real.startsWith(wsReal) || real.equals(wsReal)) {
            return null;
        }
        for (Path part : wsReal.relativize(real)) {
            if ("node_modules".equals(part.toString())) {
                return null;
            }
        }
        return Files.isRegularFile(real.resolve("package.json")) ? real.toFile() : null;
    }

    /**
     * The workspace's packages by declared name, as the rack's WAYPOINT
     * reads them (its first {@code Workspaces.MAX_PACKAGES}); only asked
     * when a dependency has no link yet, which is before an install.
     */
    private static Map<String, File> byName(File workspace) {
        return org.nmox.studio.rack.devices.Workspaces.packages(workspace);
    }

    private static JSONObject manifest(File f) {
        if (!f.isFile()) {
            return null;
        }
        try {
            return new JSONObject(org.nmox.studio.core.util.BoundedReads.read(f.toPath()));
        } catch (IOException | RuntimeException ex) {
            return null;   // an unreadable manifest declares nothing
        }
    }
}
