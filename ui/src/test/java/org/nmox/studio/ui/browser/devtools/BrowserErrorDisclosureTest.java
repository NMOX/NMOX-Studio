package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Explain-error disclosure's caps and honesty: the consent line
 * states literally what leaves, the excerpt marks the failing line
 * and caps minified monsters, and a stale line number degrades to a
 * spoken note rather than an exception.
 */
class BrowserErrorDisclosureTest {

    @Test
    @DisplayName("the consent line is the literal truth, both shapes")
    void whatIsTruthful(@TempDir Path work) {
        assertThat(BrowserErrorDisclosure.what(null, 0))
                .contains("error message only").contains("no source");
        assertThat(BrowserErrorDisclosure.what(new File(work.toFile(), "app.js"), 12))
                .contains("7 lines of app.js").contains("line 12");
    }

    @Test
    @DisplayName("the excerpt marks the line, keeps ±3, caps a minified line")
    void excerptShape(@TempDir Path work) throws Exception {
        File f = new File(work.toFile(), "app.js");
        StringBuilder src = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            src.append(i == 10 ? "x".repeat(500) : "line" + i).append('\n');
        }
        Files.writeString(f.toPath(), src);
        String body = BrowserErrorDisclosure.body("boom", f, 8);
        assertThat(body).contains(">> 8: line8");
        assertThat(body).contains("   5: line5").contains("   11: ");
        assertThat(body).doesNotContain("line4").doesNotContain("line12");
        assertThat(body)
                .as("the 500-char minified line is capped, marked")
                .contains("…[truncated]");
    }

    @Test
    @DisplayName("a line past the file's end speaks, never throws")
    void staleLineSpeaks(@TempDir Path work) throws Exception {
        File f = new File(work.toFile(), "app.js");
        Files.writeString(f.toPath(), "only\n");
        assertThat(BrowserErrorDisclosure.body("boom", f, 40))
                .contains("past the file's end");
    }
    @Test
    @DisplayName("Explain picks the LAST located error; message-only as the fallback; null when clean")
    void explainTargetChoice(@TempDir Path work) {
        java.io.File f = new java.io.File(work.toFile(), "a.js");
        var located = java.util.List.of(
                new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(f, 3, "first", true),
                new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(f, 9, "second", true));
        var t1 = RuntimeErrors.pickExplainTarget(located, java.util.List.of("consoleErr"));
        assertThat(t1.message()).isEqualTo("second");
        assertThat(t1.line()).isEqualTo(9);
        var t2 = RuntimeErrors.pickExplainTarget(java.util.List.of(),
                java.util.List.of("older", "newest"));
        assertThat(t2.message()).isEqualTo("newest");
        assertThat(t2.file()).isNull();
        assertThat(RuntimeErrors.pickExplainTarget(java.util.List.of(), java.util.List.of()))
                .isNull();
    }

}
