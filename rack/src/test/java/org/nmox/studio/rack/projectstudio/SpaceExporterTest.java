package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The exporter's laws: text-only bounded gathering with spoken skips,
 * the round-trip validation gate (the export must parse with the SAME
 * code the student's picker uses), checkpoint refusal at EXPORT time,
 * and the self-owned-only overwrite rule.
 */
class SpaceExporterTest {

    private static SpaceExporter.Options opts() {
        return new SpaceExporter.Options("Intro Express", "First API",
                LearningCatalog.Category.FRAMEWORK, "JavaScript",
                List.of("npm", "run", "dev"));
    }

    @Test
    @DisplayName("gather: text in, binary + heavy dirs + oversize out, each skip spoken")
    void gatherLaws(@TempDir Path work) throws IOException {
        File p = work.toFile();
        Files.writeString(new File(p, "server.js").toPath(), "ok");
        Files.createDirectories(new File(p, "node_modules/x").toPath());
        Files.writeString(new File(p, "node_modules/x/big.js").toPath(), "no");
        Files.write(new File(p, "logo.png").toPath(), new byte[] {1, 0, 2});
        Files.writeString(new File(p, "huge.txt").toPath(), "y".repeat(70_000));
        List<String> skipped = new ArrayList<>();
        List<LearningCatalog.SampleFile> got = SpaceExporter.gather(p, skipped);
        assertThat(got).extracting(LearningCatalog.SampleFile::path)
                .containsExactly("server.js");
        assertThat(skipped).anyMatch(s -> s.contains("binary"));
        assertThat(skipped).anyMatch(s -> s.contains("per-file cap"));
        assertThat(skipped).noneMatch(s -> s.contains("node_modules"));
    }

    @Test
    @DisplayName("export round-trips through the student's own parser, checkpoints intact")
    void exportRoundTrips(@TempDir Path work) throws IOException {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", work.resolve("home").toString());
        try {
            File p = work.resolve("proj").toFile();
            Files.createDirectories(p.toPath());
            Files.writeString(new File(p, "server.js").toPath(), "code");
            Files.writeString(new File(p, "TUTORIAL.md").toPath(), "# Lesson body");
            Files.writeString(new File(p, ".nmox-checkpoints.json").toPath(), """
                [{"label":"It runs","hint":"npm run dev","command":["npm","test"]}]
                """);
            SpaceExporter.Outcome out = SpaceExporter.export(p, opts());
            assertThat(out.slug()).isEqualTo("intro-express");
            assertThat(out.filesIncluded()).isEqualTo(2);
            // the student-side read: the REAL merged loader sees it
            List<LearningCatalog.Space> all =
                    LearningCatalog.allFrom(LearningCatalog.dropInDir());
            LearningCatalog.Space mine = all.stream()
                    .filter(s -> s.slug().equals("intro-express")).findFirst().orElseThrow();
            assertThat(mine.tutorial()).contains("Lesson body");
            assertThat(mine.checkpoints()).hasSize(1);
            assertThat(mine.checkpoints().get(0).label()).isEqualTo("It runs");
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    @DisplayName("a broken checkpoint refuses the WHOLE export, at export time")
    void brokenCheckpointRefuses(@TempDir Path work) throws IOException {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", work.resolve("home").toString());
        try {
            File p = work.resolve("proj").toFile();
            Files.createDirectories(p.toPath());
            Files.writeString(new File(p, "a.js").toPath(), "x");
            Files.writeString(new File(p, ".nmox-checkpoints.json").toPath(), """
                [{"label":"bad","command":["/abs/path/tool"]}]
                """);
            assertThatThrownBy(() -> SpaceExporter.export(p, opts()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Checkpoints refused");
            assertThat(new File(LearningCatalog.dropInDir(), "intro-express.json"))
                    .doesNotExist();
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    @DisplayName("overwrite only over a previous export of the SAME space")
    void selfOwnedOverwrite(@TempDir Path work) throws IOException {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", work.resolve("home").toString());
        try {
            File p = work.resolve("proj").toFile();
            Files.createDirectories(p.toPath());
            Files.writeString(new File(p, "a.js").toPath(), "v1");
            SpaceExporter.export(p, opts());
            Files.writeString(new File(p, "a.js").toPath(), "v2");
            SpaceExporter.Outcome again = SpaceExporter.export(p, opts());
            assertThat(Files.readString(again.file().toPath())).contains("v2");

            File foreign = new File(LearningCatalog.dropInDir(), "other.json");
            Files.writeString(foreign.toPath(),
                    "{\"spaces\":[{\"slug\":\"theirs\"},{\"slug\":\"more\"}]}");
            Files.copy(foreign.toPath(),
                    new File(LearningCatalog.dropInDir(), "intro-express.json").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assertThatThrownBy(() -> SpaceExporter.export(p, opts()))
                    .hasMessageContaining("not overwritten");
        } finally {
            System.setProperty("user.home", home);
        }
    }
    @Test
    @DisplayName("the round-trip gate EXISTS before the write — schema-drift defense, pinned structurally")
    void roundTripGatePinned() throws IOException {
        // the behavioral mutant is EQUIVALENT today: every well-formed
        // Options builds JSON the parser accepts, so no input reaches the
        // refusal. The gate exists for the day the builder and
        // LearningCatalog.parse drift apart — so its PRESENCE and its
        // position (before Files.writeString) are the pinned facts.
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/SpaceExporter.java"));
        int gate = src.indexOf("round-trip parse");
        int write = src.indexOf("Files.writeString(target.toPath()");
        assertThat(gate).as("the gate exists").isGreaterThan(0);
        assertThat(write).as("the write exists").isGreaterThan(gate);
    }

}
