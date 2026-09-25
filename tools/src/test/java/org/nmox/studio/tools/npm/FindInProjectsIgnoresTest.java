package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.queries.SharabilityQuery;
import org.netbeans.api.search.SearchScopeOptions;
import org.netbeans.api.search.provider.SearchInfo;
import org.netbeans.api.search.provider.SearchInfoUtils;
import org.netbeans.api.search.provider.SearchListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Edit ▸ Find in Projects enumerates on a Node project, measured
 * through the platform's OWN search walk rather than a model of it.
 *
 * <p>The path, read from the RELEASE310 bytecode: the Open Projects and
 * Main Project scopes ({@code utilities-project}'s
 * {@code AbstractProjectSearchScope.createDefaultProjectSearchInfo}) find
 * no {@code SearchInfoDefinition} or {@code SubTreeSearchOptions} in a
 * WebProject's lookup and call {@link SearchInfoUtils#createSearchInfoForRoots}
 * on the project directory — the root this test hands it. That info
 * carries the two default filters; {@code FilterHelper} keeps the
 * sharability one unless the dialog's "Search in Generated Sources" is
 * ticked ({@link SearchScopeOptions#setSearchInGenerated}, remembered as
 * {@code search_in_generated}, default {@code false}), and
 * {@code SharabilityFilter} answers {@code DO_NOT_TRAVERSE} for a folder
 * {@link SharabilityQuery} calls {@code NOT_SHARABLE}. That query reaches
 * the project through {@code ProjectSharabilityQuery2} →
 * {@code FileOwnerQuery} → {@link WebProjectFactory} → the WebProject's
 * lookup — which is why this test resolves the project through
 * {@link ProjectManager} instead of constructing one.
 *
 * <p>Before 3.2 a WebProject carried no sharability answer at all, so a
 * search for a function name in a project with {@code node_modules} and a
 * built {@code dist/} listed every copy of it.
 */
class FindInProjectsIgnoresTest {

    private static final String WORD = "needleFunction";

    @BeforeEach
    void freshFacts() {
        WebProjectSharability.forgetForTest();
    }

    /** The fixture the question named: one word in four places. */
    private static Path nodeFixture(Path dir) throws IOException {
        write(dir, "package.json", "{\"name\":\"fixture\"}");
        write(dir, ".gitignore", "dist/\n");
        write(dir, "src/z.js", "export function " + WORD + "() {}\n");
        write(dir, "node_modules/x.js", "module.exports = " + WORD + ";\n");
        write(dir, "node_modules/lib/package.json", "{\"name\":\"lib\"}");
        write(dir, "node_modules/lib/index.js", "exports." + WORD + " = 1;\n");
        write(dir, "dist/y.js", "var " + WORD + "=0;\n");
        return dir;
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    /** Makes {@code dir} a git work tree the way git itself marks one. */
    private static void gitInit(Path dir) throws IOException {
        write(dir, ".git/HEAD", "ref: refs/heads/main\n");
    }

    /**
     * The files the platform's search walk would read, filtered to those
     * holding the word — the rows Find in Projects lists.
     */
    private static List<String> hits(Path dir, boolean searchInGenerated) throws IOException {
        FileObject root = FileUtil.toFileObject(FileUtil.normalizeFile(dir.toFile()));
        assertThat(root).isNotNull();
        root.refresh();
        Project p = ProjectManager.getDefault().findProject(root);
        assertThat(p).as("the fixture is a WebProject").isInstanceOf(WebProject.class);

        SearchInfo info = SearchInfoUtils.createSearchInfoForRoots(new FileObject[]{root});
        SearchScopeOptions options = SearchScopeOptions.create();
        options.setSearchInGenerated(searchInGenerated);
        List<String> found = new ArrayList<>();
        for (FileObject fo : info.getFilesToSearch(options, new SearchListener() {
        }, new AtomicBoolean())) {
            File f = FileUtil.toFile(fo);
            if (Files.readString(f.toPath(), StandardCharsets.UTF_8).contains(WORD)) {
                found.add(FileUtil.getRelativePath(root, fo));
            }
        }
        found.sort(null);
        return found;
    }

    @Test
    @DisplayName("Outside a repository: node_modules and a gitignored dist/ are skipped, src/ is found")
    void noRepositoryFixture(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        assertThat(hits(dir, false)).containsExactly("src/z.js");
    }

    @Test
    @DisplayName("Search in Generated Sources brings every copy back")
    void generatedSourcesOptionIsTheWayBack(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        assertThat(hits(dir, true)).containsExactly(
                "dist/y.js", "node_modules/lib/index.js", "node_modules/x.js", "src/z.js");
    }

    @Test
    @DisplayName("In a git repository the .gitignore decides, node_modules included")
    void repositoryFollowsItsGitignore(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        gitInit(dir);
        write(dir, ".gitignore", "node_modules/\ndist/\n");
        assertThat(hits(dir, false)).containsExactly("src/z.js");
    }

    @Test
    @DisplayName("In a git repository a heavy directory git does NOT ignore is still searched")
    void repositoryNeverContradictsGit(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        gitInit(dir);
        // .gitignore lists dist/ only: git tracks node_modules here, so the
        // IDE must not call it NOT_SHARABLE (the git module would then treat
        // it as ignored and may write it into .gitignore — GitUtils.isIgnored)
        assertThat(hits(dir, false)).containsExactly(
                "node_modules/lib/index.js", "node_modules/x.js", "src/z.js");
    }

    @Test
    @DisplayName("A negation re-includes a file the rule above it excluded")
    void negationReincludes(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        gitInit(dir);
        write(dir, ".gitignore", "node_modules/\n*.log\n!keep.log\n");
        write(dir, "src/a.log", WORD);
        write(dir, "src/keep.log", WORD);
        assertThat(hits(dir, false)).containsExactly(
                "dist/y.js", "src/keep.log", "src/z.js");
    }

    @Test
    @DisplayName("A nested .gitignore applies beneath its own directory")
    void nestedGitignore(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        gitInit(dir);
        write(dir, ".gitignore", "node_modules/\ndist/\n");
        write(dir, "src/.gitignore", "generated/\n");
        write(dir, "src/generated/g.js", WORD);
        write(dir, "generated/top.js", WORD);
        assertThat(hits(dir, false)).containsExactly("generated/top.js", "src/z.js");
    }

    @Test
    @DisplayName("In a monorepo the repository root's .gitignore reaches a package project")
    void monorepoRootRulesReachThePackage(@TempDir Path dir) throws IOException {
        gitInit(dir);
        write(dir, ".gitignore", "node_modules/\ndist/\n");
        Path pkg = dir.resolve("packages/web");
        write(pkg, "package.json", "{\"name\":\"web\"}");
        write(pkg, "src/app.js", WORD);
        write(pkg, "dist/app.js", WORD);
        write(pkg, "node_modules/dep/index.js", WORD);
        assertThat(hits(pkg, false)).containsExactly("src/app.js");
    }

    @Test
    @DisplayName("What the repository keeps is never called NOT_SHARABLE - and never SHARABLE either")
    void trackedFilesStayUnknown(@TempDir Path dir) throws IOException {
        nodeFixture(dir);
        gitInit(dir);
        FileObject root = FileUtil.toFileObject(FileUtil.normalizeFile(dir.toFile()));
        root.refresh();
        assertThat(ProjectManager.getDefault().findProject(root)).isInstanceOf(WebProject.class);
        assertThat(SharabilityQuery.getSharability(root.getFileObject("src/z.js")))
                .isEqualTo(SharabilityQuery.Sharability.UNKNOWN);
        assertThat(SharabilityQuery.getSharability(root.getFileObject("dist")))
                .isEqualTo(SharabilityQuery.Sharability.NOT_SHARABLE);
        assertThat(SharabilityQuery.getSharability(root.getFileObject(".git/HEAD")))
                .as("the repository's own store is never part of the work")
                .isEqualTo(SharabilityQuery.Sharability.NOT_SHARABLE);
    }

    // ---- the 3.2 review: the git module remembers a NOT_SHARABLE answer
    // ---- for the whole session, so a wrong one hides a file from Commit

    /** A link, or the test is skipped where the OS will not make one (Windows without the privilege). */
    private static void link(Path at, Path target) throws IOException {
        try {
            Files.createSymbolicLink(at, target);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort("no symbolic links here: " + e);
        }
    }

    private static SharabilityQuery.Sharability ask(Path project, Path file) {
        FileObject dir = FileUtil.toFileObject(FileUtil.normalizeFile(project.toFile()));
        return new WebProjectSharability(dir).getSharability(file.toUri());
    }

    @Test
    @DisplayName("A symbolic link is a file to git: node_modules/ does not match a linked node_modules")
    void symlinkIsNotADirectory(@TempDir Path dir) throws IOException {
        gitInit(dir);
        write(dir, ".gitignore", "node_modules/\nbuild/\n");
        write(dir, "real/x.js", "x");
        link(dir.resolve("node_modules"), dir.resolve("real"));
        link(dir.resolve("build"), Path.of("real"));
        assertThat(ask(dir, dir.resolve("node_modules")))
                .as("git reports the link untracked, not ignored")
                .isEqualTo(SharabilityQuery.Sharability.UNKNOWN);
        assertThat(ask(dir, dir.resolve("build"))).isEqualTo(SharabilityQuery.Sharability.UNKNOWN);
        Files.createDirectories(dir.resolve("dist"));
        write(dir, ".gitignore", "node_modules/\nbuild/\ndist/\n");
        assertThat(ask(dir, dir.resolve("dist"))).as("a real folder still is one")
                .isEqualTo(SharabilityQuery.Sharability.NOT_SHARABLE);
    }

    @Test
    @DisplayName("An edit to .gitignore is seen by the very next question, however soon")
    void noStaleRules(@TempDir Path dir) throws IOException {
        gitInit(dir);
        write(dir, ".gitignore", "dist/\n");
        Path dist = dir.resolve("dist");
        Files.createDirectories(dist);
        assertThat(WebProjectSharability.ignored(dir, dist, true)).isTrue();
        write(dir, ".gitignore", "# dist is committed now\n");
        assertThat(WebProjectSharability.ignored(dir, dist, true))
                .as("no remembered answer outlives the edit").isFalse();
    }

    @Test
    @DisplayName("git init is seen at once: the heavy-folder names stop applying inside a new repository")
    void noStaleRoot(@TempDir Path dir) throws IOException {
        Path nm = dir.resolve("node_modules");
        Files.createDirectories(nm);
        assertThat(WebProjectSharability.ignored(dir, nm, true))
                .as("outside a repository the product's heavy names apply").isTrue();
        gitInit(dir);
        assertThat(WebProjectSharability.ignored(dir, nm, true))
                .as("inside one only the repository's own files speak").isFalse();
    }

    @Test
    @DisplayName("An unreadable .gitignore hides nothing beneath it; a linked one is not read at all")
    void unreadableAndLinkedIgnoreFiles(@TempDir Path dir) throws IOException {
        gitInit(dir);
        write(dir, ".gitignore", "*.log\n");
        write(dir, "sub/.gitignore", "!keep.log\n" + "#".repeat((int) WebProjectSharability.MAX_IGNORE_BYTES));
        Path log = dir.resolve("sub/keep.log");
        write(dir, "sub/keep.log", "x");
        assertThat(WebProjectSharability.ignored(dir, log, false))
                .as("the over-cap file re-includes keep.log for git").isFalse();
        assertThat(WebProjectSharability.ignored(dir, dir.resolve("other.log"), false)).isTrue();

        write(dir, "rules.txt", "*.txt\n");
        Files.createDirectories(dir.resolve("lnk"));
        link(dir.resolve("lnk/.gitignore"), dir.resolve("rules.txt"));
        write(dir, "lnk/a.txt", "x");
        assertThat(WebProjectSharability.ignored(dir, dir.resolve("lnk/a.txt"), false))
                .as("git does not follow an in-tree .gitignore link").isFalse();
    }
}
