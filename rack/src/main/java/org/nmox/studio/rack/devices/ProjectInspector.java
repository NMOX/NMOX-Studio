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

    /**
     * Detects the project's toolchain. Order expresses precedence: a
     * repo with both package.json and a Makefile is a Node project that
     * happens to have a Makefile.
     */
    public static ProjectKind detectKind(File projectDir) {
        for (ProjectKind kind : ProjectKind.values()) {
            for (String manifest : kind.manifests) {
                if (new File(projectDir, manifest).isFile()) {
                    return kind;
                }
            }
        }
        return ProjectKind.NONE;
    }

    /** True when the directory carries any recognized project manifest. */
    public static boolean hasProjectManifest(File projectDir) {
        return detectKind(projectDir) != ProjectKind.NONE;
    }

    private static JSONObject read(File projectDir) {
        File pkg = new File(projectDir, "package.json");
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
