package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

/**
 * Reads the project's package.json so devices can resolve their AUTO
 * positions to the tools the project actually uses: a project with a
 * "build" script gets `npm run build`, a vite project gets `npx vite`,
 * a jest project gets `npx jest`, and so on.
 */
public final class ProjectInspector {

    private ProjectInspector() {
    }

    /** The toolchain a project belongs to, detected from its manifest. */
    public enum ProjectKind {
        NODE("package.json"),
        RUST("Cargo.toml"),
        GO("go.mod"),
        MAVEN("pom.xml"),
        GRADLE("build.gradle", "build.gradle.kts"),
        PYTHON("pyproject.toml", "requirements.txt", "setup.py"),
        RUBY("Gemfile", "Rakefile"),
        PHP("composer.json"),
        CMAKE("CMakeLists.txt"),
        MAKE("Makefile"),
        NONE();

        private final String[] manifests;

        ProjectKind(String... manifests) {
            this.manifests = manifests;
        }

        public String manifest() {
            return manifests.length > 0 ? manifests[0] : "";
        }
    }

    /** Directories never scanned for nested project manifests. */
    private static final java.util.Set<String> SKIP_DIRS = java.util.Set.of(
            "node_modules", ".git", "dist", "build", "target", "out",
            "vendor", "coverage", "__pycache__", ".venv");
    private static final int MAX_CHILD_SCAN = 40;

    /**
     * Detects EVERY toolchain present, each mapped to the directory that
     * carries its manifest: the project root first, then one level of
     * subdirectories - the classic monorepo with frontend/package.json
     * and backend/Cargo.toml yields both, with their own directories.
     * Iteration order expresses precedence (Node first).
     */
    public static java.util.LinkedHashMap<ProjectKind, File> detectKinds(File projectDir) {
        java.util.LinkedHashMap<ProjectKind, File> found = new java.util.LinkedHashMap<>();
        for (ProjectKind kind : ProjectKind.values()) {
            if (kind == ProjectKind.NONE) {
                continue;
            }
            File dir = manifestDirFor(projectDir, kind);
            if (dir != null) {
                found.put(kind, dir);
            }
        }
        return found;
    }

    private static File manifestDirFor(File root, ProjectKind kind) {
        for (String manifest : kind.manifests) {
            if (new File(root, manifest).isFile()) {
                return root;
            }
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        int scanned = 0;
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || SKIP_DIRS.contains(name) || ++scanned > MAX_CHILD_SCAN) {
                continue;
            }
            for (String manifest : kind.manifests) {
                if (new File(child, manifest).isFile()) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * The primary toolchain (highest-precedence detected kind). Mixed
     * projects can steer every AUTO knob with the ROSETTA selector
     * instead.
     */
    public static ProjectKind detectKind(File projectDir) {
        var kinds = detectKinds(projectDir);
        return kinds.isEmpty() ? ProjectKind.NONE : kinds.keySet().iterator().next();
    }

    /**
     * The directory commands for this kind should run in: where its
     * manifest lives, falling back to the project root.
     */
    public static File kindDir(File projectDir, ProjectKind kind) {
        File dir = kind == ProjectKind.NONE ? null : manifestDirFor(projectDir, kind);
        return dir != null ? dir : projectDir;
    }

    /** True when the directory carries any recognized project manifest. */
    public static boolean hasProjectManifest(File projectDir) {
        return detectKind(projectDir) != ProjectKind.NONE;
    }

    private static JSONObject read(File projectDir) {
        File pkg = new File(kindDir(projectDir, ProjectKind.NODE), "package.json");
        if (!pkg.isFile()) {
            return null;
        }
        try {
            return new JSONObject(Files.readString(pkg.toPath()));
        } catch (Exception ex) {
            return null;
        }
    }

    /** True if package.json declares the named script. */
    public static boolean hasScript(File projectDir, String name) {
        JSONObject json = read(projectDir);
        return json != null && json.optJSONObject("scripts") != null
                && json.getJSONObject("scripts").has(name);
    }

    /**
     * Counts of [dependencies, devDependencies] declared in package.json,
     * or null when there is no readable package.json.
     */
    public static int[] dependencyCounts(File projectDir) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        JSONObject devDeps = json.optJSONObject("devDependencies");
        return new int[]{
            deps == null ? 0 : deps.length(),
            devDeps == null ? 0 : devDeps.length()
        };
    }

    /** True when the project (or its Node subproject) is an Angular workspace. */
    public static boolean hasAngular(File projectDir) {
        return new File(projectDir, "angular.json").isFile()
                || new File(kindDir(projectDir, ProjectKind.NODE), "angular.json").isFile();
    }

    /** The declared version constraint of a dependency, or null. */
    public static String dependencyVersion(File projectDir, String name) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        if (deps != null && deps.has(name)) {
            return deps.getString(name);
        }
        JSONObject devDeps = json.optJSONObject("devDependencies");
        if (devDeps != null && devDeps.has(name)) {
            return devDeps.getString(name);
        }
        return null;
    }

    /**
     * The first of the candidate packages found in dependencies or
     * devDependencies, or null. Order expresses preference.
     */
    public static String firstDependency(File projectDir, String... candidates) {
        JSONObject json = read(projectDir);
        if (json == null) {
            return null;
        }
        JSONObject deps = json.optJSONObject("dependencies");
        JSONObject devDeps = json.optJSONObject("devDependencies");
        for (String name : candidates) {
            if ((deps != null && deps.has(name)) || (devDeps != null && devDeps.has(name))) {
                return name;
            }
        }
        return null;
    }
}
