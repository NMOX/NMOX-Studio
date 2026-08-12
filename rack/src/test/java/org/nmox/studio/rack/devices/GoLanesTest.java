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
 * The Go lanes: a Go module lints with vet (or golangci-lint when the
 * project opted in) and formats with gofmt — all in the toolchain's
 * box. The special case is gofmt's exit contract: {@code gofmt -l}
 * exits ZERO even when files need formatting (pinned live on go 1.26),
 * so CHECK mode's verdict reads the OUTPUT, not the exit code.
 */
class GoLanesTest {

    @TempDir
    Path dir;

    private Rack aimedGoRack() throws IOException {
        Files.writeString(dir.resolve("go.mod"), "module x\n\ngo 1.22\n");
        Files.writeString(dir.resolve("main.go"), "package main\n\nfunc main() {}\n");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    @Test
    @DisplayName("hasGolangci: any of the four config spellings opts in")
    void golangciDetection() throws IOException {
        Files.writeString(dir.resolve("go.mod"), "module x\n");
        assertThat(ProjectInspector.hasGolangci(dir.toFile())).isFalse();
        Files.writeString(dir.resolve(".golangci.yml"), "linters: {}\n");
        assertThat(ProjectInspector.hasGolangci(dir.toFile())).isTrue();
    }

    @Test
    @DisplayName("PURITY auto vets a Go module; a .golangci config upgrades to golangci-lint")
    void purityAuto() throws IOException {
        Rack rack = aimedGoRack();
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            assertThat(lint.buildCommand()).containsExactly("go", "vet", "./...");
            // go vet has no autofix — FIX adds nothing rather than lying
            lint.applyState(java.util.Map.of("fix", "true"));
            assertThat(lint.buildCommand()).containsExactly("go", "vet", "./...");

            Files.writeString(dir.resolve(".golangci.yml"), "linters: {}\n");
            lint.applyState(java.util.Map.of("fix", "false"));
            assertThat(lint.buildCommand()).containsExactly("golangci-lint", "run");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS writes with gofmt -w and checks with gofmt -l")
    void glossCommands() throws IOException {
        Rack rack = aimedGoRack();
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            assertThat(fmt.buildCommand()).containsExactly("gofmt", "-w", ".");
            fmt.applyState(java.util.Map.of("write", "false"));
            assertThat(fmt.buildCommand()).containsExactly("gofmt", "-l", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("gofmt -l's verdict is its OUTPUT: any listed file fails despite exit 0")
    void gofmtCheckVerdict() throws IOException {
        Rack rack = aimedGoRack();
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            fmt.beginGoCheckForTest();
            assertThat(fmt.verdictForTest(0))
                    .as("no output, exit 0 — clean").isTrue();
            fmt.feedLineForTest("main.go");
            assertThat(fmt.verdictForTest(0))
                    .as("a listed file is a FAIL even at exit 0").isFalse();
        } finally {
            rack.shutdown();
        }
    }
}
