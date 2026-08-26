package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The experiment lifecycle contract: throwaway means no git and a
 * marker; promote means move + un-mark; discard refuses anything that
 * is not marked - it must never become a general-purpose rm -rf.
 */
class ExperimentsTest {

    @Test
    @DisplayName("info reads what the marker recorded; broken markers degrade to ?")
    void infoReadsTheMarker(@TempDir Path work) throws IOException {
        File exp = new File(work.toFile(), "exp");
        Files.createDirectories(exp.toPath());
        Files.writeString(new File(exp, Experiments.MARKER).toPath(),
                "created=2026-07-03\ntemplate=VANILLA\n");
        assertThat(Experiments.info(exp))
                .isEqualTo(new Experiments.Info("2026-07-03", "VANILLA"));

        File bare = new File(work.toFile(), "bare");
        Files.createDirectories(bare.toPath());
        assertThat(Experiments.info(bare))
                .as("no marker at all: placeholders, never an exception")
                .isEqualTo(new Experiments.Info("?", "?"));
    }

    @Test
    @DisplayName("create() actually writes the guide — the wiring half of the seam (v1.321.0 law)")
    void createWiresTheGuideWrite() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/Experiments.java"));
        assertThat(src)
                .as("a green writeGuide test with no call site is a payload without a gate")
                .contains("writeGuide(dir, template);");
    }

    @Test
    @DisplayName("an experiment is born with its walkthrough — EXPERIMENT.md, template-specific")
    void writeGuideWritesTheWalkthrough(@TempDir Path work) throws IOException {
        File dir = new File(work.toFile(), "try-express");
        Files.createDirectories(dir.toPath());
        Experiments.writeGuide(dir, ProjectTemplates.EXPRESS_API);
        File guide = new File(dir, Experiments.GUIDE);
        assertThat(guide).isFile();
        assertThat(Files.readString(guide.toPath()))
                .as("the guide teaches THIS stack, not a generic blurb")
                .contains("try-express — an experiment")
                .contains("Test in API Studio")
                .contains("File ▸ Experiments…");
    }

    @Test
    @DisplayName("Duplicate forks the whole tree under a fresh name; the source is untouched")
    void duplicateForks(@TempDir Path work) throws IOException {
        // save-and-RESTORE: clearProperty would leave user.home NULL for
        // every later test in this JVM — the tri-lane cascade of
        // 2026-08-26, where the errors landed in whichever class ran
        // next on each OS (the fork-reorder trap, again)
        String realHome = System.getProperty("user.home");
        System.setProperty("user.home", work.toFile().getAbsolutePath());
        try {
            File exps = new File(work.toFile(), ".nmox/experiments");
            File exp = new File(exps, "probe");
            Files.createDirectories(new File(exp, "src").toPath());
            Files.writeString(new File(exp, Experiments.MARKER).toPath(),
                    "created=2026-08-01\ntemplate=VANILLA\n");
            Files.writeString(new File(exp, "src/app.js").toPath(), "one");

            File fork = Experiments.duplicate(exp);

            assertThat(fork.getName()).isEqualTo("probe-2");
            assertThat(new File(fork, "src/app.js")).hasContent("one");
            assertThat(Files.readString(new File(fork, Experiments.MARKER).toPath()))
                    .contains("forkedFrom=probe")
                    .contains("template=VANILLA");
            assertThat(new File(exp, "src/app.js")).hasContent("one");

            File notAnExperiment = new File(work.toFile(), "plain");
            Files.createDirectories(notAnExperiment.toPath());
            assertThatThrownBy(() -> Experiments.duplicate(notAnExperiment))
                    .isInstanceOf(IOException.class);
        } finally {
            System.setProperty("user.home", realHome);
        }
    }

    @Test
    @DisplayName("Promote moves the tree, drops the marker, keeps the files")
    void promoteGraduatesTheExperiment(@TempDir Path work) throws IOException {
        File exp = new File(work.toFile(), "exp");
        Files.createDirectories(exp.toPath());
        Files.writeString(new File(exp, Experiments.MARKER).toPath(), "created=today\n");
        Files.writeString(new File(exp, "index.html").toPath(), "<html/>");

        File dest = Experiments.promote(exp, new File(work.toFile(), "projects"));

        assertThat(exp).doesNotExist();
        assertThat(new File(dest, "index.html")).exists();
        assertThat(new File(dest, Experiments.MARKER)).doesNotExist();
    }

    @Test
    @DisplayName("Promote and discard refuse unmarked directories")
    void refusesUnmarkedDirectories(@TempDir Path work) throws IOException {
        File notAnExperiment = new File(work.toFile(), "real-project");
        Files.createDirectories(notAnExperiment.toPath());
        Files.writeString(new File(notAnExperiment, "precious.txt").toPath(), "data");

        assertThatThrownBy(() -> Experiments.discard(notAnExperiment))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Experiments.promote(notAnExperiment, work.toFile()))
                .isInstanceOf(IOException.class);
        assertThat(new File(notAnExperiment, "precious.txt")).exists();
    }

    @Test
    @DisplayName("Discard deletes a marked tree completely")
    void discardDeletesMarkedTree(@TempDir Path work) throws IOException {
        File exp = new File(work.toFile(), "exp");
        Files.createDirectories(new File(exp, "src").toPath());
        Files.writeString(new File(exp, Experiments.MARKER).toPath(), "created=today\n");
        Files.writeString(new File(exp, "src/app.js").toPath(), "console.log(1)");

        Experiments.discard(exp);

        assertThat(exp).doesNotExist();
    }

    @Test
    @DisplayName("an aimed discard re-aims at the known-good home, AFTER the delete (v1.290.0 law)")
    void aimedDiscardReaims() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/Experiments.java"));
        int delete = src.indexOf("deleteTree(experiment.toPath());");
        int reaim = src.indexOf("service.openProjectQuietly(LearningSpace.fallbackWorkspace());");
        assertThat(delete).as("the delete exists").isGreaterThan(0);
        assertThat(reaim)
                .as("the re-aim exists and runs AFTER the delete — aiming first lets "
                        + "watchers race the removal (caught live in the v2.36.1 walk)")
                .isGreaterThan(delete);
    }

    @Test
    @DisplayName("the shelf summary teaches the lifecycle with real numbers")
    void shelfSummarySpeaks() {
        assertThat(Experiments.shelfSummary(1, 900))
                .isEqualTo("1 experiment · 900 B on disk — discard what you're done with, promote what grew up.");
        assertThat(Experiments.shelfSummary(3, 5L * 1024 * 1024))
                .startsWith("3 experiments · 5 MB on disk");
        assertThat(Experiments.shelfSummary(2, 2L * 1024 * 1024 * 1024))
                .startsWith("2 experiments · 2.0 GB on disk");
    }

    @Test
    @DisplayName("sizeOf walks the tree and sums file bytes")
    void sizeOfSumsTheTree(@TempDir Path work) throws IOException {
        File exp = new File(work.toFile(), "exp");
        Files.createDirectories(new File(exp, "sub").toPath());
        Files.writeString(new File(exp, "a.txt").toPath(), "12345");
        Files.writeString(new File(exp, "sub/b.txt").toPath(), "123");
        assertThat(Experiments.sizeOf(exp)).isEqualTo(8);
    }

    @Test
    @DisplayName("isExperiment is exactly the marker check")
    void markerIsTheContract(@TempDir Path work) throws IOException {
        File dir = new File(work.toFile(), "d");
        Files.createDirectories(dir.toPath());
        assertThat(Experiments.isExperiment(dir)).isFalse();
        Files.writeString(new File(dir, Experiments.MARKER).toPath(), "x");
        assertThat(Experiments.isExperiment(dir)).isTrue();
    }
}
