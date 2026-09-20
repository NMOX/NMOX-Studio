package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.projectstudio.Experiments;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.nmox.studio.rack.projectstudio.LearningSpace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The docs forge's staging fixtures are the product's own artifacts:
 * every marker it writes parses through the product's own reader, every
 * patch it writes is a loadable rack, and the shelf it seeds names spaces
 * the catalogue really has. Mutation-proven: a misspelled marker key
 * reads back as "?" and fails by name.
 */
class DocsStagingTest {

    /** A platform without symlinks has nothing to prove in the walk below. */
    private static boolean linked(java.nio.file.Path from, java.nio.file.Path to) {
        try {
            Files.createSymbolicLink(from, to);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return false;
        }
    }

    @Test
    @DisplayName("a sample file that resolves outside its space is not written, and SAYS so")
    void stagingRefusalSpeaks(@TempDir File home) throws Exception {
        // the rust space's sample lands at src/main.rs, so a src that is a
        // link out of the space is the shape this guard exists for — and a
        // silently skipped sample paints a shot of a space that merely
        // looks incomplete (refusals speak)
        File outside = new File(home, "outside");
        assertThat(outside.mkdirs()).isTrue();
        File spaceDir = new File(new File(new File(home, ".nmox"), "learn"), "rust");
        assertThat(spaceDir.mkdirs()).isTrue();
        if (!linked(spaceDir.toPath().resolve("src"), outside.toPath())) {
            return;
        }

        java.util.List<java.util.logging.LogRecord> said = new java.util.ArrayList<>();
        java.util.logging.Logger log =
                java.util.logging.Logger.getLogger(DocsStaging.class.getName());
        java.util.logging.Handler ear = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) {
                said.add(r);
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(ear);
        try {
            DocsStaging.seedLearningSpaces(home, Map.of("rust", 0));
        } finally {
            log.removeHandler(ear);
        }

        assertThat(new File(outside, "main.rs"))
                .as("never write outside the space, even for a built-in")
                .doesNotExist();
        assertThat(said)
                .as("the skip must be audible — it used to be a bare continue")
                .anySatisfy(r -> {
                    assertThat(r.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                    assertThat(java.text.MessageFormat.format(r.getMessage(), r.getParameters()))
                            .contains("rust")
                            .contains("src/main.rs")
                            .contains("resolves outside");
                });
    }

    @Test
    @DisplayName("the classic site is a generated Classic Web project carrying the Classic Web Bench patch")
    void classicSite(@TempDir File home) throws Exception {
        File dir = DocsStaging.classicSite(home);
        assertThat(dir).isEqualTo(new File(new File(home, "NMOX"), DocsStaging.CLASSIC_NAME));
        assertThat(new File(dir, "index.html")).isFile();
        assertThat(new File(dir, "js/app.js")).isFile();
        JSONObject patch = new JSONObject(Files.readString(new File(dir, RackIO.DEFAULT_FILENAME).toPath()));
        assertThat(patch.getJSONArray("devices").length())
                .as("the Classic Web Bench preset racks several devices").isGreaterThanOrEqualTo(4);
        // DYNAMO's TASK knob needs a real Gruntfile: the kit's own
        assertThat(Files.readString(new File(dir, "Gruntfile.js").toPath()))
                .isEqualTo(org.nmox.studio.rack.projectstudio.ClassicKit.gruntfileFor(dir))
                .contains("grunt");
        // VITALS names the served site, not its Vite default
        boolean vitalsNamesTheSite = false;
        for (int i = 0; i < patch.getJSONArray("devices").length(); i++) {
            JSONObject d = patch.getJSONArray("devices").getJSONObject(i);
            if ("vitals".equals(d.getString("type"))) {
                assertThat(d.getJSONObject("state").getString("url")).isEqualTo(DocsStaging.CLASSIC_URL);
                vitalsNamesTheSite = true;
            }
        }
        assertThat(vitalsNamesTheSite).as("the Classic Web Bench racks VITALS").isTrue();
        // idempotent: a second call keeps the site and refreshes the patch
        assertThat(DocsStaging.classicSite(home)).isEqualTo(dir);
    }

    @Test
    @DisplayName("the experiment carries the product's marker, walkthrough and a VERITAS → KVASIR → MONITOR patch")
    void expressExperiment(@TempDir File home) throws Exception {
        File dir = DocsStaging.expressExperiment(home);
        assertThat(dir).isEqualTo(new File(home, ".nmox/experiments/" + DocsStaging.EXPERIMENT_NAME));
        assertThat(new File(dir, "package.json")).isFile();
        Experiments.Info info = Experiments.info(dir);
        assertThat(info.template()).isEqualTo("EXPRESS_API");
        assertThat(info.created()).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(Files.readString(new File(dir, Experiments.GUIDE).toPath()))
                .startsWith("# " + DocsStaging.EXPERIMENT_NAME + " — an experiment");
        JSONObject patch = new JSONObject(Files.readString(new File(dir, RackIO.DEFAULT_FILENAME).toPath()));
        assertThat(patch.getJSONArray("devices").length()).isEqualTo(3);
        assertThat(patch.getJSONArray("cables").length()).isEqualTo(2);
        assertThat(patch.toString()).contains("\"kvasir\"").contains("\"test\"").contains("\"console\"");
    }

    @Test
    @DisplayName("the seeded shelf lists catalogue spaces the product's own reader parses, dated as asked")
    void seedLearningSpaces(@TempDir File home) throws Exception {
        Map<String, Integer> shelf = new LinkedHashMap<>();
        shelf.put("first-web-page", 0);
        shelf.put("angular", 23);
        shelf.put("no-such-space", 5);
        List<File> spaces = DocsStaging.seedLearningSpaces(home, shelf);
        assertThat(spaces).hasSize(2);
        for (File space : spaces) {
            assertThat(LearningSpace.isLearningSpace(space)).isTrue();
            LearningSpace.Info info = LearningSpace.info(space);
            LearningCatalog.Space catalogued = LearningCatalog.find(info.slug());
            assertThat(catalogued).as("the marker names a catalogue slug").isNotNull();
            assertThat(info.name()).isEqualTo(catalogued.name());
            assertThat(new File(space, "TUTORIAL.md")).isFile();
        }
        assertThat(LearningSpace.info(spaces.get(0)).created())
                .isEqualTo(java.time.LocalDate.now().toString());
        assertThat(LearningSpace.info(spaces.get(1)).created())
                .isEqualTo(java.time.LocalDate.now().minusDays(23).toString());
        // the six the forge seeds all exist in the catalogue
        for (String slug : List.of("first-web-page", "angular", "elm", "nim", "gleam", "lisp-clisp")) {
            assertThat(LearningCatalog.find(slug)).as("forge shelf slug " + slug).isNotNull();
        }
    }
}
