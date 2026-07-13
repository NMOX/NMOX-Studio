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
        LinkedHashMap<String, File> found = new LinkedHashMap<>();
        if (root == null || !root.isDirectory()) {
            return found;
        }
        for (String glob : declaredGlobs(root)) {
            for (File dir : resolve(root, glob)) {
                if (found.size() >= MAX_PACKAGES) {
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

    /** The raw globs both manifest dialects declare, in file order. */
    private static List<String> declaredGlobs(File root) {
        java.util.List<String> globs = new java.util.ArrayList<>();
        File pkg = new File(root, "package.json");
        if (pkg.isFile()) {
            try {
                JSONObject o = new JSONObject(Files.readString(pkg.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8));
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
        if (pnpm.isFile() && pnpm.length() < 64 * 1024) {
            try {
                boolean inPackages = false;
                for (String raw : Files.readAllLines(pnpm.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
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

    private static void walk(File dir, int depth, List<File> out) {
        if (depth < 0 || dir == null || !dir.isDirectory()
                || "node_modules".equals(dir.getName())) {
            return;
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
            String name = new JSONObject(Files.readString(pkg.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8)).optString("name", "");
            return name.isBlank() ? dir.getName() : name;
        } catch (IOException | org.json.JSONException ex) {
            return dir.getName();
        }
    }
}
