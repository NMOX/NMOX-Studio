package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drop-in Environment Doctor probes (v1.305.0, the sixth drop-in
 * surface): a {@code .json} file in {@code ~/.nmox/doctor.d} adds a
 * row to the Doctor. Because a probe is a command the Doctor EXECUTES
 * on open — no GO button, no trust prompt between the file and the
 * spawn — the validation law here is the unit's substance: a drop-in
 * may only name a PATH-resolved tool, never a path, and only
 * flag-shaped version arguments.
 */
class UserProbesTest {

    private static final Set<String> NONE = Set.of();

    @Test
    @DisplayName("listing: filename order, defaults applied, purpose stripped")
    void listsInOrderWithDefaults(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("b-tf.json"),
                "{\"tool\":\"terraform\",\"purpose\":\" infra plans \"}",
                StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("a-kc.json"),
                "{\"tool\":\"kubectl\",\"purpose\":\"cluster ops\","
                + "\"args\":[\"version\",\"--client\"],\"install\":\"brew install kubectl\"}",
                StandardCharsets.UTF_8);

        UserProbes.Loaded loaded = UserProbes.loadFrom(tmp.toFile(), NONE);

        assertThat(loaded.skipped()).isEmpty();
        assertThat(loaded.probes()).extracting(UserProbes.Custom::tool)
                .as("filename order, like every drop-in sibling")
                .containsExactly("kubectl", "terraform");
        assertThat(loaded.probes().get(0).args())
                .containsExactly("version", "--client");
        assertThat(loaded.probes().get(1).args())
                .as("no args declared → the --version default")
                .containsExactly("--version");
        assertThat(loaded.probes().get(1).purpose()).isEqualTo("infra plans");
    }

    @Test
    @DisplayName("the tool law: a path is refused — the probe names a tool, never a file")
    void toolMustBeABareName(@TempDir Path tmp) throws Exception {
        // a probe is executed on Doctor open; a path here would let a
        // drop-in point the spawn at an arbitrary binary it chose
        for (String hostile : List.of("/bin/sh", "../../bin/sh",
                "..\\\\evil.exe", "C:/evil.exe", "--version", "")) {
            Files.writeString(tmp.resolve("p.json"),
                    "{\"tool\":\"" + hostile + "\",\"purpose\":\"x\"}",
                    StandardCharsets.UTF_8);
            UserProbes.Loaded loaded = UserProbes.loadFrom(tmp.toFile(), NONE);
            assertThat(loaded.probes())
                    .as("tool %s must disqualify the whole file", hostile)
                    .isEmpty();
            assertThat(loaded.skipped()).hasSize(1);
            assertThat(loaded.skipped().get(0))
                    .contains("p.json").contains("bare binary name");
        }
    }

    @Test
    @DisplayName("the args law: only flag-shaped entries — no paths, spaces, or shell text")
    void argsMustBeFlagShaped(@TempDir Path tmp) throws Exception {
        for (String hostile : List.of("/etc/passwd", "a b", "$(rm -rf .)",
                "--eval=require('fs')", "..", ";ls")) {
            Files.writeString(tmp.resolve("p.json"),
                    "{\"tool\":\"node\",\"purpose\":\"x\",\"args\":["
                    + org.json.JSONObject.quote(hostile) + "]}",
                    StandardCharsets.UTF_8);
            UserProbes.Loaded loaded = UserProbes.loadFrom(tmp.toFile(), NONE);
            assertThat(loaded.probes())
                    .as("arg %s must disqualify the whole file", hostile)
                    .isEmpty();
            assertThat(loaded.skipped()).hasSize(1);
            assertThat(loaded.skipped().get(0)).contains("flag-shaped");
        }
    }

    @Test
    @DisplayName("a tool the product already probes is skipped — one authoritative row")
    void duplicateOfBuiltInIsSkipped(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("git.json"),
                "{\"tool\":\"git\",\"purpose\":\"my own git row\"}",
                StandardCharsets.UTF_8);
        UserProbes.Loaded loaded =
                UserProbes.loadFrom(tmp.toFile(), Set.of("git", "node"));
        assertThat(loaded.probes()).isEmpty();
        assertThat(loaded.skipped().get(0))
                .as("the built-in row carries the version dialect; a second"
                        + " row for the same tool could contradict it")
                .contains("already probed by the product");
    }

    @Test
    @DisplayName("purpose is required — the Used-for column never renders blank")
    void purposeRequired(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("p.json"),
                "{\"tool\":\"terraform\"}", StandardCharsets.UTF_8);
        UserProbes.Loaded loaded = UserProbes.loadFrom(tmp.toFile(), NONE);
        assertThat(loaded.probes()).isEmpty();
        assertThat(loaded.skipped().get(0)).contains("purpose");
    }

    @Test
    @DisplayName("malformed JSON is skipped with a note, never a blocked table")
    void malformedIsSkippedWithNote(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("broken.json"), "{not json",
                StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("ok.json"),
                "{\"tool\":\"terraform\",\"purpose\":\"plans\"}",
                StandardCharsets.UTF_8);
        UserProbes.Loaded loaded = UserProbes.loadFrom(tmp.toFile(), NONE);
        assertThat(loaded.probes()).hasSize(1);
        assertThat(loaded.skipped()).hasSize(1);
        assertThat(loaded.skipped().get(0)).contains("broken.json");
    }

    @Test
    @DisplayName("a missing drop-in dir lists nothing and is never created")
    void missingDirIsEmpty(@TempDir Path tmp) {
        File absent = tmp.resolve("never-created").toFile();
        UserProbes.Loaded loaded = UserProbes.loadFrom(absent, NONE);
        assertThat(loaded.probes()).isEmpty();
        assertThat(loaded.skipped()).isEmpty();
        assertThat(absent).doesNotExist();
    }

    @Test
    @DisplayName("the Doctor action wires the drop-ins and renders the skip notes")
    void doctorActionWiring() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("..", "ui", "src", "main", "java",
                "org", "nmox", "studio", "ui", "actions",
                "EnvironmentDoctorAction.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src)
                .as("the drop-in dir must be scanned, or doctor.d is dead")
                .contains("UserProbes.load(");
        assertThat(src)
                .as("customs must ride the shared probe seam (leash + detailFor)")
                .contains("EnvironmentDoctor.probeCustom(");
        assertThat(src)
                .as("a refused drop-in becomes a VISIBLE row — the"
                        + " skip-with-note law, rendered where the user looks")
                .contains("\"skipped — \" + note");
    }
}
