package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 * Teaches the platform what counts as a project: a {@code
 * @ServiceProvider}-registered {@link ProjectFactory} the global
 * ProjectManager consults for every directory it is asked about. Any of
 * the ~58 recognized manifests (package.json through Clarinet.toml) —
 * or, as the deliberate last resort, a bare index.html — makes the
 * directory a {@link WebProject}. Recognition here is only the boolean;
 * kind precedence (which toolchain wins when several manifests coexist)
 * lives in the rack's {@code ProjectInspector}. {@code saveProject} is
 * a no-op because these projects carry no IDE-owned metadata to write.
 */
@ServiceProvider(service = ProjectFactory.class)
public class WebProjectFactory implements ProjectFactory {

    public static final String PACKAGE_JSON = "package.json";

    /** Every manifest the rack understands makes a real platform project. */
    private static final String[] MANIFESTS = {
        "package.json", "Cargo.toml", "go.mod", "mix.exs", "rebar.config",
        "deps.edn", "project.clj", "Package.swift", "pom.xml", "build.gradle",
        "build.gradle.kts", "pyproject.toml", "requirements.txt", "Gemfile",
        "composer.json", "angular.json", "bun.lock", "bunfig.toml", "deno.json", "deno.jsonc",
        "foundry.toml",
        "Project.toml", "JuliaProject.toml", "dub.json", "dub.sdl", "info.rkt",
        "elm.json", "rescript.json", "bsconfig.json", "spago.yaml", "spago.dhall",
        "v.mod", "fpm.toml", "alire.toml", "Scarb.toml", "Move.toml",
        "aiken.toml", "Clarinet.toml", "tact.config.json",
        "gleam.toml", "pubspec.yaml", "build.sbt", "stack.yaml", "cabal.project",
        "build.zig", "dune-project", "shard.yml",
        // classic web (v1.34): manifest-only legacy repos open as projects
        "bower.json", "Gruntfile.js", "Gruntfile.coffee",
        "gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs",
        "webpack.config.js", "webpack.config.cjs", "webpack.config.mjs",
        // Ember CLI + Remix/React Router framework mode (v1.92.0)
        "ember-cli-build.js", "remix.config.js",
        // v1.233.0: the kinds ProjectInspector always knew but this
        // factory never recognized — their IDE lanes were wired to a
        // project that could not open
        "CMakeLists.txt", "Makefile"};

    /**
     * The glob-detected kinds (v1.233.0): ProjectInspector detects
     * DOTNET by *.csproj/*.fsproj/*.sln and NIM by *.nimble, but this
     * factory only ever checked fixed names — so a .NET or bare-nimble
     * Nim checkout was never a platform project at all: no
     * ActionProvider, no F6/Test, no OpenProjects. The lanes existed;
     * the door didn't.
     */
    private static final String[] GLOB_SUFFIXES = {".csproj", ".fsproj", ".sln", ".nimble"};

    /**
     * Manifests that recur in EVERY subdirectory by their build system's
     * own convention (cmake's add_subdirectory, recursive make), so the
     * nearest-ancestor rule would fragment one repo into a project per
     * folder — F6 on a file under src/ would configure src/ instead of
     * the root (v1.234.0 review). A directory whose PARENT carries the
     * same manifest is a subdirectory of the real project, not a
     * project of its own; the chain collapses to its outermost member.
     */
    private static final String[] RECURSIVE_MANIFESTS = {"CMakeLists.txt", "Makefile"};

    private static boolean isRecursive(String manifest) {
        for (String r : RECURSIVE_MANIFESTS) {
            if (r.equals(manifest)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isProject(FileObject projectDirectory) {
        for (String manifest : MANIFESTS) {
            if (projectDirectory.getFileObject(manifest) != null) {
                if (isRecursive(manifest)) {
                    FileObject parent = projectDirectory.getParent();
                    if (parent != null && parent.getFileObject(manifest) != null) {
                        continue; // a subdirectory of the real CMake/make root
                    }
                }
                return true;
            }
        }
        if (hasGlobbedManifest(projectDirectory)) {
            return true;
        }
        // the static last resort, deliberate: a directory with an
        // index.html is a project — a 2005 site deserves to open too.
        // Kind precedence (any real manifest outranks STATIC) lives in
        // ProjectInspector; recognition here is just a boolean.
        return projectDirectory.getFileObject("index.html") != null
                || projectDirectory.getFileObject("index.htm") != null;
    }

    /**
     * The glob check rides a name-only {@code File.list} where possible:
     * {@code getChildren()} materializes a FileObject per child, and this
     * factory is consulted for every ancestor of every file the platform
     * asks about — a full-listing walk was the v1.234.0 review's MED.
     * Dotfiles are excluded and plain files required, because nimble's
     * package cache is a DIRECTORY named {@code ~/.nimble}: without the
     * filter, every Nim user's $HOME became a platform project, re-arming
     * the v1.33.1 TCC-storm class.
     */
    private static boolean hasGlobbedManifest(FileObject projectDirectory) {
        File dir = FileUtil.toFile(projectDirectory);
        if (dir != null) {
            String[] hits = dir.list((parent, name) -> !name.startsWith(".")
                    && matchesGlobSuffix(name) && new File(parent, name).isFile());
            return hits != null && hits.length > 0;
        }
        // non-masterfs mounts (tests): same filter over FileObjects
        for (FileObject child : projectDirectory.getChildren()) {
            String name = child.getNameExt();
            if (child.isData() && !name.startsWith(".") && matchesGlobSuffix(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGlobSuffix(String name) {
        for (String suffix : GLOB_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
        if (isProject(projectDirectory)) {
            return new WebProject(projectDirectory, state);
        }
        return null;
    }

    @Override
    public void saveProject(Project project) throws IOException, ClassCastException {
        // Projects are saved automatically
    }
}