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
    @DisplayName("isExperiment is exactly the marker check")
    void markerIsTheContract(@TempDir Path work) throws IOException {
        File dir = new File(work.toFile(), "d");
        Files.createDirectories(dir.toPath());
        assertThat(Experiments.isExperiment(dir)).isFalse();
        Files.writeString(new File(dir, Experiments.MARKER).toPath(), "x");
        assertThat(Experiments.isExperiment(dir)).isTrue();
    }
}
