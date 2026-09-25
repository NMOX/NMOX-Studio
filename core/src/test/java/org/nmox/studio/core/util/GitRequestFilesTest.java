package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitRequestFilesTest {

    @Test
    @DisplayName("a file git wrote for its editor is one of its names inside a .git folder")
    void namesInsideGit() {
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/COMMIT_EDITMSG"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/worktrees/w/MERGE_MSG"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/rebase-merge/git-rebase-todo"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/addp-hunk-edit.diff"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/docs/COMMIT_EDITMSG")))
                .as("the same name in the project").isFalse();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/config"))).isFalse();
        assertThat(GitRequestFiles.isRequestFile(null)).isFalse();
    }

    /** Runs real git with no user or system config, so the shapes are git's own. */
    private static void git(Path dir, String... args) throws Exception {
        var cmd = new java.util.ArrayList<String>(List.of("git"));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        pb.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor()).as(String.join(" ", cmd) + ": " + out).isZero();
    }

    @Test
    @DisplayName("a git folder under another name is recognised by git's own shape: --separate-git-dir, bare, a linked worktree")
    void gitFoldersUnderOtherNames(@TempDir Path tmp) throws Exception {
        Path store = tmp.resolve("store");
        Path wt = tmp.resolve("wt");
        Files.createDirectories(wt);
        git(wt, "init", "-q", "--separate-git-dir=" + store);
        assertThat(GitRequestFiles.isRequestFile(store.resolve("COMMIT_EDITMSG").toFile()))
                .as("--separate-git-dir").isTrue();
        assertThat(GitRequestFiles.isRequestFile(store.resolve("rebase-merge/git-rebase-todo").toFile())).isTrue();

        Path bare = tmp.resolve("bare.git-store");
        Files.createDirectories(bare);
        git(bare, "init", "-q", "--bare");
        assertThat(GitRequestFiles.isRequestFile(bare.resolve("TAG_EDITMSG").toFile())).as("bare").isTrue();

        git(wt, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "one");
        git(wt, "worktree", "add", "-q", tmp.resolve("linked").toString());
        assertThat(GitRequestFiles.isRequestFile(store.resolve("worktrees/linked/MERGE_MSG").toFile()))
                .as("a linked worktree's folder").isTrue();
        assertThat(GitRequestFiles.isGitDir(store.resolve("worktrees/linked").toFile()))
                .as("HEAD beside commondir, on its own").isTrue();
    }

    @Test
    @DisplayName("a project folder that only looks like one is not: HEAD alone, or objects and refs with no HEAD")
    void lookalikesAreNot(@TempDir Path tmp) throws Exception {
        Path half = tmp.resolve("half");
        Files.createDirectories(half.resolve("objects"));
        Files.createDirectories(half.resolve("refs"));
        assertThat(GitRequestFiles.isRequestFile(half.resolve("COMMIT_EDITMSG").toFile())).isFalse();
        Path head = tmp.resolve("head");
        Files.createDirectories(head);
        Files.writeString(head.resolve("HEAD"), "ref: refs/heads/main\n");
        assertThat(GitRequestFiles.isRequestFile(head.resolve("COMMIT_EDITMSG").toFile())).isFalse();
        Files.createDirectories(head.resolve("objects"));
        assertThat(GitRequestFiles.isRequestFile(head.resolve("COMMIT_EDITMSG").toFile()))
                .as("objects without refs").isFalse();
    }

    @Test
    @DisplayName("the name test reads no disk and knows every name")
    void namesArePure() {
        assertThat(GitRequestFiles.hasRequestName(new File("/nowhere/NOTES_EDITMSG"))).isTrue();
        assertThat(GitRequestFiles.hasRequestName(new File("/nowhere/git-rebase-todo"))).isTrue();
        assertThat(GitRequestFiles.hasRequestName(new File("/nowhere/ADD_EDIT.patch"))).isTrue();
        assertThat(GitRequestFiles.hasRequestName(new File("/nowhere/HEAD"))).isFalse();
        assertThat(GitRequestFiles.hasRequestName(null)).isFalse();
    }
}
