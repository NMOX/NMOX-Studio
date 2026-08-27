package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** The tutor disclosure: truthful consent line, capped file, no
 *  command output beyond the detail. */
class CheckDisclosureTest {

    private static Checkpoints.Checkpoint fileCk(String label, String path) {
        return new Checkpoints.Checkpoint(label, "the hint", path, "<h1>", null, null, null);
    }

    @Test
    @DisplayName("the consent line counts checks and files literally")
    void whatIsTruthful() {
        var cmd = new Checkpoints.Checkpoint("tests", "run them", null, null, null,
                List.of("go", "test"), null);
        assertThat(CheckDisclosure.what("Go", List.of(cmd)))
                .contains("1 failed check").contains("no file contents");
        assertThat(CheckDisclosure.what("Web", List.of(fileCk("head", "index.html"), cmd)))
                .contains("2 failed checks").contains("your checked file (capped)");
    }

    @Test
    @DisplayName("a file checkpoint carries the learner's file, capped code-point-safe")
    void fileRidesCapped(@TempDir Path work) throws Exception {
        File dir = work.toFile();
        Files.writeString(new File(dir, "index.html").toPath(),
                "é".repeat(5000));
        String body = CheckDisclosure.body(dir, List.of(fileCk("head", "index.html")),
                List.of(new Checkpoints.Result("head", false, "the hint")));
        assertThat(body).contains("✗ head").contains("my index.html");
        assertThat(body).contains("…[truncated]");
        assertThat(body.length()).isLessThan(4600);
    }

    @Test
    @DisplayName("a command checkpoint sends detail only — never output dumps")
    void commandSendsDetailOnly(@TempDir Path work) {
        var cmd = new Checkpoints.Checkpoint("tests", "fix them", null, null, null,
                List.of("cargo", "test"), null);
        String body = CheckDisclosure.body(work.toFile(), List.of(cmd),
                List.of(new Checkpoints.Result("tests", false, "exit 101. fix them")));
        assertThat(body).contains("✗ tests").contains("exit 101");
        assertThat(body).doesNotContain("  my ");
    }
}
