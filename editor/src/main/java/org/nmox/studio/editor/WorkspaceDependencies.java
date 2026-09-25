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

    /** Packages the route jump reads beyond the file's own: at most this many. */
    static final int MAX_SERVERS = 8;
    /** Workspace manifests the route jump enumerates, once per click. */
    public static final int MAX_WORKSPACE_PACKAGES = 512;
    /** The server frameworks whose route registrations the route jump reads ({@code Routes.SERVER_ROUTE}'s shape). */
    static final List<String> SERVER_FRAMEWORKS = List.of("express", "fastify", "koa", "@koa/router", "hono");

    /**
     * The workspace's server packages, for the fetch-path → route jump
     * (3.3): a {@code web} package's {@code fetch('/api/users')} is served
     * by an {@code api} package it does not depend on, so here the
     * question is which packages declare a server framework. {@code
     * complete} is false when the workspace has more packages than
     * {@value #MAX_WORKSPACE_PACKAGES} or more server packages than
     * {@value #MAX_SERVERS}, so a miss can say it did not read them all.
     * A click-time query, uncached; off the EDT.
     */
    public record Servers(List<File> dirs, boolean complete) {
    }

    public static Servers serverPackages(File pkg) {
        File workspace = pkg == null ? null : workspaceAbove(pkg);
        if (workspace == null) {
            return new Servers(List.of(), true);
        }
        Path wsReal;
        try {
            wsReal = workspace.toPath().toRealPath();
        } catch (IOException ex) {
            return new Servers(List.of(), true);
        }
        File self = real(pkg);
        Map<String, File> all = org.nmox.studio.rack.devices.Workspaces.packages(workspace, MAX_WORKSPACE_PACKAGES);
        boolean complete = all.size() < MAX_WORKSPACE_PACKAGES;
        List<File> out = new ArrayList<>();
        for (File candidate : all.values()) {
            File dir = inside(candidate.toPath(), wsReal);
            if (dir == null || dir.equals(self) || out.contains(dir) || !declaresServer(dir)) {
                continue;
            }
            if (out.size() >= MAX_SERVERS) {
                complete = false;
                break;
            }
            out.add(dir);
        }
        return new Servers(out, complete);
    }

    /** The package's real path, so it compares equal to the real paths {@link #inside} returns. */
    private static File real(File pkg) {
        try {
            return pkg.toPath().toRealPath().toFile();
        } catch (IOException ex) {
            return pkg.getAbsoluteFile();
        }
    }

    private static boolean declaresServer(File dir) {
        for (String name : dependencyNames(dir)) {
            if (SERVER_FRAMEWORKS.contains(name)) {
                return true;
            }
        }
        return false;
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
        File self = real(pkg);
        Set<File> out = new LinkedHashSet<>();
        Map<String, File> declared = null;   // read only when a link is missing
        for (String name : dependencyNames(pkg)) {
            if (out.size() >= MAX_DEPENDENCIES) {
                break;
            }
            File dir = linked(pkg, workspace, name, wsReal);
            if (dir == null && !installed(pkg, workspace, name)) {
                // not installed yet: the workspace's own list says where it is
                // (an install that is not a workspace package is third-party,
                // and never costs a read of every manifest — its review)
                if (declared == null) {
                    declared = byName(workspace);
                }
                File candidate = declared.get(name);
                dir = candidate == null ? null : inside(candidate.toPath(), wsReal);
            }
            if (dir != null && !dir.equals(self)) {
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

    /** Dependency names read from one manifest, at most: a clone's manifest can declare any number. */
    static final int MAX_NAMES = 200;
    private static final List<String> SECTIONS = List.of("dependencies", "devDependencies", "peerDependencies");

    /**
     * Every dependency name the package declares, runtime first, in the
     * order the file writes them — read with a streaming tokener, because
     * {@code JSONObject} keeps its keys in hash order and "the first
     * {@value #MAX_DEPENDENCIES} declared" must mean the file's first
     * (its review). At most {@value #MAX_NAMES}; the reading stops there.
     */
    static List<String> dependencyNames(File pkg) {
        File f = new File(pkg, "package.json");
        if (!f.isFile()) {
            return List.of();
        }
        java.util.Map<String, List<String>> bySection = new java.util.HashMap<>();
        try {
            org.json.JSONTokener t = new org.json.JSONTokener(
                    org.nmox.studio.core.util.BoundedReads.read(f.toPath()));
            if (t.nextClean() != '{') {
                return List.of();
            }
            int read = 0;
            for (char c = t.nextClean(); c == '"' && read < MAX_NAMES; ) {
                String key = t.nextString('"');
                if (t.nextClean() != ':') {
                    break;
                }
                if (SECTIONS.contains(key) && t.nextClean() == '{') {
                    List<String> keys = bySection.computeIfAbsent(key, k -> new ArrayList<>());
                    read += orderedKeys(t, keys, MAX_NAMES - read);
                } else {
                    if (SECTIONS.contains(key)) {
                        t.back();
                    }
                    t.nextValue();
                }
                char sep = t.nextClean();
                c = sep == ',' ? t.nextClean() : 0;
            }
        } catch (IOException | RuntimeException ex) {
            // an unreadable or malformed manifest declares what was read before it
        }
        Set<String> names = new LinkedHashSet<>();
        for (String section : SECTIONS) {
            for (String name : bySection.getOrDefault(section, List.of())) {
                if (isPackageName(name)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** Reads an object's keys in order (the tokener just past its brace), skipping values; returns how many were read. */
    private static int orderedKeys(org.json.JSONTokener t, List<String> into, int max) {
        int n = 0;
        for (char c = t.nextClean(); c == '"'; ) {
            String key = t.nextString('"');
            if (t.nextClean() != ':') {
                return n;
            }
            t.nextValue();
            if (n < max) {
                into.add(key);
                n++;
            }
            char sep = t.nextClean();
            if (sep != ',' || n >= max) {
                return n;
            }
            c = t.nextClean();
        }
        return n;
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

    /** Whether anything is installed under {@code name}, a link or a directory. */
    private static boolean installed(File pkg, File workspace, String name) {
        for (File base : List.of(pkg, workspace)) {
            if (Files.exists(new File(new File(base, "node_modules"), name).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
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
