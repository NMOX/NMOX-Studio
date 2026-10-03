package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** One notice per decision: a folder covers its own repository's sub-folders, and no other repository's (3.5.13). */
class TrustNoticesTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void freshSession() {
        TrustNotices.forgetForTest();
    }

    @Test
    @DisplayName("a project inside an announced repository is the same decision; said once")
    void aSubFolderOfTheSameRepositoryIsCovered() throws Exception {
        File repo = Files.createDirectories(tmp.resolve("repo/.git")).getParent().toFile();
        File app = Files.createDirectories(repo.toPath().resolve("packages/app")).toFile();
        assertThat(TrustNotices.firstFor(repo)).isTrue();
        assertThat(TrustNotices.firstFor(app)).as("the repository's notice stands for it").isFalse();
        assertThat(TrustNotices.firstFor(repo)).isFalse();
    }

    @Test
    @DisplayName("a clone under an announced folder is another repository and gets its own notice")
    void aNestedRepositoryIsAnotherDecision() throws Exception {
        File outer = Files.createDirectories(tmp.resolve("src/.git")).getParent().toFile();
        File clone = Files.createDirectories(outer.toPath().resolve("stranger/.git")).getParent().toFile();
        File inside = Files.createDirectories(clone.toPath().resolve("lib")).toFile();
        assertThat(TrustNotices.firstFor(outer)).isTrue();
        assertThat(TrustNotices.firstFor(clone))
                .as("3.5.10 left every clone under an announced folder silent, for git and for its servers").isTrue();
        assertThat(TrustNotices.firstFor(inside)).as("and that notice covers the clone's own folders").isFalse();
    }

    @Test
    @DisplayName("a path that only begins with an announced one is not inside it")
    void aNamePrefixIsNotAParent() throws Exception {
        File foo = Files.createDirectories(tmp.resolve("foo")).toFile();
        File foobar = Files.createDirectories(tmp.resolve("foobar")).toFile();
        assertThat(TrustNotices.firstFor(foo)).isTrue();
        assertThat(TrustNotices.firstFor(foobar)).isTrue();
    }
}
