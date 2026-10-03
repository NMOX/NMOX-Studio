package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where git may be spawned without asking (3.5.13): the one question the
 * chip, line blame and the Standup ask, anchored at the repository's root.
 */
class GitMayRunTest {

    @TempDir
    Path tmp;

    private File repo(String name) throws Exception {
        Files.createDirectories(tmp.resolve(name).resolve(".git"));
        return tmp.resolve(name).toFile();
    }

    @Test
    @DisplayName("a trusted project inside an untrusted repository does not make git run: the config is the repository's")
    void theAnchorIsTheRepositoryRoot() throws Exception {
        File root = repo("mono");
        File app = Files.createDirectories(root.toPath().resolve("packages/app")).toFile();
        String elsewhere = tmp.resolve("home").toString();

        assertThat(WorkspaceTrust.gitMayRun(app, elsewhere, app::equals))
                .as("only the sub-folder is trusted: the Standup ran git log here until 3.5.13").isFalse();
        assertThat(WorkspaceTrust.gitMayRun(app, elsewhere, root::equals))
                .as("the repository's root is trusted").isTrue();
        assertThat(WorkspaceTrust.gitMayRun(root, elsewhere, root::equals)).isTrue();
    }

    @Test
    @DisplayName("a folder in no repository has nothing for git to do")
    void noRepositoryNoGit() throws Exception {
        File loose = Files.createDirectories(tmp.resolve("loose")).toFile();
        assertThat(WorkspaceTrust.gitMayRun(loose, tmp.resolve("home").toString(), f -> true)).isFalse();
    }

    @Test
    @DisplayName("a repository rooted at the home folder, or above it, is the user's own: no question, and so no notice")
    void theHomeRepositoryIsTheUsersOwn() throws Exception {
        File home = repo("home");                       // dotfiles kept in git
        File workspace = Files.createDirectories(home.toPath().resolve("NMOX")).toFile();
        assertThat(WorkspaceTrust.gitMayRun(workspace, home.getPath(), f -> false))
                .as("~/NMOX resolves to the home repository").isTrue();

        File clone = Files.createDirectories(home.toPath().resolve("src/stranger/.git")).getParent().toFile();
        assertThat(WorkspaceTrust.gitMayRun(clone, home.getPath(), f -> false))
                .as("a clone under home has a root of its own, and that one is asked about").isFalse();
    }

    @Test
    @DisplayName("above home counts, beside it does not, and neither does a folder inside it")
    void holdsHomeOnAPathBoundary() {
        String home = new File(tmp.toFile(), "Users/david").getPath();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users/david"), home)).isTrue();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users"), home)).isTrue();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users/dav"), home))
                .as("a prefix of the name is not a parent").isFalse();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users/david/code"), home)).isFalse();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users/david"), null)).isFalse();
        assertThat(WorkspaceTrust.holdsHome(new File(tmp.toFile(), "Users/david"), " ")).isFalse();
    }

    @Test
    @DisplayName("the chip, line blame and the Standup ask this question, not the folder's own trust")
    void theThreeSitesAskIt() throws Exception {
        assertThat(org.nmox.studio.rack.GateSources.stripComments(Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/service/GitChip.java"))))
                .contains("trusted = WorkspaceTrust::gitMayRun;");
        assertThat(org.nmox.studio.rack.GateSources.stripComments(Files.readString(Path.of(
                "../editor/src/main/java/org/nmox/studio/editor/blame/LineBlame.java"))))
                .contains("trusted = org.nmox.studio.rack.service.WorkspaceTrust::gitMayRun;");
        String board = org.nmox.studio.rack.GateSources.stripComments(Files.readString(Path.of(
                "../ui/src/main/java/org/nmox/studio/ui/tasks/TasksTopComponent.java")));
        int log = board.indexOf("\"git\", \"log\"");
        assertThat(log).isPositive();
        assertThat(board.substring(Math.max(0, log - 400), log))
                .as("the Standup's git log is behind the repository-root question")
                .contains("WorkspaceTrust.gitMayRun(dir)")
                .doesNotContain("WorkspaceTrust.isTrusted(dir)");
    }
}
