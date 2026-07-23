package org.nmox.studio.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 57/58/59 closed in one sweep — the core review's three LOWs:
 * atomic rewrites keep an existing file's permissions, version compare
 * survives raw/suffixed input, and the git first-line read is bounded.
 */
class CoreLowSweepTest {

    // ---- 57: AtomicFiles keeps an existing target's permissions ----

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("Rewriting a 0644 file atomically leaves it 0644, not the temp's 0600")
    void rewriteKeepsExistingPerms(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("workspace.json");
        Files.writeString(f, "{}");
        Set<PosixFilePermission> shared = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(f, shared);

        AtomicFiles.writeString(f, "{\"v\":2}");

        assertThat(Files.readString(f)).isEqualTo("{\"v\":2}");
        assertThat(Files.getPosixFilePermissions(f))
                .as("the rewrite must not silently narrow a shared file (ledger 57)")
                .isEqualTo(shared);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("A file created fresh stays owner-only — tighter is the safe default")
    void freshFileStaysPrivate(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("new.json");
        AtomicFiles.writeString(f, "{}");
        assertThat(Files.getPosixFilePermissions(f))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    // ---- 58: Versions.compare on non-normalized input ----

    @Test
    @DisplayName("Suffixed and raw versions compare by their numeric spine, never throw")
    void compareSurvivesSuffixes() {
        assertThat(Versions.compare("1.24.0-rc1", "1.24.0")).isZero();
        assertThat(Versions.compare("1.24.10-beta", "1.24.9")).isPositive();
        assertThat(Versions.compare("2.0.0+build5", "1.99.99")).isPositive();
        assertThat(Versions.compare("1.x.0", "1.0.0")).isZero(); // non-numeric → 0
    }

    @Test
    @DisplayName("An absurdly long digit run clamps instead of overflowing")
    void compareClampsHugeSegments() {
        assertThat(Versions.compare("1." + "9".repeat(40) + ".0", "1.0.0")).isPositive();
        assertThat(Versions.compare("1.0.0", "1." + "9".repeat(40) + ".0")).isNegative();
    }

    @Test
    @DisplayName("Normalized comparisons are unchanged — the existing callers see no difference")
    void compareNormalizedUnchanged() {
        assertThat(Versions.compare("1.112.0", "1.113.0")).isNegative();
        assertThat(Versions.compare("1.113.0", "1.113.0")).isZero();
        assertThat(Versions.compare("2.0", "1.99.99")).isPositive();
    }

    // ---- 59: GitFacts bounded first-line read ----

    @Test
    @DisplayName("A crafted multi-megabyte .git HEAD costs 4 KB, and the branch still resolves")
    void hugeHeadIsReadBounded(@TempDir Path dir) throws Exception {
        Path gitDir = dir.resolve(".git");
        Files.createDirectories(gitDir);
        // an honest first line followed by megabytes of adversarial padding
        StringBuilder sb = new StringBuilder("ref: refs/heads/main\n");
        sb.append("x".repeat(5_000_000));
        Files.writeString(gitDir.resolve("HEAD"), sb.toString());

        assertThat(GitFacts.branch(dir.toFile()))
                .as("the first line wins regardless of the payload behind it")
                .isEqualTo("main");
    }

    @Test
    @DisplayName("A first line longer than the cap degrades to null, never a mis-parse")
    void oversizedFirstLineDegradesHonestly(@TempDir Path dir) throws Exception {
        Path gitDir = dir.resolve(".git");
        Files.createDirectories(gitDir);
        // no newline within the cap: the prefix is not a valid ref line
        Files.writeString(gitDir.resolve("HEAD"),
                "ref: refs/heads/" + "a".repeat(GitFacts.FIRST_LINE_CAP * 2));

        // a >4KB branch name is not a real repo state; hiding beats lying
        assertThat(GitFacts.branch(dir.toFile())).isNull();
    }
}
