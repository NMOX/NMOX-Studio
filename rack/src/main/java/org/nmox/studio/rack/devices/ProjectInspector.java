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
        BUN("bun.lock", "bun.lockb", "bunfig.toml"),
        DENO("deno.json", "deno.jsonc"),
        NODE("package.json"),
        RUST("Cargo.toml"),
        FOUNDRY("foundry.toml"),
        GO("go.mod"),
        ELIXIR("mix.exs"),
        ERLANG("rebar.config"),
        CLOJURE("deps.edn", "project.clj"),
        SWIFT("Package.swift"),
        DOTNET(), // *.csproj / *.fsproj / *.sln - glob-detected below
        DART("pubspec.yaml"),
        SCALA("build.sbt"),
        HASKELL("stack.yaml", "cabal.project"),
        ZIG("build.zig"),
        OCAML("dune-project"),
        CRYSTAL("shard.yml"),
        MAVEN("pom.xml"),
        GRADLE("build.gradle", "build.gradle.kts"),
        PYTHON("pyproject.toml", "requirements.txt", "setup.py"),
        RUBY("Gemfile", "Rakefile"),
        PHP("composer.json"),
        CMAKE("CMakeLists.txt"),
        MAKE("Makefile"),
        // ---- classic web (v1.34): every real toolchain manifest above
        // outranks these, and they outrank NONE ----
        WEBPACK("webpack.config.js", "webpack.config.cjs", "webpack.config.mjs"),
        GRUNT("Gruntfile.js", "Gruntfile.coffee"),
        GULP("gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs"),
        BOWER("bower.json"),
        /**
         * The last resort, root-only, and only when nothing else matched:
         * a bare directory with an index.html is a project too — the
         * oldest stack on the web deserves to open like any other.
         */
        STATIC("index.html", "index.htm"),
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
            if (kind == ProjectKind.NONE || kind == ProjectKind.STATIC) {
                continue;
            }
            File dir = manifestDirFor(projectDir, kind);
            if (dir != null) {
                found.put(kind, dir);
            }
        }
        // STATIC is the true last resort: a ROOT index.html, and only when
        // no real manifest matched anywhere — so a Vite app (root
        // index.html beside package.json) never grows a spurious kind.
        if (found.isEmpty() && hasManifestAt(projectDir, ProjectKind.STATIC)) {
            found.put(ProjectKind.STATIC, projectDir);
        }
        return found;
    }

    /** True when one of the kind's manifests sits directly in this directory. */
    public static boolean hasManifestAt(File dir, ProjectKind kind) {
        for (String manifest : kind.manifests) {
            if (new File(dir, manifest).isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The directory carrying this kind's manifest — the root, then one
     * level of subdirectories — or null when the kind is absent.
     */
    public static File manifestDir(File projectDir, ProjectKind kind) {
        return manifestDirFor(projectDir, kind);
    }

    private static File manifestDirFor(File root, ProjectKind kind) {
        if (kind == ProjectKind.DOTNET) {
            return dotnetDir(root);
        }
        if (kind == ProjectKind.STATIC) {
            // STATIC never walks subdirectories: it means "serve THIS folder",
            // not "some docs/ dir happens to hold an index.html"
            return hasManifestAt(root, kind) ? root : null;
        }
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

    /** .NET projects carry *.csproj/*.fsproj/*.sln - extension-detected. */
    private static File dotnetDir(File root) {
        if (hasDotnetManifest(root)) {
            return root;
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
            if (hasDotnetManifest(child)) {
                return child;
            }
        }
        return null;
    }

    private static boolean hasDotnetManifest(File dir) {
        String[] names = dir.list((d, name) -> name.endsWith(".csproj")
                || name.endsWith(".fsproj") || name.endsWith(".sln"));
        return names != null && names.length > 0;
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

    private static final java.util.Map<File, CacheEntry> packageJsonCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CacheEntry {
        final long lastModified;
        final JSONObject json;

        CacheEntry(long lastModified, JSONObject json) {
            this.lastModified = lastModified;
            this.json = json;
        }
    }

    private static JSONObject read(File projectDir) {
        File pkg = new File(kindDir(projectDir, ProjectKind.NODE), "package.json");
        if (!pkg.isFile()) {
            return null;
        }
        long currentMod = pkg.lastModified();
        CacheEntry entry = packageJsonCache.get(pkg);
        if (entry != null && entry.lastModified == currentMod) {
            return entry.json;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(pkg.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            packageJsonCache.put(pkg, new CacheEntry(currentMod, json));
            return json;
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

    /** Extracts a dependency version from mix.exs using the given pattern. */
    public static String mixDependencyVersion(File projectDir, java.util.regex.Pattern pattern) {
        File mixExs = new File(projectDir, "mix.exs");
        if (!mixExs.isFile()) {
            return null;
        }
        try {
            java.util.regex.Matcher m = pattern.matcher(Files.readString(mixExs.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * The locked version of a package from composer.lock (searching
     * packages, then packages-dev), with composer's "v" prefix stripped:
     * "v11.9.2" -> "11.9.2". Null when there is no readable lock or the
     * package is not in it.
     */
    public static String composerLockVersion(File dir, String packageName) {
        File lock = new File(dir, "composer.lock");
        if (!lock.isFile()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(lock.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            for (String section : new String[]{"packages", "packages-dev"}) {
                org.json.JSONArray packages = json.optJSONArray(section);
                if (packages == null) {
                    continue;
                }
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject pkg = packages.optJSONObject(i);
                    if (pkg != null && packageName.equals(pkg.optString("name"))) {
                        String version = pkg.optString("version", "");
                        if (version.isEmpty()) {
                            return null;
                        }
                        return version.startsWith("v") ? version.substring(1) : version;
                    }
                }
            }
        } catch (Exception ex) {
            // unreadable or malformed lock: report unknown
        }
        return null;
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
