package org.nmox.studio.editor.snippets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.snippets.ProjectSnippets.Found;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disk half: which files are snippet files, how far up they are
 * looked for, and what a clone can put there that must not be read.
 */
class ProjectSnippetsTest {

    private static String snippet(String name, String prefix) {
        return "{\"" + name + "\": {\"prefix\": \"" + prefix + "\", \"body\": \"" + name + " body\"}}";
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private static List<String> names(Found found) {
        return found.snippets().stream().map(Snippet::name).toList();
    }

    /** The log lines one read produces. */
    private static List<String> logOf(Runnable read) {
        List<String> lines = new ArrayList<>();
        Logger logger = Logger.getLogger(ProjectSnippets.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord r) {
                lines.add(new java.util.logging.SimpleFormatter().formatMessage(r));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            read.run();
        } finally {
            logger.removeHandler(handler);
        }
        return lines;
    }

    @Test
    @DisplayName("*.code-snippets in the project's .vscode are read; settings.json and <language>.json are not snippet files")
    void readsOnlySnippetFiles(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        write(project.resolve(".vscode/team.code-snippets"), snippet("Team log", "log"));
        write(project.resolve(".vscode/api.code-snippets"), snippet("Api call", "api"));
        write(project.resolve(".vscode/javascript.json"), snippet("User snippet", "usr"));
        write(project.resolve(".vscode/settings.json"), "{\"editor.tabSize\": 2}");
        write(project.resolve(".vscode/nested/deep.code-snippets"), snippet("Deep", "deep"));
        File edited = write(project.resolve("src/app/a.ts"), "").toFile();

        Found found = ProjectSnippets.read(edited);
        assertThat(names(found)).containsExactly("Api call", "Team log");
        assertThat(found.snippets().get(0).source()).isEqualTo("api.code-snippets");
        assertThat(found.workspace().getName()).isEqualTo("shop");
    }

    @Test
    @DisplayName("a monorepo: the repository root's snippets reach a file whose own project is three folders down")
    void repositoryRootIsRead(@TempDir Path tmp) throws IOException {
        Path repo = tmp.resolve("mono");
        Files.createDirectories(repo.resolve(".git"));
        write(repo.resolve(".vscode/team.code-snippets"), snippet("Repo wide", "rw"));
        write(repo.resolve("packages/web/package.json"), "{}");
        write(repo.resolve("packages/web/.vscode/web.code-snippets"), snippet("Web only", "web"));
        write(repo.resolve("packages/api/package.json"), "{}");
        File web = write(repo.resolve("packages/web/src/main.ts"), "").toFile();
        File api = write(repo.resolve("packages/api/src/main.ts"), "").toFile();

        Found forWeb = ProjectSnippets.read(web);
        assertThat(names(forWeb)).containsExactly("Web only", "Repo wide");
        assertThat(forWeb.workspace().getName()).as("the outermost folder that has snippets").isEqualTo("mono");
        assertThat(names(ProjectSnippets.read(api))).containsExactly("Repo wide");

        File outside = write(tmp.resolve("elsewhere/x.ts"), "").toFile();
        assertThat(ProjectSnippets.read(outside).snippets()).isEmpty();
    }

    @Test
    @DisplayName("a snippet file that is a link out of the project is not read; a link to a file inside it is")
    void linkOutIsNotRead(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        Path outside = write(tmp.resolve("outside/secrets.code-snippets"), snippet("From outside", "out"));
        Path inside = write(project.resolve("shared/common.snippets"), snippet("From inside", "in"));
        write(project.resolve(".vscode/own.code-snippets"), snippet("Own", "own"));
        try {
            Files.createSymbolicLink(project.resolve(".vscode/evil.code-snippets"), outside);
            Files.createSymbolicLink(project.resolve(".vscode/linked.code-snippets"), inside);
        } catch (IOException | UnsupportedOperationException noLinks) {
            Assumptions.abort("this file system makes no symbolic links");
        }
        File edited = write(project.resolve("a.js"), "").toFile();

        Found[] found = new Found[1];
        List<String> log = logOf(() -> found[0] = ProjectSnippets.read(edited));
        assertThat(names(found[0])).containsExactly("From inside", "Own");
        assertThat(log).anySatisfy(line -> assertThat(line)
                .contains("evil.code-snippets").contains("link out of the project"));
        assertThat(logOf(() -> ProjectSnippets.read(edited)))
                .as("said once, not at every completion").noneMatch(l -> l.contains("evil.code-snippets"));
    }

    @Test
    @DisplayName("a .vscode that is itself a link out of the project gives nothing")
    void folderLinkOutIsNotRead(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        Path foreign = tmp.resolve("foreign-vscode");
        write(foreign.resolve("x.code-snippets"), snippet("Foreign", "f"));
        try {
            Files.createSymbolicLink(project.resolve(".vscode"), foreign);
        } catch (IOException | UnsupportedOperationException noLinks) {
            Assumptions.abort("this file system makes no symbolic links");
        }
        File edited = write(project.resolve("a.js"), "").toFile();
        Found[] found = new Found[1];
        List<String> log = logOf(() -> found[0] = ProjectSnippets.read(edited));
        assertThat(found[0].snippets()).isEmpty();
        assertThat(log).as("the folder is refused as a folder: the foreign directory is not even listed")
                .anySatisfy(line -> assertThat(line).contains(".vscode leads out of the project"));
        assertThat(log).noneMatch(line -> line.contains("x.code-snippets"));
    }

    @Test
    @DisplayName("a file that does not parse or is too large is left out with one log line, and the others load")
    void badFilesAreSkippedOnce(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        write(project.resolve(".vscode/a-broken.code-snippets"), "{ this is not json");
        write(project.resolve(".vscode/b-huge.code-snippets"),
                "{\"Huge\": {\"prefix\": \"h\", \"body\": \"" + "x".repeat((int) ProjectSnippets.MAX_BYTES) + "\"}}");
        write(project.resolve(".vscode/c-good.code-snippets"), snippet("Good", "g"));
        File edited = write(project.resolve("a.js"), "").toFile();

        Found[] found = new Found[1];
        List<String> first = logOf(() -> found[0] = ProjectSnippets.read(edited));
        assertThat(names(found[0])).containsExactly("Good");
        assertThat(first).filteredOn(l -> l.contains("a-broken.code-snippets")).hasSize(1)
                .allSatisfy(l -> assertThat(l).contains("offers no snippets"));
        assertThat(first).filteredOn(l -> l.contains("b-huge.code-snippets")).hasSize(1)
                .allSatisfy(l -> assertThat(l).contains("KiB"));
        assertThat(logOf(() -> ProjectSnippets.read(edited))).as("the same versions are not read or reported again")
                .isEmpty();
    }

    @Test
    @DisplayName("past twenty snippet files the rest are not read, in name order, and that is said")
    void fileCap(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        for (int i = 0; i < ProjectSnippets.MAX_FILES + 5; i++) {
            write(project.resolve(".vscode/f%02d.code-snippets".formatted(i)), snippet("S%02d".formatted(i), "p" + i));
        }
        File edited = write(project.resolve("a.js"), "").toFile();
        Found[] found = new Found[1];
        List<String> log = logOf(() -> found[0] = ProjectSnippets.read(edited));
        assertThat(names(found[0])).hasSize(ProjectSnippets.MAX_FILES).first().isEqualTo("S00");
        assertThat(names(found[0])).last().isEqualTo("S19");
        assertThat(log).anySatisfy(l -> assertThat(l).contains("more than 20 snippet files"));
    }

    @Test
    @DisplayName("an unchanged file is parsed once; a changed one is read again")
    void cachedByVersion(@TempDir Path tmp) throws IOException {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        Path file = write(project.resolve(".vscode/team.code-snippets"), snippet("Before", "b"));
        File edited = write(project.resolve("a.js"), "").toFile();
        Snippet once = ProjectSnippets.read(edited).snippets().get(0);
        assertThat(ProjectSnippets.read(edited).snippets().get(0)).isSameAs(once);
        Files.writeString(file, snippet("After the edit", "a"));
        assertThat(names(ProjectSnippets.read(edited))).containsExactly("After the edit");
    }

    @Test
    @DisplayName("the home folder's .vscode is VS Code's own and is never read as a project's")
    void homeIsNotAProject(@TempDir Path tmp) throws IOException {
        String home = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tmp.toFile().getAbsolutePath());
            write(tmp.resolve(".vscode/mine.code-snippets"), snippet("Home", "h"));
            File scratch = write(tmp.resolve("scratch.js"), "").toFile();
            assertThat(ProjectSnippets.read(scratch).snippets()).isEmpty();
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    @DisplayName("within() answers from the reading lane, and a document with no file has no snippets")
    void withinUsesTheLane(@TempDir Path tmp) throws Exception {
        Path project = tmp.resolve("shop");
        write(project.resolve("package.json"), "{}");
        Files.createDirectories(project.resolve(".git")); // a repository: no repository, no snippets
        write(project.resolve(".vscode/team.code-snippets"), snippet("Lane", "l"));
        File edited = write(project.resolve("a.js"), "").toFile();
        assertThat(names(ProjectSnippets.within(edited, 10_000))).containsExactly("Lane");
        assertThat(ProjectSnippets.within(null, 10).snippets()).isEmpty();
        ProjectSnippets.awaitIdle();
    }
}
