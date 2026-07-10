package org.nmox.studio.core.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Git facts from file reads alone: no fixture here ever needs the git
 * binary — a .git directory with a HEAD file IS the on-disk contract
 * these parsers target, so temp-dir fixtures are the real thing.
 */
class GitFactsTest {

    @TempDir
    Path dir;

    private File repoWithHead(String headContent) throws Exception {
        Path gitDir = dir.resolve("repo/.git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), headContent, StandardCharsets.UTF_8);
        return dir.resolve("repo").toFile();
    }

    // ---- repoRoot ----

    @Test
    @DisplayName("repoRoot finds the nearest ancestor with a .git directory")
    void repoRootWalksUp() throws Exception {
        File repo = repoWithHead("ref: refs/heads/main\n");
        Path nested = repo.toPath().resolve("src/deep/inside");
        Files.createDirectories(nested);
        assertThat(GitFacts.repoRoot(nested.toFile())).isEqualTo(repo);
        assertThat(GitFacts.repoRoot(repo)).isEqualTo(repo);
    }

    @Test
    @DisplayName("repoRoot accepts a .git FILE — worktrees and submodules mark roots that way")
    void repoRootAcceptsGitFile() throws Exception {
        Path worktree = dir.resolve("wt");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: /elsewhere\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.repoRoot(worktree.toFile())).isEqualTo(worktree.toFile());
    }

    @Test
    @DisplayName("repoRoot is null outside any repository")
    void repoRootNullForNonRepo() throws Exception {
        Path plain = dir.resolve("plain/sub");
        Files.createDirectories(plain);
        // stop the walk from escaping the fixture into the real filesystem:
        // the temp dir itself lives outside any repo, so a hit above it
        // would be a real machine's checkout leaking in — not expected
        // under the JUnit temp root
        assertThat(GitFacts.repoRoot(plain.toFile())).isNull();
    }

    // ---- branch ----

    @Test
    @DisplayName("normal ref: refs/heads/main parses to main")
    void branchNormalRef() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("ref: refs/heads/main\n"))).isEqualTo("main");
    }

    @Test
    @DisplayName("branch names keep their slashes: feature/x stays whole")
    void branchSlashedName() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("ref: refs/heads/feature/x\n")))
                .isEqualTo("feature/x");
    }

    @Test
    @DisplayName("detached HEAD (bare sha) shows its first 7 characters")
    void branchDetachedSha() throws Exception {
        assertThat(GitFacts.branch(repoWithHead(
                "0123456789abcdef0123456789abcdef01234567\n")))
                .isEqualTo("0123456");
    }

    @Test
    @DisplayName("a .git FILE's gitdir: pointer is followed to the real HEAD")
    void branchFollowsGitdirFile() throws Exception {
        Path realGitDir = dir.resolve("main-repo/.git/worktrees/wt");
        Files.createDirectories(realGitDir);
        Files.writeString(realGitDir.resolve("HEAD"),
                "ref: refs/heads/hotfix/panel\n", StandardCharsets.UTF_8);
        Path worktree = dir.resolve("wt");
        Files.createDirectories(worktree);
        // relative pointer, resolved against the worktree — the shape
        // `git worktree add` actually writes
        Files.writeString(worktree.resolve(".git"),
                "gitdir: ../main-repo/.git/worktrees/wt\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(worktree.toFile())).isEqualTo("hotfix/panel");
    }

    @Test
    @DisplayName("unreadable or unrecognized HEAD yields null — hide, don't lie")
    void branchNullOnUnreadableState() throws Exception {
        // .git dir exists but HEAD is missing
        Path noHead = dir.resolve("nohead/.git");
        Files.createDirectories(noHead);
        assertThat(GitFacts.branch(dir.resolve("nohead").toFile())).isNull();

        // HEAD holds something that is neither a ref nor a sha
        assertThat(GitFacts.branch(repoWithHead("total garbage\n"))).isNull();

        // gitdir: pointer to a directory that does not exist
        Path badWt = dir.resolve("badwt");
        Files.createDirectories(badWt);
        Files.writeString(badWt.resolve(".git"),
                "gitdir: ../does-not-exist\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(badWt.toFile())).isNull();

        // not a repo at all
        assertThat(GitFacts.branch(null)).isNull();
    }

    // ---- changeCount ----

    @Test
    @DisplayName("porcelain lines count changes; the trailing newline never inflates")
    void changeCountCountsNonBlankLines() {
        assertThat(GitFacts.changeCount(" M pom.xml\n?? new.txt\n")).isEqualTo(2);
        assertThat(GitFacts.changeCount(" M pom.xml")).isEqualTo(1);
        assertThat(GitFacts.changeCount("")).isZero();
        assertThat(GitFacts.changeCount(null)).isZero();
        assertThat(GitFacts.changeCount("\n\n")).isZero();
    }
}
