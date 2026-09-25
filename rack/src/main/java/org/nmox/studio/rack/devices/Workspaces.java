package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The packages of a JS workspace monorepo: the union of package.json's
 * {@code "workspaces"} globs (array form or {@code {"packages":[...]}})
 * and pnpm-workspace.yaml's {@code packages:} list, resolved to the
 * directories that actually carry a package.json. Pure and bounded — a
 * deliberate glob SUBSET ({@code *} matches one path segment,
 * {@code **} walks at most {@value #MAX_DEPTH} levels, exclusions
 * ({@code !...}) are skipped) because the rack steers lanes, it does
 * not re-implement npm. node_modules is never entered; the result is
 * capped at {@value #MAX_PACKAGES} packages.
 */
public final class Workspaces {

    static final int MAX_PACKAGES = 64;
    static final int MAX_DEPTH = 3;

    private Workspaces() {
    }

    /**
     * Package display name → directory, insertion-ordered by manifest
     * declaration. Empty when the project declares no workspaces. The
     * name is the package.json {@code "name"} when present, the
     * directory name otherwise.
     */
    public static LinkedHashMap<String, File> packages(File root) {
        return packages(root, MAX_PACKAGES);
    }

    /**
     * {@link #packages(File)} with a caller's own ceiling: the editor's
     * route jump reads a whole monorepo's manifests once per click (3.3),
     * where WAYPOINT's knob wants a list a person can dial. A map exactly
     * {@code max} long may have stopped short.
     */
    public static LinkedHashMap<String, File> packages(File root, int max) {
        LinkedHashMap<String, File> found = new LinkedHashMap<>();
        if (root == null || !root.isDirectory()) {
            return found;
        }
        for (String glob : declaredGlobs(root)) {
            if (!staysInside(glob)) {
                continue;   // a clone's glob naming a path outside it (3.3 review)
            }
            for (File dir : resolve(root, glob)) {
                if (!inside(root, dir)) {
                    continue;   // reached through a link that leaves the repository
                }
                if (found.size() >= max) {
                    return found;
                }
                String name = packageName(dir);
                if (name != null && !found.containsValue(dir)) {
                    // two packages may collide on name; the dir disambiguates
                    String key = found.containsKey(name)
                            ? name + " (" + dir.getName() + ")" : name;
                    found.put(key, dir);
                }
            }
        }
        return found;
    }

    /** A glob that cannot climb out of the root: relative, and no {@code ..} segment. */
    static boolean staysInside(String glob) {
        if (glob.startsWith("/") || glob.startsWith("\\") || (glob.length() > 1 && glob.charAt(1) == ':')) {
            return false;
        }
        for (String part : glob.split("[/\\\\]")) {
            if (part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code dir} resolves inside the root: a link out of the repository is not a package of it. */
    private static boolean inside(File root, File dir) {
        try {
            String relative = root.getAbsoluteFile().toPath()
                    .relativize(dir.getAbsoluteFile().toPath()).toString();
            // the one home judges the RESOLVED path (ledger 111)
            return org.nmox.studio.core.util.Containment.resolve(root, relative) != null;
        } catch (IllegalArgumentException ex) {
            return false;   // another drive: not inside
        }
    }

    /** The raw globs both manifest dialects declare, in file order. */
    private static List<String> declaredGlobs(File root) {
        java.util.List<String> globs = new java.util.ArrayList<>();
        File pkg = new File(root, "package.json");
        if (pkg.isFile()) {
            try {
                JSONObject o = new JSONObject(
                        org.nmox.studio.core.util.BoundedReads.read(pkg.toPath()));
                Object ws = o.opt("workspaces");
                JSONArray arr = ws instanceof JSONArray a ? a
                        : ws instanceof JSONObject wo ? wo.optJSONArray("packages") : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        globs.add(arr.optString(i, ""));
                    }
                }
            } catch (IOException | org.json.JSONException ex) {
                // unreadable manifest = no npm workspaces; pnpm may still declare
            }
        }
        File pnpm = new File(root, "pnpm-workspace.yaml");
        if (pnpm.isFile()) {
            try {
                boolean inPackages = false;
                // this one always had a ceiling of its own; it goes through the
                // shared reader so the file has one way of reading a clone's text
                for (String raw : org.nmox.studio.core.util.BoundedReads
                        .readLines(pnpm.toPath(), 64 * 1024)) {
                    String line = raw.strip();
                    if (line.startsWith("packages:")) {
                        inPackages = true;
                        continue;
                    }
                    if (inPackages) {
                        if (line.startsWith("- ")) {
                            String g = line.substring(2).strip();
                            if ((g.startsWith("\"") && g.endsWith("\"") && g.length() > 1)
                                    || (g.startsWith("'") && g.endsWith("'") && g.length() > 1)) {
                                g = g.substring(1, g.length() - 1);
                            }
                            globs.add(g);
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inPackages = false; // next top-level key
                        }
                    }
                }
            } catch (IOException ex) {
                // unreadable = undeclared
            }
        }
        globs.removeIf(g -> g.isBlank() || g.startsWith("!"));
        return globs;
    }

    /** The directories a single glob names, within the bounded subset. */
    private static List<File> resolve(File root, String glob) {
        java.util.List<File> dirs = new java.util.ArrayList<>();
        if (glob.contains("**")) {
            String prefix = glob.substring(0, glob.indexOf("**"));
            File base = prefix.isEmpty() ? root
                    : new File(root, prefix.replaceAll("/+$", ""));
            walk(base, MAX_DEPTH, dirs);
        } else if (glob.endsWith("/*")) {
            File base = new File(root, glob.substring(0, glob.length() - 2));
            File[] children = base.listFiles(File::isDirectory);
            if (children != null) {
                java.util.Arrays.sort(children,
                        java.util.Comparator.comparing(File::getName));
                dirs.addAll(java.util.Arrays.asList(children));
            }
        } else {
            dirs.add(new File(root, glob));
        }
        return dirs;
    }

    /**
     * The {@code **} arm of a declared workspace glob. The skip set is the
     * one home (ledger 110): this was the shortest of the thirteen answers
     * — a single inline name, which is also the shape no set-literal gate
     * would ever have caught — and the eleven it was missing matter here.
     * An Angular library build writes {@code dist/my-lib/package.json}, so
     * a project declaring {@code "workspaces": ["**"]} was offering its own
     * build output to WAYPOINT as a package to dial, with every Node lane
     * then rooted in a directory the next build deletes.
     */
    private static void walk(File dir, int depth, List<File> out) {
        if (depth < 0 || dir == null || !dir.isDirectory()
                || java.nio.file.Files.isSymbolicLink(dir.toPath())
                || org.nmox.studio.core.util.HeavyDirs.isHeavy(dir.getName())) {
            return;   // (3.3 review) a ** walk never follows a link: its target may be anywhere
        }
        if (new File(dir, "package.json").isFile()) {
            out.add(dir);
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children != null) {
            java.util.Arrays.sort(children,
                    java.util.Comparator.comparing(File::getName));
            for (File child : children) {
                walk(child, depth - 1, out);
            }
        }
    }

    /** The package's declared name, or the dir name; null = not a package. */
    private static String packageName(File dir) {
        File pkg = new File(dir, "package.json");
        if (!pkg.isFile()) {
            return null;
        }
        try {
            String name = new JSONObject(org.nmox.studio.core.util.BoundedReads
                    .read(pkg.toPath())).optString("name", "");
            return name.isBlank() ? dir.getName() : name;
        } catch (IOException | org.json.JSONException ex) {
            return dir.getName();
        }
    }
}
