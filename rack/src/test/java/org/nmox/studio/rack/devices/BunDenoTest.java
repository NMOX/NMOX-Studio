package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bun and Deno as first-class toolchains: detection outranks plain
 * Node when their manifests are present, and every AUTO device speaks
 * the right binary.
 */
class BunDenoTest {

    @Test
    @DisplayName("bun.lock beside package.json means Bun, not Node")
    void bunOutranksNode(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("bun.lock"), "");
        assertThat(ProjectInspector.detectKind(dir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.BUN);
    }

    @Test
    @DisplayName("deno.json means Deno; devices run deno test / deno install / deno task")
    void denoDevicesSpeakDeno(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("deno.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        TestDevice tests = new TestDevice();
        rack.addDevice(tests);
        assertThat(tests.buildCommand()).startsWith("deno", "test");
        rack.shutdown();
    }

    @Test
    @DisplayName("Bun project: VERITAS runs bun test; re-run failed uses -t")
    void bunVeritas(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("bun.lock"), "");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        TestDevice tests = new TestDevice();
        rack.addDevice(tests);
        assertThat(tests.buildCommand()).startsWith("bun", "test");
        assertThat(TestDevice.rerunFailedCommand("bun", java.util.List.of("a", "b")))
                .containsExactly("bun", "test", "-t", "a|b");
        assertThat(TestDevice.rerunFailedCommand("deno", java.util.List.of("x")))
                .containsExactly("deno", "test", "--filter", "x");
        rack.shutdown();
    }
}
