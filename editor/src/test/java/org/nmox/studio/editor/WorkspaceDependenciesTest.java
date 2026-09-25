package org.nmox.studio.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.design.CssClasses;
import org.nmox.studio.editor.design.CssTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A monorepo's design tokens and shared styles live in a package the
 * edited package depends on: the design scans read the file's own package
 * and then those, never an unrelated sibling, never a third-party install,
 * never a link that leaves the repository.
 */
class WorkspaceDependenciesTest {

    @TempDir
    Path tmp;

    private static void write(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    /** A workspace with packages/tokens, packages/web (depends on tokens) and packages/other. */
    private Path monorepo(String webDeps) throws IOException {
        Path ws = tmp.resolve("repo");
        Files.createDirectories(ws.resolve(".git"));
        write(ws.resolve("package.json"), "{\"name\":\"acme\",\"private\":true,\"workspaces\":[\"packages/*\"]}");
        write(ws.resolve("packages/tokens/package.json"), "{\"name\":\"@acme/tokens\"}");
        write(ws.resolve("packages/tokens/tokens.css"), ":root { --brand: tomato; --space: 8px; }\n.btn { color: var(--brand); }\n");
        write(ws.resolve("packages/other/package.json"), "{\"name\":\"@acme/other\"}");
        write(ws.resolve("packages/other/other.css"), ":root { --stranger: blue; }\n");
        write(ws.resolve("packages/web/package.json"), "{\"name\":\"@acme/web\",\"dependencies\":{" + webDeps + "}}");
        write(ws.resolve("packages/web/app.css"), ":root { --space: 12px; }\n.card { padding: var(--space); }\n");
        return ws;
    }

    private static Path link(Path at, Path target) throws IOException {
        Files.createDirectories(at.getParent());
        try {
            return Files.createSymbolicLink(at, target);
        } catch (IOException | UnsupportedOperationException ex) {
            assumeTrue(false, "this file system cannot make a symbolic link");
            return null;
        }
    }

    @Test
    @DisplayName("a package depending on a workspace package reads it; an unrelated sibling is never read")
    void declaredDependency() throws IOException {
        Path ws = monorepo("\"@acme/tokens\":\"workspace:*\",\"react\":\"^19\"");
        List<File> deps = WorkspaceDependencies.of(ws.resolve("packages/web").toFile());
        assertThat(deps).extracting(File::getName).containsExactly("tokens");
    }

    @Test
    @DisplayName("the design scans find a dependency's token and class, the own package's value winning a shared name")
    void scansReadTheDependency() throws IOException {
        Path ws = monorepo("\"@acme/tokens\":\"*\"");
        File web = ws.resolve("packages/web").toFile();
        List<CssTokens.ProjectToken> tokens = CssTokens.scanProject(web);
        assertThat(tokens).extracting(CssTokens.ProjectToken::name).contains("--brand", "--space")
                .doesNotContain("--stranger");
        CssTokens.ProjectToken space = tokens.stream().filter(t -> t.name().equals("--space")).findFirst().orElseThrow();
        assertThat(space.value()).as("the package's own declaration comes first").isEqualTo("12px");
        assertThat(CssClasses.scanProject(web)).extracting(CssClasses.ProjectSelector::name).contains("btn", "card");
    }

    @Test
    @DisplayName("Rename Class edits only its own package, but a name a dependency declares is a collision")
    void renameStaysHome() throws IOException {
        Path ws = monorepo("\"@acme/tokens\":\"*\"");
        File web = ws.resolve("packages/web").toFile();
        CssClasses.RenameSurvey onto = CssClasses.surveyRename(web, "card", "btn");
        assertThat(onto.collision()).as(".btn is declared by @acme/tokens").isTrue();
        assertThat(onto.files()).extracting(File::getName).containsExactly("app.css");
        assertThat(CssClasses.surveyRename(web, "card", "tile").collision()).isFalse();
    }

    @Test
    @DisplayName("a dependency is found where the package manager linked it, even outside the declared globs")
    void linkedDependency() throws IOException {
        Path ws = monorepo("\"@acme/brand\":\"*\"");
        write(ws.resolve("libs/brand/package.json"), "{\"name\":\"@acme/brand\"}");
        link(ws.resolve("node_modules/@acme/brand"), ws.resolve("libs/brand"));
        assertThat(WorkspaceDependencies.of(ws.resolve("packages/web").toFile()))
                .extracting(File::getName).containsExactly("brand");
    }

    @Test
    @DisplayName("a link leaving the repository, and a third-party install, are never read")
    void containment() throws IOException {
        Path ws = monorepo("\"@acme/evil\":\"*\",\"lodash\":\"*\"");
        Path outside = tmp.resolve("outside/evil");
        write(outside.resolve("package.json"), "{\"name\":\"@acme/evil\"}");
        link(ws.resolve("node_modules/@acme/evil"), outside);
        write(ws.resolve("node_modules/lodash/package.json"), "{\"name\":\"lodash\"}");
        assertThat(WorkspaceDependencies.of(ws.resolve("packages/web").toFile())).isEmpty();
    }

    @Test
    @DisplayName("no workspace above the package, or one past the repository's root: nothing to follow")
    void noWorkspace() throws IOException {
        Path plain = tmp.resolve("plain");
        Files.createDirectories(plain.resolve(".git"));
        write(plain.resolve("package.json"), "{\"name\":\"plain\",\"dependencies\":{\"@acme/tokens\":\"*\"}}");
        assertThat(WorkspaceDependencies.of(plain.toFile())).isEmpty();

        // a workspace manifest above a repository's root is someone else's
        Path outer = tmp.resolve("outer");
        write(outer.resolve("package.json"), "{\"workspaces\":[\"*\"]}");
        Path inner = outer.resolve("inner");
        Files.createDirectories(inner.resolve(".git"));
        write(inner.resolve("pkg/package.json"), "{\"name\":\"pkg\",\"dependencies\":{\"sib\":\"*\"}}");
        write(outer.resolve("sib/package.json"), "{\"name\":\"sib\"}");
        assertThat(WorkspaceDependencies.workspaceAbove(inner.resolve("pkg").toFile())).isNull();
    }

    @Test
    @DisplayName("a dependency name that could walk a path is not a name")
    void hostileNames() throws IOException {
        Path ws = monorepo("\"../other\":\"*\",\"@acme/../other\":\"*\",\"@acme/tokens\":\"*\"");
        assertThat(WorkspaceDependencies.dependencyNames(ws.resolve("packages/web").toFile()))
                .containsExactly("@acme/tokens");
        assertThat(WorkspaceDependencies.isPackageName("@acme/ui-kit.v2")).isTrue();
        assertThat(WorkspaceDependencies.isPackageName("lodash")).isTrue();
        for (String bad : new String[] {"..", ".hidden", "@acme/..", "@/x", "a/b", "@a/b/c", "A", "a\\b", ""}) {
            assertThat(WorkspaceDependencies.isPackageName(bad)).as(bad).isFalse();
        }
    }

    @Test
    @DisplayName("the package at the workspace root follows nothing: its scan already covers the packages")
    void rootFollowsNothing() throws IOException {
        Path ws = monorepo("\"@acme/tokens\":\"*\"");
        assertThat(WorkspaceDependencies.of(ws.toFile())).isEmpty();
    }
}
