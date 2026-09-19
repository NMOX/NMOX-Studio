package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
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

    /**
     * The kinds whose marker is a LAST RESORT rather than a manifest to walk
     * up to: {@code ProjectInspector.detectKinds} only grants them at the
     * project ROOT and only when nothing else matched. They are answered by
     * {@link #lastResortMarker} instead, because walking ancestors for an
     * {@code index.html} would make a project of every directory that holds
     * one.
     */
    private static final Set<ProjectKind> LAST_RESORT =
            Set.of(ProjectKind.STATIC, ProjectKind.LEARN);

    /**
     * Doors this factory opens that name no {@code ProjectKind} — an Angular
     * workspace and the two framework manifests from v1.92.0, all of which
     * the rack detects some other way.
     */
    private static final String[] NOT_A_KIND_MARKER = {
        "angular.json", "ember-cli-build.js", "remix.config.js"};

    /**
     * Every manifest the rack understands makes a real platform project —
     * DERIVED from {@code ProjectKind}, not copied from it.
     *
     * <p>It used to be sixty names written out by hand under that same
     * sentence, and the sentence was not true. {@code setup.py},
     * {@code Rakefile} and {@code bun.lockb} were markers the rack detected
     * and this list had never learned, so a Python repository carrying only a
     * {@code setup.py} had working Run, Build and Test lanes and no way to be
     * opened: no {@code ActionProvider}, no F6, no OpenProjects.
     *
     * <p>That is the same defect recorded a few lines below for
     * {@code CMakeLists.txt} and {@code Makefile} — <i>the lanes existed; the
     * door didn't</i> — and it recurred within the same file because the fix
     * then was a longer list rather than one home. A list you can derive was
     * never a list to keep (v2.146.0), and {@code ProjectKindDoorsTest} now
     * fails the build if a kind ever loses its door again.
     */
    private static final String[] MANIFESTS = manifests();

    private static String[] manifests() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ProjectKind kind : ProjectKind.values()) {
            if (LAST_RESORT.contains(kind)) {
                continue;
            }
            names.addAll(List.of(kind.manifests()));
        }
        names.addAll(List.of(NOT_A_KIND_MARKER));
        return names.toArray(String[]::new);
    }

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
        return lastResortMarker(projectDirectory);
    }

    /**
     * The last-resort doors, deliberate and root-only: a directory with an
     * {@code index.html} is a project — a 2005 site deserves to open too —
     * and so is a learning space, whose {@code .nmox-learn} marker IS its
     * manifest and whose pre-wired rack driver IS its toolchain (v2.58.0).
     *
     * <p>These are checked here rather than walked up to like a manifest,
     * because an ancestor walk would make a project of every directory that
     * happens to hold an {@code index.html}. Kind precedence — any real
     * manifest outranks both — lives in {@code ProjectInspector}; recognition
     * here is just a boolean.
     */
    /**
     * Whether a directory holding a file of this name opens as a project —
     * the NAME-ONLY half of {@link #isProject}.
     *
     * <p>It cannot be the whole of it: the recursive-manifest rule needs a
     * directory to ask about its parent. It reads the same three sources the
     * real check does — the derived manifests, the globbed suffixes, the
     * last-resort markers — so it is a second traversal, never a second list.
     * {@code ProjectKindDoorsTest} asks the factory this question rather than
     * comparing against a copy of the answer.
     */
    static boolean opensOn(String fileName) {
        for (String manifest : MANIFESTS) {
            if (manifest.equals(fileName)) {
                return true;
            }
        }
        if (!fileName.startsWith(".") && matchesGlobSuffix(fileName)) {
            return true;
        }
        for (ProjectKind kind : LAST_RESORT) {
            for (String marker : kind.manifests()) {
                if (marker.equals(fileName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The manifests walked up to, for the gate that keeps the derivation honest. */
    static List<String> walkedManifests() {
        return List.of(MANIFESTS);
    }

    private static boolean lastResortMarker(FileObject dir) {
        for (ProjectKind kind : LAST_RESORT) {
            for (String marker : kind.manifests()) {
                if (dir.getFileObject(marker) != null) {
                    return true;
                }
            }
        }
        return false;
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