package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wizard's own words for what is already at the target path.
 *
 * <p>The case this class exists for: a target that is a BROKEN symbolic
 * link. {@code File.exists()} follows the link, finds nothing and says
 * false, so the wizard used to accept it — and then
 * {@code Files.createDirectories} threw {@code FileAlreadyExistsException},
 * because a broken link is a directory entry like any other. Its
 * {@code getMessage()} is bare the path, so the dialog showed "Could not
 * create the project: /…/my-app": the operating system's words for a
 * refusal this product should be making itself.
 */
class NewProjectTargetStateTest {

    /**
     * A platform that refuses symbolic links has nothing to prove here —
     * but it must SAY so. A bare {@code return} would make the whole test
     * body vanish under a green tick, which is the v2.186.0
     * {@code argvPinned} defect.
     */
    private static void assumeLinked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            org.junit.jupiter.api.Assumptions.abort(
                    "this platform will not create symbolic links: " + noSymlinks);
        }
    }

    @Test
    @DisplayName("nothing there is FREE — including when the parent does not exist yet")
    void nothingThereIsFree(@TempDir Path tmp) {
        assertThat(NewProjectDialog.targetState(tmp.resolve("my-app").toFile()))
                .isEqualTo(NewProjectDialog.Target.FREE);
        assertThat(NewProjectDialog.targetState(
                tmp.resolve("not/made/yet/my-app").toFile()))
                .isEqualTo(NewProjectDialog.Target.FREE);
    }

    @Test
    @DisplayName("a real directory or file is OCCUPIED — the refusal that already worked")
    void somethingRealIsOccupied(@TempDir Path tmp) throws Exception {
        Files.createDirectory(tmp.resolve("dir"));
        Files.writeString(tmp.resolve("file"), "x");
        assertThat(NewProjectDialog.targetState(tmp.resolve("dir").toFile()))
                .isEqualTo(NewProjectDialog.Target.OCCUPIED);
        assertThat(NewProjectDialog.targetState(tmp.resolve("file").toFile()))
                .isEqualTo(NewProjectDialog.Target.OCCUPIED);
    }

    @Test
    @DisplayName("a link that still RESOLVES is OCCUPIED — that case was never wrong")
    void aResolvingLinkIsStillOccupied(@TempDir Path tmp) throws Exception {
        Path real = Files.createDirectory(tmp.resolve("real"));
        assumeLinked(tmp.resolve("link"), real);
        assertThat(NewProjectDialog.targetState(tmp.resolve("link").toFile()))
                .as("exists() followed this one correctly, and still does")
                .isEqualTo(NewProjectDialog.Target.OCCUPIED);
    }

    @Test
    @DisplayName("a BROKEN link is its own answer, not FREE and not OCCUPIED")
    void aBrokenLinkIsNamedForWhatItIs(@TempDir Path tmp) {
        assumeLinked(tmp.resolve("my-app"), tmp.resolve("gone"));
        File dir = tmp.resolve("my-app").toFile();

        assertThat(dir.exists())
                .as("the old check: exists() FOLLOWS the link, so the wizard"
                        + " waved this straight through to createDirectories")
                .isFalse();
        assertThat(NewProjectDialog.targetState(dir))
                .isEqualTo(NewProjectDialog.Target.DANGLING_LINK);
    }

    @Test
    @DisplayName("and what the old path handed the user was the OS's sentence")
    void theRefusalItReplacesWasTheOperatingSystems(@TempDir Path tmp) {
        assumeLinked(tmp.resolve("my-app"), tmp.resolve("gone"));
        Path dir = tmp.resolve("my-app");

        // the measurement, kept as an assertion: this is what reached
        // NewProjectDialog_createFailed("Could not create the project: {0}")
        try {
            Files.createDirectories(dir);
            org.junit.jupiter.api.Assertions.fail(
                    "expected createDirectories to refuse a broken link");
        } catch (java.io.IOException expected) {
            assertThat(expected)
                    .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            assertThat(expected.getMessage())
                    .as("a bare path, naming no cause and offering no way out")
                    .isEqualTo(dir.toString());
        }
    }

    @Test
    @DisplayName("the three states are the three the dialog renders — no silent fourth")
    void everyStateHasASentence() throws Exception {
        // the FREE arm proceeds; the other two must each name a key, or a
        // new state would fall into the wrong refusal without a compiler
        // or a test noticing
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "NewProjectDialog.java"),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src).contains("Bundle.NewProjectDialog_danglingLink(dir.getName())");
        assertThat(src).contains("Bundle.NewProjectDialog_alreadyExists(dir.getName())");
        assertThat(NewProjectDialog.Target.values())
                .as("a new state needs its own sentence before it can ship")
                .hasSize(3);
    }
}
