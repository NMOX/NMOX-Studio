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
import org.netbeans.api.queries.VisibilityQuery;
import org.netbeans.api.search.SearchScopeOptions;
import org.netbeans.api.search.provider.SearchInfo;
import org.netbeans.api.search.provider.SearchInfoUtils;
import org.netbeans.api.search.provider.SearchListener;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.search.SearchInfoDefinition;
import org.netbeans.spi.search.SubTreeSearchOptions;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A repository's {@code search.exclude} and {@code files.exclude}
 * (.vscode/settings.json) in a WebProject: Find in Projects skips what
 * they name, the Projects view hides what {@code files.exclude} names -
 * and NOTHING about either reaches the queries the platform's git module
 * reads. That last part is the 3.2 law: a "not sharable" answer hides a
 * file from Commit for the rest of the session, so an exclusion a project
 * wants for its SEARCH must travel by a seam only the search reads.
 */
class FindInProjectsSearchExcludeTest {

    private static final String WORD = "needleFunction";

    @BeforeEach
    void freshFacts() {
        WebProjectSharability.forgetForTest();
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    /** One word in six places, in a git work tree whose .gitignore names none of them. */
    private static Path fixture(Path dir, String settings) throws IOException {
        write(dir, "package.json", "{\"name\":\"fixture\"}");
        write(dir, ".git/HEAD", "ref: refs/heads/main\n");
        if (settings != null) {
            write(dir, ".vscode/settings.json", settings);
        }
        write(dir, "src/a.js", WORD);
        write(dir, "src/b.min.js", WORD);
        write(dir, "vendor/v.js", WORD);
        write(dir, "lib/vendor/x.js", WORD);
        write(dir, "tmp-cache/c.js", WORD);
        write(dir, "docs/d.md", WORD);
        return dir;
    }

    private static final String SETTINGS = """
            {
              // the team's own
              "files.exclude": { "**/tmp-cache": true },
              "search.exclude": { "**/vendor": true, "**/*.min.js": true, },
            }
            """;

    private static FileObject root(Path dir) {
        FileObject root = FileUtil.toFileObject(FileUtil.normalizeFile(dir.toFile()));
        assertThat(root).isNotNull();
        root.refresh();
        return root;
    }

    private static WebProject project(FileObject root) throws IOException {
        Project p = ProjectManager.getDefault().findProject(root);
        assertThat(p).as("the fixture is a WebProject").isInstanceOf(WebProject.class);
        return (WebProject) p;
    }

    /** The rows Find in Projects would list, through the walk the project scopes ask the project for. */
    private static List<String> hits(Path dir, boolean searchInGenerated) throws IOException {
        FileObject root = root(dir);
        SearchInfoDefinition own = project(root).getLookup().lookup(SearchInfoDefinition.class);
        assertThat(own).as("the scopes find the project's own definition in its lookup").isNotNull();
        return hits(root, SearchInfoUtils.createForDefinition(own), searchInGenerated);
    }

    private static List<String> hits(FileObject root, SearchInfo info, boolean searchInGenerated) throws IOException {
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
    @DisplayName("Find in Projects skips what search.exclude names, and what files.exclude hides")
    void searchSkipsWhatTheSettingsName(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        assertThat(hits(dir, false)).containsExactly("docs/d.md", "src/a.js");
    }

    @Test
    @DisplayName("the settings add to what git ignores, they do not replace it: both are skipped")
    void gitignoreStillApplies(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        write(dir, ".gitignore", "docs/\n");
        assertThat(hits(dir, false)).as("docs is git's to skip, vendor and the rest the settings'")
                .containsExactly("src/a.js");
        assertThat(SharabilityQuery.getSharability(root(dir).getFileObject("docs")))
                .as("and git's own answer is still git's").isEqualTo(SharabilityQuery.Sharability.NOT_SHARABLE);
    }

    @Test
    @DisplayName("the control: without the settings every copy is listed")
    void control(@TempDir Path dir) throws IOException {
        fixture(dir, null);
        assertThat(hits(dir, false)).containsExactly(
                "docs/d.md", "lib/vendor/x.js", "src/a.js", "src/b.min.js", "tmp-cache/c.js", "vendor/v.js");
    }

    @Test
    @DisplayName("Search in Generated Sources is the one way back to everything")
    void generatedSourcesBringsEverythingBack(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        assertThat(hits(dir, true)).containsExactly(
                "docs/d.md", "lib/vendor/x.js", "src/a.js", "src/b.min.js", "tmp-cache/c.js", "vendor/v.js");
    }

    @Test
    @DisplayName("the 3.2 law: an excluded file's sharability and visibility answers are what they were")
    void theGitFacingAnswersAreUnchanged(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        FileObject root = root(dir);
        project(root);
        assertThat(hits(dir, false)).doesNotContain("vendor/v.js", "tmp-cache/c.js", "src/b.min.js");
        for (String rel : new String[] {"vendor", "vendor/v.js", "lib/vendor/x.js", "src/b.min.js", "tmp-cache", "tmp-cache/c.js"}) {
            FileObject fo = root.getFileObject(rel);
            assertThat(fo).as(rel).isNotNull();
            assertThat(SharabilityQuery.getSharability(fo))
                    .as("%s: the git module reads this answer, and it must not learn of the exclusion", rel)
                    .isEqualTo(SharabilityQuery.Sharability.UNKNOWN);
            assertThat(VisibilityQuery.getDefault().isVisible(fo))
                    .as("%s: every tree and Go to File read this answer", rel).isTrue();
            assertThat(WebProjectSharability.ignored(dir, dir.resolve(rel), fo.isFolder())).as(rel).isFalse();
        }
    }

    @Test
    @DisplayName("the seam is the search's own: a walk built without asking the project does not see the settings")
    void onlyTheProjectsOwnWalkIsFiltered(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        FileObject root = root(dir);
        WebProject p = project(root);
        assertThat(hits(root, SearchInfoUtils.createSearchInfoForRoots(new FileObject[] {root}), false))
                .as("the platform's default walk: sharability and visibility only")
                .contains("vendor/v.js", "tmp-cache/c.js", "src/b.min.js");
        assertThat(p.getLookup().lookup(SubTreeSearchOptions.class))
                .as("a search scoped to a selected folder is built by the platform's defaults: not filtered, and said so")
                .isNull();
    }

    @Test
    @DisplayName("a false in search.exclude brings a folder files.exclude hides back into the search")
    void searchExcludeOverridesKeyByKey(@TempDir Path dir) throws IOException {
        fixture(dir, "{ \"files.exclude\": {\"**/tmp-cache\": true, \"docs\": true}, \"search.exclude\": {\"**/tmp-cache\": false} }");
        assertThat(hits(dir, false)).containsExactly(
                "lib/vendor/x.js", "src/a.js", "src/b.min.js", "tmp-cache/c.js", "vendor/v.js");
    }

    @Test
    @DisplayName("an edit to settings.json is seen by the very next search")
    void theNextSearchSeesAnEdit(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        assertThat(hits(dir, false)).containsExactly("docs/d.md", "src/a.js");
        write(dir, ".vscode/settings.json", "{ \"search.exclude\": { \"docs\": true } } // edited, longer than before........");
        assertThat(hits(dir, false)).containsExactly(
                "lib/vendor/x.js", "src/a.js", "src/b.min.js", "tmp-cache/c.js", "vendor/v.js");
    }

    @Test
    @DisplayName("in a monorepo the repository root's settings reach a package project, judged from the root")
    void monorepo(@TempDir Path dir) throws IOException {
        write(dir, ".git/HEAD", "ref: refs/heads/main\n");
        write(dir, ".vscode/settings.json", "{ \"search.exclude\": { \"packages/*/vendor\": true, \"src\": true } }");
        Path pkg = dir.resolve("packages/web");
        write(pkg, "package.json", "{\"name\":\"web\"}");
        write(pkg, "src/app.js", WORD);
        write(pkg, "vendor/v.js", WORD);
        assertThat(hits(pkg, false)).as("packages/web/vendor is named; 'src' names the repository root's own src")
                .containsExactly("src/app.js");
    }

    @Test
    @DisplayName("outside a repository the settings are nobody's: nothing is skipped for them")
    void noRepository(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"name\":\"fixture\"}");
        write(dir, ".vscode/settings.json", SETTINGS);
        write(dir, "vendor/v.js", WORD);
        write(dir, "src/a.js", WORD);
        assertThat(hits(dir, false)).containsExactly("src/a.js", "vendor/v.js");
    }

    @Test
    @DisplayName("what the dialog may ask on the EDT - can it search, which roots - is the default walk's answer")
    void rootsAndCanSearch(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        FileObject root = root(dir);
        SearchInfoDefinition own = project(root).getLookup().lookup(SearchInfoDefinition.class);
        assertThat(own.canSearch()).isTrue();
        assertThat(own.getSearchRoots()).hasSize(1);
        assertThat(own.getSearchRoots().get(0).getFileObject()).isEqualTo(root);
    }

    @Test
    @DisplayName("the filter keeps the search API's contract: a file is not a folder, a folder is not a file")
    void filterContract(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        FileObject root = root(dir);
        WebProjectSearch.Excluded filter = new WebProjectSearch.Excluded(root,
                org.nmox.studio.core.util.VsCodeExcludes.parse(SETTINGS, false));
        assertThat(filter.searchFile(root.getFileObject("src/a.js"))).isTrue();
        assertThat(filter.searchFile(root.getFileObject("src/b.min.js"))).isFalse();
        assertThat(filter.traverseFolder(root.getFileObject("vendor")))
                .isEqualTo(org.netbeans.spi.search.SearchFilterDefinition.FolderResult.DO_NOT_TRAVERSE);
        assertThat(filter.traverseFolder(root.getFileObject("src")))
                .isEqualTo(org.netbeans.spi.search.SearchFilterDefinition.FolderResult.TRAVERSE);
        assertThat(filter.traverseFolder(root)).as("the project folder itself")
                .isEqualTo(org.netbeans.spi.search.SearchFilterDefinition.FolderResult.TRAVERSE);
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> filter.searchFile(root.getFileObject("src")));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> filter.traverseFolder(root.getFileObject("src/a.js")));
    }

    // ---- the Projects view ---------------------------------------------------

    private static List<String> listed(Node folder) {
        List<String> names = new ArrayList<>();
        for (Node n : folder.getChildren().getNodes(true)) {
            DataObject d = n.getLookup().lookup(DataObject.class);
            names.add(d == null ? "?" + n.getName() : d.getPrimaryFile().getNameExt());
        }
        names.sort(null);
        return names;
    }

    @Test
    @DisplayName("the Projects view hides what files.exclude names - and only that: search.exclude hides nothing")
    void projectsViewHidesFilesExclude(@TempDir Path dir) throws IOException {
        fixture(dir, SETTINGS);
        FileObject root = root(dir);
        WebProject p = project(root);
        Node view = p.getLookup().lookup(LogicalViewProvider.class).createLogicalView();
        assertThat(listed(view)).containsExactly(".git", ".vscode", "docs", "lib", "package.json", "src", "vendor");
        assertThat(view.getLookup().lookup(Project.class)).as("still the project's node").isSameAs(p);
        assertThat(view.getDisplayName()).isEqualTo(p.getName());
        assertThat(dir.resolve("tmp-cache/c.js")).as("hidden, not removed").hasContent(WORD);
    }

    @Test
    @DisplayName("the Projects view's control: no settings, everything listed")
    void projectsViewControl(@TempDir Path dir) throws IOException {
        fixture(dir, null);
        WebProject p = project(root(dir));
        Node view = p.getLookup().lookup(LogicalViewProvider.class).createLogicalView();
        assertThat(listed(view)).containsExactly(".git", "docs", "lib", "package.json", "src", "tmp-cache", "vendor");
    }
}
