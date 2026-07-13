package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool output is untrusted: a captured digit run bigger than an int
 * must clamp, not throw inside an output listener (safeAccept would
 * contain the throw, but the line's counts and diagnostic are lost).
 */
class NumbersTest {

    @Test
    @DisplayName("normal, huge, and absurd digit runs never throw")
    void clampsAndZeroes() {
        assertThat(Numbers.intOrZero("42")).isEqualTo(42);
        assertThat(Numbers.intOrZero("2147483647")).isEqualTo(Integer.MAX_VALUE);
        assertThat(Numbers.intOrZero("99999999999")).isEqualTo(Integer.MAX_VALUE); // > int, fits long
        assertThat(Numbers.intOrZero("99999999999999999999")).isZero();            // > long
        assertThat(Numbers.intOrZero("")).isZero();
    }

    @TempDir
    Path dir;

    @Test
    @DisplayName("PURITY survives a poisoned biome line-number without dropping the run's parse")
    void poisonedLineNumberSurvives(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("package.json"), "{}");
        Files.writeString(project.resolve("biome.json"), "{}");
        Files.writeString(project.resolve("index.js"), "var x = 1;\n");
        Rack rack = new Rack();
        rack.setProjectDir(project.toFile());
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            lint.applyState(java.util.Map.of("linter", "2")); // biome, explicit
            lint.beginParseForTest();
            lint.onLine("index.js:99999999999999999999:1 lint/style/noVar"); // > long
            lint.onLine("Found 1 error.");
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            // the poisoned line clamps to 0 instead of throwing, and the
            // summary that FOLLOWS it still lands on the LCD
            assertThat(lint.lcdTextForTest()).isEqualTo("E:1 W:0");
        } finally {
            rack.shutdown();
        }
    }
}
