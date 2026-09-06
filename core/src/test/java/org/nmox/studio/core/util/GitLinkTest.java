package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GitLinkTest {

    @Test
    @DisplayName("every remote form git accepts resolves to the same owner/repo; non-GitHub hosts are null")
    void parsesEveryRemoteForm() {
        for (String u : new String[] {
            "git@github.com:NMOX/NMOX-Studio.git",
            "git@github.com:NMOX/NMOX-Studio",
            "https://github.com/NMOX/NMOX-Studio.git",
            "https://github.com/NMOX/NMOX-Studio",
            "https://github.com/NMOX/NMOX-Studio/",
            "ssh://git@github.com/NMOX/NMOX-Studio.git",
            "git://github.com/NMOX/NMOX-Studio.git",
            "  https://GitHub.com/NMOX/NMOX-Studio.git  "}) {
            GitLink.Remote r = GitLink.parseRemote(u);
            assertThat(r).as(u).isNotNull();
            assertThat(r.slug()).as(u).isEqualTo("NMOX/NMOX-Studio");
        }
        assertThat(GitLink.parseRemote("git@gitlab.com:o/r.git")).isNull();
        assertThat(GitLink.parseRemote("https://gitlab.com/o/r")).isNull();
        assertThat(GitLink.parseRemote("https://github.com/only-owner")).isNull();
        assertThat(GitLink.parseRemote("https://github.com/o/r/extra")).isNull();
        assertThat(GitLink.parseRemote("/local/path/repo.git")).isNull();
        assertThat(GitLink.parseRemote("")).isNull();
        assertThat(GitLink.parseRemote(null)).isNull();
    }

    @Test
    @DisplayName("the origin url is read from its own section, whatever comes before it and however git spells it")
    void originFromConfig() {
        String cfg = "[core]\n\trepositoryformatversion = 0\n"
                + "[remote \"upstream\"]\n\turl = git@github.com:other/thing.git\n"
                + "[remote  \"origin\"]\n\t# pushed here\n\tURL = git@github.com:NMOX/NMOX-Studio.git\n\tfetch = +refs/heads/*:refs/remotes/origin/*\n"
                + "[branch \"main\"]\n\tremote = origin\n";
        assertThat(GitLink.originUrl(cfg)).isEqualTo("git@github.com:NMOX/NMOX-Studio.git");
        assertThat(GitLink.originUrl("[remote \"upstream\"]\n\turl = x\n")).isNull();
        assertThat(GitLink.originUrl("[remote \"origin\"]\n\turl =\n")).isNull();
        assertThat(GitLink.originUrl(null)).isNull();
    }

    @Test
    @DisplayName("a blob url carries ref, path and a 1-based inclusive range; a single line is #L7; the whole file has no fragment")
    void blobUrls() {
        GitLink.Remote r = new GitLink.Remote("NMOX", "NMOX-Studio");
        assertThat(GitLink.blobUrl(r, "main", "src/App.jsx", 3, 14))
                .isEqualTo("https://github.com/NMOX/NMOX-Studio/blob/main/src/App.jsx#L3-L14");
        assertThat(GitLink.blobUrl(r, "main", "src/App.jsx", 7, 7))
                .isEqualTo("https://github.com/NMOX/NMOX-Studio/blob/main/src/App.jsx#L7");
        assertThat(GitLink.blobUrl(r, "feature/x", "src/App.jsx", 0, 0))
                .isEqualTo("https://github.com/NMOX/NMOX-Studio/blob/feature/x/src/App.jsx");
    }

    @Test
    @DisplayName("path segments are percent-encoded, slashes stay separators — a space or a hash in a name never breaks the link")
    void encodesSegments() {
        GitLink.Remote r = new GitLink.Remote("o", "r");
        assertThat(GitLink.blobUrl(r, "main", "docs/My Notes#1/ü.md", 2, 2))
                .isEqualTo("https://github.com/o/r/blob/main/docs/My%20Notes%231/%C3%BC.md#L2");
    }

    @Test
    @DisplayName("the link line labels the path and range and escapes a bracket in the name")
    void linkLine() {
        assertThat(GitLink.linkLine("src/App.jsx", 3, 14, "U")).isEqualTo("[src/App.jsx#L3-L14](U)");
        assertThat(GitLink.linkLine("src/App.jsx", 0, 0, "U")).isEqualTo("[src/App.jsx](U)");
        assertThat(GitLink.linkLine("a]b.md", 1, 1, "U")).isEqualTo("[a\\]b.md#L1](U)");
    }

    @Test
    @DisplayName("GitFacts.originUrl reads a bounded config, follows a worktree's commondir, and is null without one")
    void originFromRepo(@TempDir Path tmp) throws Exception {
        Path main = tmp.resolve("main");
        Files.createDirectories(main.resolve(".git"));
        Files.writeString(main.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(main.resolve(".git/config"), "[remote \"origin\"]\n\turl = https://github.com/NMOX/NMOX-Studio.git\n");
        assertThat(GitFacts.originUrl(main.toFile())).isEqualTo("https://github.com/NMOX/NMOX-Studio.git");
        // a linked worktree: .git is a FILE pointing at <main>/.git/worktrees/<n>, whose commondir names the real gitdir
        Path wtGit = main.resolve(".git/worktrees/wt2");
        Files.createDirectories(wtGit);
        Files.writeString(wtGit.resolve("HEAD"), "ref: refs/heads/shift\n");
        Files.writeString(wtGit.resolve("commondir"), "../..\n");
        Path wt = tmp.resolve("wt2");
        Files.createDirectories(wt);
        Files.writeString(wt.resolve(".git"), "gitdir: " + wtGit + "\n");
        assertThat(GitFacts.originUrl(wt.toFile())).isEqualTo("https://github.com/NMOX/NMOX-Studio.git");
        // a commondir aimed OUTSIDE any .git dir is refused (the ledger-43 shape on the second pointer)
        Path outside = tmp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("config"), "[remote \"origin\"]\n\turl = https://github.com/evil/oracle.git\n");
        Files.writeString(wtGit.resolve("commondir"), outside + "\n");
        assertThat(GitFacts.originUrl(wt.toFile())).isNull();
        // no origin, no repo
        Files.writeString(main.resolve(".git/config"), "[core]\n\tbare = false\n");
        assertThat(GitFacts.originUrl(main.toFile())).isNull();
        assertThat(GitFacts.originUrl(tmp.resolve("nowhere").toFile())).isNull();
        assertThat(GitFacts.originUrl((File) null)).isNull();
    }
}
