package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wizard's default location skips internal homes: a learning space
 * under ~/.nmox/learn is a recent PROJECT but never where a user keeps
 * new work (found live — a fresh gleam tutorial made the wizard default
 * to ~/.nmox/learn).
 */
class NewProjectDefaultLocationTest {

    private static final File HOME = new File(System.getProperty("user.home"));

    @Test
    @DisplayName("a learning space as the most-recent project does not become the default")
    void skipsInternalHomes() {
        File learnSpace = new File(HOME, ".nmox/learn/gleam");
        File real = new File(HOME, "NMOX/orders-api");
        assertThat(NewProjectDialog.defaultLocationFrom(List.of(learnSpace, real)))
                .isEqualTo(new File(HOME, "NMOX"));
    }

    @Test
    @DisplayName("only internal recents → the ~/NMOX workspace fallback")
    void allInternalFallsBack() {
        File learnSpace = new File(HOME, ".nmox/learn/gleam");
        File experiment = new File(HOME, ".nmox/experiments/scratch");
        assertThat(NewProjectDialog.defaultLocationFrom(List.of(learnSpace, experiment)))
                .isEqualTo(new File(HOME, "NMOX"));
    }

    @Test
    @DisplayName("a real recent project's parent wins, as before")
    void realRecentWins() {
        File real = new File(HOME, "Code/side-project");
        assertThat(NewProjectDialog.defaultLocationFrom(List.of(real)))
                .isEqualTo(new File(HOME, "Code"));
    }
}
