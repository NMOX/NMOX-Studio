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

    @Test
    @DisplayName("a gitdir: pointer aimed OUTSIDE a .git directory is refused (ledger 43 confinement)")
    void branchRefusesGitdirOutsideDotGit() throws Exception {
        // a crafted .git FILE aiming at an arbitrary dir whose HEAD-shaped
        // first line would otherwise leak through the branch chip
        Path secret = dir.resolve("not-a-git-dir");
        Files.createDirectories(secret);
        Files.writeString(secret.resolve("HEAD"),
                "ref: refs/heads/leaked\n", StandardCharsets.UTF_8);
        Path victim = dir.resolve("victim");
        Files.createDirectories(victim);
        Files.writeString(victim.resolve(".git"),
                "gitdir: " + secret.toAbsolutePath() + "\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(victim.toFile()))
                .as("a gitdir pointer must land inside a .git dir or be refused")
                .isNull();
    }

    @Test
    @DisplayName("a gitdir: pointer using ../ to escape is canonicalized and refused")
    void branchRefusesTraversalGitdir() throws Exception {
        Path secret = dir.resolve("escape");
        Files.createDirectories(secret);
        Files.writeString(secret.resolve("HEAD"),
                "ref: refs/heads/escaped\n", StandardCharsets.UTF_8);
        Path victim = dir.resolve("nested/victim");
        Files.createDirectories(victim);
        Files.writeString(victim.resolve(".git"),
                "gitdir: ../../escape\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(victim.toFile())).isNull();
    }

    // ---- unreadable / unrecognized states hide rather than lie ----

    @Test
    @DisplayName("branch is null for a directory that has no .git at all")
    void branchNullWithoutDotGit() throws Exception {
        Path plain = dir.resolve("no-repo");
        Files.createDirectories(plain);
        assertThat(GitFacts.branch(plain.toFile())).isNull();
    }

    @Test
    @DisplayName("an empty ref name after the prefix is refused, not shown as blank")
    void branchNullOnEmptyRefName() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("ref: refs/heads/\n"))).isNull();
    }

    @Test
    @DisplayName("a .git FILE that is not a gitdir: pointer yields no branch")
    void branchNullOnNonPointerGitFile() throws Exception {
        Path odd = dir.resolve("odd");
        Files.createDirectories(odd);
        Files.writeString(odd.resolve(".git"), "this is not a pointer\n",
                StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(odd.toFile())).isNull();
    }

    @Test
    @DisplayName("a gitdir: pointer with an empty path is refused")
    void branchNullOnEmptyGitdirPath() throws Exception {
        Path odd = dir.resolve("empty-pointer");
        Files.createDirectories(odd);
        Files.writeString(odd.resolve(".git"), "gitdir:   \n", StandardCharsets.UTF_8);
        assertThat(GitFacts.branch(odd.toFile())).isNull();
    }

    @Test
    @DisplayName("a whitespace-only HEAD yields no branch")
    void branchNullOnBlankHead() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("   \n"))).isNull();
    }

    @Test
    @DisplayName("a HEAD without a trailing newline still parses (small file, no terminator)")
    void branchParsesHeadWithoutNewline() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("ref: refs/heads/main")))
                .isEqualTo("main");
    }

    @Test
    @DisplayName("a detached SHA-256 HEAD (64 hex chars) abbreviates like SHA-1 does")
    void branchDetachedSha256() throws Exception {
        String sha256 = "a1b2c3d4".repeat(8); // 64 hex chars
        assertThat(GitFacts.branch(repoWithHead(sha256 + "\n")))
                .isEqualTo(sha256.substring(0, 7));
    }

    @Test
    @DisplayName("a 40-char HEAD with non-hex characters is not a commit — null, not a fake branch")
    void branchRefusesNonHexSha() throws Exception {
        assertThat(GitFacts.branch(repoWithHead("z".repeat(40) + "\n"))).isNull();
    }

    // ---- headStamp (3.2.0, line blame's cache key) ----

    @Test
    @DisplayName("headStamp moves when the branch ref moves — a commit re-keys the blame cache")
    void headStampFollowsTheRef() throws Exception {
        File repo = repoWithHead("ref: refs/heads/main\n");
        Path ref = repo.toPath().resolve(".git/refs/heads/main");
        Files.createDirectories(ref.getParent());
        Files.writeString(ref, "a".repeat(40) + "\n", StandardCharsets.UTF_8);
        String before = GitFacts.headStamp(repo);
        Files.writeString(ref, "b".repeat(40) + "\n", StandardCharsets.UTF_8);
        String after = GitFacts.headStamp(repo);
        assertThat(before).isNotNull().contains("a".repeat(40));
        assertThat(after).isNotEqualTo(before).contains("b".repeat(40));
    }

    @Test
    @DisplayName("headStamp: a detached HEAD is its own stamp; outside a repository it is null")
    void headStampDetachedAndOutside() throws Exception {
        assertThat(GitFacts.headStamp(repoWithHead("c".repeat(40) + "\n"))).startsWith("c".repeat(40));
        assertThat(GitFacts.headStamp(null)).isNull();
        Path plain = dir.resolve("plain");
        Files.createDirectories(plain);
        assertThat(GitFacts.headStamp(plain.toFile())).isNull();
    }

    @Test
    @DisplayName("headStamp never reads outside refs/: a crafted HEAD naming ../ is not followed")
    void headStampRefusesTraversal() throws Exception {
        File repo = repoWithHead("ref: refs/../../secret\n");
        Files.writeString(dir.resolve("repo/secret"), "TOKEN-VALUE\n", StandardCharsets.UTF_8);
        assertThat(GitFacts.headStamp(repo)).isNotNull().doesNotContain("TOKEN-VALUE");
    }

    // ---- 3.2.0: porcelain v2, onBranch, regular files only ----

    @Test
    @DisplayName("porcelain v2: entry lines count, # branch headers never do")
    void changeCountV2() {
        String out = "# branch.oid abc\n# branch.head main\n# branch.ab +1 -0\n"
                + "1 .M N... 100644 100644 100644 a b x.js\n2 R. N... 100644 100644 100644 a b R100 y.js\tz.js\n"
                + "u UU N... 100644 100644 100644 100644 a b c w.js\n? new.txt\n\n";
        assertThat(GitFacts.changeCountV2(out)).isEqualTo(4);
        assertThat(GitFacts.changeCountV2("# branch.head main\n")).isZero();
        assertThat(GitFacts.changeCountV2(null)).isZero();
    }

    @Test
    @DisplayName("ahead/behind from # branch.ab; none without an upstream or when malformed")
    void aheadBehind() {
        assertThat(GitFacts.aheadBehind("# branch.head main\n# branch.ab +3 -12\n")).containsExactly(3, 12);
        assertThat(GitFacts.aheadBehind("# branch.head main\n")).as("no upstream").isNull();
        assertThat(GitFacts.aheadBehind("# branch.ab 3 -1\n")).isNull();
        assertThat(GitFacts.aheadBehind("# branch.ab +x -1\n")).isNull();
        assertThat(GitFacts.aheadBehind("# branch.ab +1\n")).isNull();
        assertThat(GitFacts.aheadBehind(null)).isNull();
    }

    @Test
    @DisplayName("onBranch: a ref is a branch; a detached sha, an empty ref and no repo are not")
    void onBranch() throws Exception {
        assertThat(GitFacts.onBranch(repoWithHead("ref: refs/heads/main\n"))).isTrue();
        assertThat(GitFacts.onBranch(repoWithHead("0123456789abcdef0123456789abcdef01234567\n"))).isFalse();
        assertThat(GitFacts.onBranch(repoWithHead("ref: refs/heads/\n"))).isFalse();
        assertThat(GitFacts.onBranch(null)).isFalse();
    }

    @Test
    @DisplayName("a HEAD that is not a regular file (a FIFO planted in .git) reads as nothing, never blocks")
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void headNotARegularFile() throws Exception {
        Path gitDir = dir.resolve("fifo/.git");
        Files.createDirectories(gitDir);
        Process mk = new ProcessBuilder("mkfifo", gitDir.resolve("HEAD").toString()).start();
        assertThat(mk.waitFor()).isZero();
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> assertThat(GitFacts.branch(dir.resolve("fifo").toFile())).isNull());
    }
}
