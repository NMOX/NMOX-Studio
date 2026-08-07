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
 * Discarding a learning space (v1.289.0, the organize sweep's last
 * creator). Every other named-artifact list grew a removal verb; the
 * spaces under {@code ~/.nmox/learn} could only ever be created, which
 * is why a language tried once stayed in the Workbench's daily PROJECTS
 * list for the life of the install.
 *
 * <p>The delete is irreversible, so its guards are the subject here:
 * the marker is the contract (as it is for experiments), and the space
 * must live directly under the learning-space home — a marker file
 * elsewhere on disk must never authorize a tree delete.
 */
class LearningSpaceDiscardTest {

    private static File markedSpace(Path parent, String name) throws IOException {
        File dir = parent.resolve(name).toFile();
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(new File(dir, LearningSpace.MARKER).toPath(),
                "slug=" + name + "\n", StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "TUTORIAL.md").toPath(), "# hi\n",
                StandardCharsets.UTF_8);
        File nested = new File(dir, "src");
        assertThat(nested.mkdirs()).isTrue();
        Files.writeString(new File(nested, "main.txt").toPath(), "x", StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    @DisplayName("an unmarked directory is refused — this is never a general rm -rf")
    void refusesUnmarkedDirectory(@TempDir Path tmp) throws IOException {
        File plain = tmp.resolve("not-a-space").toFile();
        assertThat(plain.mkdirs()).isTrue();
        Files.writeString(plain.toPath().resolve("keep.txt"), "precious",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> LearningSpace.discard(plain))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Not a learning space");
        assertThat(plain).as("the refusal must not delete anything").exists();
        assertThat(plain.toPath().resolve("keep.txt")).exists();
    }

    @Test
    @DisplayName("a marked directory OUTSIDE the learning home is still refused")
    void refusesMarkedDirectoryOutsideRoot(@TempDir Path tmp) throws IOException {
        // a marker file is cheap to create anywhere; being marked is not
        // enough on its own, or a symlinked or hand-edited path could
        // authorize deleting a tree the product never made
        File impostor = markedSpace(tmp, "impostor");

        assertThatThrownBy(() -> LearningSpace.discard(impostor))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Outside the learning-space home");
        assertThat(impostor).exists();
        assertThat(new File(impostor, "src/main.txt")).exists();
    }

    @Test
    @DisplayName("a real space under the home is deleted whole, nested files included")
    void discardsRealSpace() throws IOException {
        File root = LearningSpace.root();
        Files.createDirectories(root.toPath());
        File space = markedSpace(root.toPath(), "nmox-discard-test-space");
        try {
            assertThat(space).exists();
            LearningSpace.discard(space);
            assertThat(space).as("the tree is gone, nested files and all").doesNotExist();
        } finally {
            if (space.exists()) {
                LearningSpace.discard(space);
            }
        }
        assertThat(root).as("the home itself survives its last space").exists();
    }

    @Test
    @DisplayName("discarding the AIMED space re-aims the studio, after the delete")
    void aimedDiscardReAims() throws IOException {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "LearningSpace.java"),
                StandardCharsets.UTF_8);

        int delete = src.indexOf("Files.deleteIfExists(p)");
        int reAim = src.indexOf("openProjectQuietly(fallbackWorkspace())");
        assertThat(delete).isPositive();
        assertThat(reAim)
                .as("re-aiming BEFORE the tree is gone lets the watchers and the"
                        + " patch autosave race the delete and re-create files"
                        + " under the directory being removed")
                .isGreaterThan(delete);
        // BOTH effects must be guarded: stopping the devices and
        // re-aiming. Asserting the guard merely *appears* is too weak —
        // it passes with the re-aim unguarded, because the panic block
        // still carries one (a surviving mutant caught exactly this).
        assertThat(src.split("if \\(wasAimedHere\\) \\{", -1).length - 1)
                .as("only an aimed discard stops devices and re-aims —"
                        + " dropping some other space must leave the studio"
                        + " and its running work exactly where they were")
                .isEqualTo(2);
        assertThat(src)
                .as("quietly: a discarded space must not re-enter the recents"
                        + " list the caller just cleaned up")
                .contains("openProjectQuietly(");
    }

    @Test
    @DisplayName("the fallback is the ~/NMOX workspace, never $HOME")
    void fallbackIsTheWorkspaceNotHome() {
        File fallback = LearningSpace.fallbackWorkspace();
        assertThat(fallback.getName())
                .as("the v1.33.1 law: aiming at $HOME walks TCC-protected"
                        + " folders and stacks macOS permission prompts")
                .isEqualTo("NMOX");
        assertThat(fallback.getParentFile())
                .isEqualTo(new File(System.getProperty("user.home")));
    }

    @Test
    @DisplayName("the manage action confirms with the safe default and forgets the row")
    void actionUsesSafeDefaultAndForgets() throws IOException {
        String src = Files.readString(Path.of("..", "ui", "src", "main", "java", "org",
                "nmox", "studio", "ui", "actions", "ManageLearningSpacesAction.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .as("a reflexive Enter must not delete a space — the v1.98.0 idiom"
                        + " is the full NotifyDescriptor ctor with NO_OPTION last")
                .contains("NotifyDescriptor.NO_OPTION);");
        assertThat(src)
                .as("a discarded space must leave the recents list, or the"
                        + " Workbench shows a row for a directory that is gone")
                .contains("forgetRecentProject(dir)");
        assertThat(src)
                .as("the tree delete must not run on the paint thread")
                .contains("SPACES_RP.post");
    }
}
