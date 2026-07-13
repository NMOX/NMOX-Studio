package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.61.0 promise: a biome.json means the project lints and formats
 * with Biome — PURITY and GLOSS run the project's own toolchain, the
 * same respect-their-contract rule as v1.60.0's package managers.
 */
class BiomeLanesTest {

    @TempDir
    Path dir;

    private Rack aimedRack(boolean biome) throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        if (biome) {
            Files.writeString(dir.resolve("biome.json"), "{}");
        }
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    @Test
    @DisplayName("hasBiome: biome.json or biome.jsonc opts in, bare projects don't")
    void detection() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(ProjectInspector.hasBiome(dir.toFile())).isFalse();
        Files.writeString(dir.resolve("biome.jsonc"), "{}");
        assertThat(ProjectInspector.hasBiome(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("PURITY auto lints with biome when biome.json exists, eslint when not")
    void purityAuto() throws IOException {
        Rack rack = aimedRack(true);
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            assertThat(lint.buildCommand())
                    .containsExactly("npx", "@biomejs/biome", "lint", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("PURITY auto without biome.json stays eslint; FIX spells --fix")
    void purityAutoEslint() throws IOException {
        Rack rack = aimedRack(false);
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            lint.applyState(Map.of("fix", "true"));
            assertThat(lint.buildCommand())
                    .containsExactly("npx", "eslint", ".", "--fix");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("PURITY biome FIX spells --write, not --fix")
    void purityBiomeFix() throws IOException {
        Rack rack = aimedRack(true);
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            lint.applyState(Map.of("fix", "true"));
            assertThat(lint.buildCommand())
                    .containsExactly("npx", "@biomejs/biome", "lint", ".", "--write");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a saved patch that pinned eslint keeps eslint even in a biome project — auto was APPENDED")
    void savedPatchKeepsPinnedLinter() throws IOException {
        Rack rack = aimedRack(true);
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            lint.applyState(Map.of("linter", "0")); // legacy index 0 = eslint
            assertThat(lint.buildCommand()).containsExactly("npx", "eslint", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS formats with biome when biome.json exists — WRITE and CHECK modes")
    void glossBiome() throws IOException {
        Rack rack = aimedRack(true);
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            assertThat(fmt.buildCommand())
                    .containsExactly("npx", "@biomejs/biome", "format", "--write", ".");
            fmt.applyState(Map.of("write", "false"));
            assertThat(fmt.buildCommand())
                    .containsExactly("npx", "@biomejs/biome", "format", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS without biome.json stays prettier")
    void glossPrettier() throws IOException {
        Rack rack = aimedRack(false);
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            assertThat(fmt.buildCommand())
                    .containsExactly("npx", "prettier", "--write", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("PURITY parses biome output: findings collected, LCD counts from the summary")
    void biomeOutputParse() throws Exception {
        Rack rack = aimedRack(true);
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            Files.writeString(dir.resolve("index.js"), "var x = 1 == 2;\n");

            // arm a biome run without spawning: primaryAction resolves the
            // active linter then launches; we drive onLine directly instead
            lint.applyState(Map.of("linter", "2")); // index 2 = biome, explicit
            lint.beginParseForTest();
            lint.onLine("index.js:1:9 lint/suspicious/noDoubleEquals ━━━━━━━━━━━");
            lint.onLine("  × Use === instead of ==.");
            lint.onLine("Checked 1 file in 4ms. No fixes applied.");
            lint.onLine("Found 1 error.");
            lint.onLine("Found 2 warnings.");
            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            assertThat(lint.collectedForTest()).hasSize(1);
            assertThat(lint.collectedForTest().get(0).line()).isEqualTo(1);
            assertThat(lint.lcdTextForTest()).isEqualTo("E:1 W:2");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("PREFLIGHT counts biome.json as a lint config")
    void preflightBiomeConfig() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("biome.json"), "{}");
        assertThat(PreflightPlan.hasLintConfig(dir.toFile())).isTrue();
    }
}
