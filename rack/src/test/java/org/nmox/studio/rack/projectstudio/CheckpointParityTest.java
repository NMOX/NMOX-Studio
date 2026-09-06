package org.nmox.studio.rack.projectstudio;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /**
     * The outcome law (v2.85.0, the learning-space walk): a file
     * checkpoint is either a TASK — its label starts with "You …" and
     * it must FAIL against the space's own untouched seed, because it
     * exists to tell the learner's work from the sample — or a GUARD
     * ("… is still linked"), which must PASS against the seed. Two
     * seeded tasks passed on day one: the WIT space's absent clause
     * carried a double-escaped newline that no file contains, and the
     * first web page's "third list item" only asked for a substring
     * the two-item seed already had. Check My Work said "nicely done"
     * to a learner who had done nothing. Evaluated through the real
     * runner on the real seed bytes, never by re-deriving the rule.
     */
    @Test
    @DisplayName("a task checkpoint fails on the untouched seed; a guard passes on it")
    void seededFileCheckpointsTellTheSeedFromTheWork(@TempDir java.nio.file.Path work) throws java.io.IOException {
        int tasks = 0;
        int guards = 0;
        for (LearningCatalog.Space space : LearningCatalog.all()) {
            if (space.checkpoints().stream().noneMatch(Checkpoints.Checkpoint::isFileKind)) {
                continue;
            }
            java.io.File seed = work.resolve(space.slug()).toFile();
            for (LearningCatalog.SampleFile f : space.files()) {
                java.nio.file.Path target = seed.toPath().resolve(f.path());
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.writeString(target, f.content());
            }
            for (Checkpoints.Checkpoint c : space.checkpoints()) {
                if (!c.isFileKind()) {
                    continue;
                }
                boolean task = c.label().startsWith("You ");
                Checkpoints.Result onSeed = Checkpoints.run(seed, c, null);
                if (task) {
                    tasks++;
                    assertThat(onSeed.passed())
                            .as("%s / \"%s\": a TASK checkpoint must FAIL on the untouched "
                                    + "seed — one that passes can never tell the work from "
                                    + "the sample (an absent clause the seed never contained, "
                                    + "a contains the seed already satisfies)",
                                    space.slug(), c.label())
                            .isFalse();
                } else {
                    guards++;
                    assertThat(onSeed.passed())
                            .as("%s / \"%s\": a GUARD checkpoint must PASS on the untouched "
                                    + "seed — it exists to say \"still true\"",
                                    space.slug(), c.label())
                            .isTrue();
                }
            }
        }
        assertThat(tasks).as("seeded tasks exist").isGreaterThanOrEqualTo(3);
        assertThat(guards).as("seeded guards exist").isGreaterThanOrEqualTo(2);
    }
}
