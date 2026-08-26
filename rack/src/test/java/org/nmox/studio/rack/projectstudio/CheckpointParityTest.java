package org.nmox.studio.rack.projectstudio;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeded checkpoints can't rot (v2.39.1, the ExperimentGuideParityTest
 * pattern): a file checkpoint must name a file the space actually
 * ships, and a command checkpoint's tool must be one the space
 * already declares (its driver command or install hints) — a
 * checkpoint demanding a tool the space never mentions would fail
 * every learner who followed the tutorial exactly.
 */
class CheckpointParityTest {

    @Test
    @DisplayName("every seeded checkpoint's claims are grounded in its own space")
    void seededCheckpointsGrounded() {
        int seen = 0;
        for (LearningCatalog.Space space : LearningCatalog.all()) {
            for (Checkpoints.Checkpoint c : space.checkpoints()) {
                seen++;
                if (c.isFileKind()) {
                    List<String> shipped = space.files().stream()
                            .map(LearningCatalog.SampleFile::path).toList();
                    assertThat(shipped)
                            .as("%s / %s: file checkpoint targets a shipped file",
                                    space.slug(), c.label())
                            .contains(c.filePath());
                } else {
                    String tool = c.command().get(0);
                    String declared = String.join(" ", space.driver().command())
                            + " " + String.join(" ", space.install().values());
                    assertThat(declared)
                            .as("%s / %s: command tool '%s' must be one the space declares",
                                    space.slug(), c.label(), tool)
                            .contains(tool);
                }
            }
        }
        assertThat(seen)
                .as("the flagship seeds exist — an empty pass proves nothing")
                .isGreaterThanOrEqualTo(6);
    }
}
