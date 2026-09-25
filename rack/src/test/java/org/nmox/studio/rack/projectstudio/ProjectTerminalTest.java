package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⌃` the way VS Code's works (3.1.0): the first press opens a shell in the
 * aimed project, later presses bring the open terminal forward.
 */
class ProjectTerminalTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("an open terminal is brought forward, never a second one spawned")
    void openTerminalWins() {
        assertThat(ProjectTerminal.decide(true, tmp.toFile())).isEqualTo(ProjectTerminal.Choice.FOCUS_EXISTING);
        assertThat(ProjectTerminal.decide(true, null)).isEqualTo(ProjectTerminal.Choice.FOCUS_EXISTING);
    }

    @Test
    @DisplayName("with no terminal open, the shell starts in the aimed project")
    void startsInTheProject() {
        assertThat(ProjectTerminal.decide(false, tmp.toFile())).isEqualTo(ProjectTerminal.Choice.OPEN_IN_PROJECT);
    }

    @Test
    @DisplayName("no project, or one that vanished: the platform's plain terminal")
    void plainWithoutAProject() {
        assertThat(ProjectTerminal.decide(false, null)).isEqualTo(ProjectTerminal.Choice.OPEN_PLAIN);
        assertThat(ProjectTerminal.decide(false, new File(tmp.toFile(), "gone")))
                .isEqualTo(ProjectTerminal.Choice.OPEN_PLAIN);
    }

    @Test
    @DisplayName("after a re-aim, the chord starts a shell in the new project instead of focusing the old one")
    void reaimStartsANewShell() throws Exception {
        File a = java.nio.file.Files.createDirectory(tmp.resolve("a")).toFile();
        File b = java.nio.file.Files.createDirectory(tmp.resolve("b")).toFile();
        assertThat(ProjectTerminal.decide(true, b, a)).as("the aim moved from a to b")
                .isEqualTo(ProjectTerminal.Choice.OPEN_IN_PROJECT);
        assertThat(ProjectTerminal.decide(true, a, a)).as("same project: bring it forward")
                .isEqualTo(ProjectTerminal.Choice.FOCUS_EXISTING);
        assertThat(ProjectTerminal.decide(true, b, null)).as("a terminal the chord did not start: bring it forward")
                .isEqualTo(ProjectTerminal.Choice.FOCUS_EXISTING);
        assertThat(ProjectTerminal.decide(true, null, a)).as("nothing aimed any more: bring it forward")
                .isEqualTo(ProjectTerminal.Choice.FOCUS_EXISTING);
    }

    @Test
    @DisplayName("the action is registered where the chord's shadow points")
    void registered() throws Exception {
        String layer = new String(ProjectTerminal.class.getClassLoader()
                .getResourceAsStream("META-INF/generated-layer.xml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(layer).contains("org-nmox-studio-rack-projectstudio-ProjectTerminalAction.instance");
    }
}
