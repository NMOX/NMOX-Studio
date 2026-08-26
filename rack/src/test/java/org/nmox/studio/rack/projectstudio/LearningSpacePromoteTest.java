package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The graduation contract (v2.38.8, experiments parity): promote
 * refuses non-spaces and anything outside the real home — the marker
 * is the contract, and a marker elsewhere on disk must not authorize
 * a move — while the move mechanics (tested guard-free via
 * {@code graduate}) carry the files, drop the marker, and refuse an
 * existing destination. The shelf summary and marker reader are
 * pinned alongside.
 */
class LearningSpacePromoteTest {

    private static File markedSpace(Path parent, String name) throws IOException {
        File dir = parent.resolve(name).toFile();
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(new File(dir, LearningSpace.MARKER).toPath(),
                "slug=" + name + "\nname=Elm\ncreated=2026-08-01\n", StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "TUTORIAL.md").toPath(), "# hi\n",
                StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    @DisplayName("promote refuses an unmarked directory and anything outside ~/.nmox/learn")
    void promoteRefusals(@TempDir Path work) throws IOException {
        File notASpace = work.resolve("real-project").toFile();
        assertThat(notASpace.mkdirs()).isTrue();
        assertThatThrownBy(() -> LearningSpace.promote(notASpace, work.toFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Not a learning space");

        // marked, but outside the home: the marker alone must not
        // authorize a move (the discard guard, mirrored)
        File marked = markedSpace(work, "impostor");
        assertThatThrownBy(() -> LearningSpace.promote(marked, work.toFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Not under");
        assertThat(marked).exists();
    }

    @Test
    @DisplayName("graduate moves the tree, drops the marker, keeps the files")
    void graduateMechanics(@TempDir Path work) throws IOException {
        File space = markedSpace(work, "elm-space");
        File destParent = work.resolve("projects").toFile();

        File promoted = LearningSpace.graduate(space, destParent);

        assertThat(space).doesNotExist();
        assertThat(new File(promoted, "TUTORIAL.md")).exists();
        assertThat(new File(promoted, LearningSpace.MARKER)).doesNotExist();
    }

    @Test
    @DisplayName("graduate refuses an existing destination — never a merge, never a clobber")
    void graduateRefusesExistingDest(@TempDir Path work) throws IOException {
        File space = markedSpace(work, "elm-space");
        File destParent = work.resolve("projects").toFile();
        assertThat(new File(destParent, "elm-space").mkdirs()).isTrue();

        assertThatThrownBy(() -> LearningSpace.graduate(space, destParent))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Already exists");
        assertThat(new File(space, "TUTORIAL.md")).exists();
    }

    @Test
    @DisplayName("info reads the marker; the shelf summary teaches the lifecycle")
    void infoAndSummary(@TempDir Path work) throws IOException {
        File space = markedSpace(work, "elm-space");
        LearningSpace.Info info = LearningSpace.info(space);
        assertThat(info.name()).isEqualTo("Elm");
        assertThat(info.created()).isEqualTo("2026-08-01");

        assertThat(LearningSpace.shelfSummary(1, 2048))
                .isEqualTo("1 space · 2 KB on disk — discard what you've"
                        + " finished, promote what grew up.");
        assertThat(LearningSpace.shelfSummary(3, 5L * 1024 * 1024))
                .startsWith("3 spaces · 5 MB");
    }
}
