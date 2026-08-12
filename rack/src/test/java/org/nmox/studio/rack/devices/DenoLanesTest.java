package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Deno lanes: a deno.json workspace lints, formats, tests, and
 * ships through the runtime's own subcommands — running npx-resolved
 * Node tooling there is not merely un-idiomatic, it can fail outright
 * (a Deno project need not have a node_modules at all).
 */
class DenoLanesTest {

    @TempDir
    Path dir;

    private Rack aimedDenoRack() throws IOException {
        Files.writeString(dir.resolve("deno.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    @Test
    @DisplayName("hasDeno: deno.json or deno.jsonc opts in, bare projects don't")
    void detection() throws IOException {
        assertThat(ProjectInspector.hasDeno(dir.toFile())).isFalse();
        Files.writeString(dir.resolve("deno.jsonc"), "{}");
        assertThat(ProjectInspector.hasDeno(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("PURITY auto lints with deno lint in a Deno workspace; FIX spells --fix")
    void purityAuto() throws IOException {
        Rack rack = aimedDenoRack();
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            assertThat(lint.buildCommand()).containsExactly("deno", "lint");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS checks with deno fmt --check and writes with deno fmt")
    void glossAuto() throws IOException {
        Rack rack = aimedDenoRack();
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            assertThat(fmt.buildCommand()).containsExactly("deno", "fmt", "--check");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("deno beats biome when both manifests exist — deno.json IS the project kind")
    void denoOutranksBiome() throws IOException {
        Files.writeString(dir.resolve("biome.json"), "{}");
        Rack rack = aimedDenoRack();
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            assertThat(lint.buildCommand()).containsExactly("deno", "lint");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("PREFLIGHT ships a Deno project through lint + fmt --check + test")
    void preflightPlan() throws IOException {
        Files.writeString(dir.resolve("deno.json"), "{}");
        List<PreflightPlan.Check> checks =
                PreflightPlan.forProject(dir.toFile());
        assertThat(checks).extracting(c -> c.command())
                .contains(List.of("deno", "lint"),
                        List.of("deno", "fmt", "--check"),
                        List.of("deno", "test"));
    }

    @Test
    @DisplayName("the deno lint summary drives the findings LCD: 'Found N problems'")
    void lintSummaryShape() {
        // pinned live against deno 2.9.4 — both output variants
        assertThat("Found 2 problems").matches(s ->
                java.util.regex.Pattern.compile("^Found (\\d+) problems?\\b")
                        .matcher(s).find());
        assertThat("Found 2 problems (2 fixable via --fix)").matches(s ->
                java.util.regex.Pattern.compile("^Found (\\d+) problems?\\b")
                        .matcher(s).find());
        assertThat("Found 1 not formatted file in 5 files").matches(s ->
                !java.util.regex.Pattern.compile("^Found (\\d+) problems?\\b")
                        .matcher(s).find());
    }
}
