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
 * Mixed-language repos: multiple toolchains detected with their own
 * directories, ROSETTA steering every AUTO knob, commands running
 * where their manifest lives.
 */
class MixedProjectTest {

    @TempDir
    Path repo;

    /** frontend/package.json + backend/Cargo.toml + root Makefile. */
    private void scaffoldMonorepo() throws IOException {
        Files.createDirectories(repo.resolve("frontend"));
        Files.createDirectories(repo.resolve("backend"));
        Files.writeString(repo.resolve("frontend/package.json"),
                "{\"name\":\"web\",\"scripts\":{\"test\":\"vitest\"}}");
        Files.writeString(repo.resolve("backend/Cargo.toml"), "[package]\nname=\"api\"");
        Files.writeString(repo.resolve("Makefile"), "run:\n\techo hi");
    }

    @Test
    @DisplayName("detectKinds finds every toolchain with its own directory")
    void detectsTheWholeMix() throws IOException {
        scaffoldMonorepo();
        var kinds = ProjectInspector.detectKinds(repo.toFile());

        assertThat(kinds.keySet()).containsExactly(
                ProjectInspector.ProjectKind.NODE,
                ProjectInspector.ProjectKind.RUST,
                ProjectInspector.ProjectKind.MAKE);
        assertThat(kinds.get(ProjectInspector.ProjectKind.NODE))
                .isEqualTo(repo.resolve("frontend").toFile());
        assertThat(kinds.get(ProjectInspector.ProjectKind.RUST))
                .isEqualTo(repo.resolve("backend").toFile());
        assertThat(kinds.get(ProjectInspector.ProjectKind.MAKE))
                .isEqualTo(repo.toFile());
    }

    @Test
    @DisplayName("ROSETTA override steers every AUTO device to the dialed toolchain")
    void rosettaSteersAutoKnobs() throws IOException {
        scaffoldMonorepo();
        Rack rack = new Rack();
        rack.setProjectDir(repo.toFile());
        RunDevice run = new RunDevice();
        rack.addDevice(run);

        // AUTO with no override: Node wins by precedence
        assertThat(run.buildCommand().get(0)).isIn("npm", "node");

        RosettaDevice rosetta = new RosettaDevice();
        rosetta.applyState(Map.of("toolchain", "rust"));
        rack.addDevice(rosetta);

        assertThat(rack.getToolchainOverride()).isEqualTo("RUST");
        assertThat(run.buildCommand()).startsWith("cargo", "run");
        // and cargo runs in the backend directory, not the repo root
        assertThat(run.commandDir()).isEqualTo(repo.resolve("backend").toFile());
        rack.shutdown();
    }

    @Test
    @DisplayName("Explicit device knobs win their own directory regardless of override")
    void explicitKnobsResolveTheirOwnDir() throws IOException {
        scaffoldMonorepo();
        Rack rack = new Rack();
        rack.setProjectDir(repo.toFile());
        TestDevice tests = new TestDevice();
        tests.applyState(Map.of("framework", "7")); // cargo
        rack.addDevice(tests);

        assertThat(tests.buildCommand()).startsWith("cargo", "test");
        assertThat(tests.commandDir()).isEqualTo(repo.resolve("backend").toFile());
        rack.shutdown();
    }

    @Test
    @DisplayName("NPM-9000 reads the monorepo's frontend package.json")
    void npmScriptsFollowNodeDir() throws IOException {
        scaffoldMonorepo();
        Rack rack = new Rack();
        rack.setProjectDir(repo.toFile());
        NpmScriptDevice npm = new NpmScriptDevice();
        rack.addDevice(npm);

        assertThat(npm.commandDir()).isEqualTo(repo.resolve("frontend").toFile());
        rack.shutdown();
    }

    @Test
    @DisplayName("Removing ROSETTA releases the override")
    void disposingRosettaReleasesOverride() throws IOException {
        scaffoldMonorepo();
        Rack rack = new Rack();
        rack.setProjectDir(repo.toFile());
        RosettaDevice rosetta = new RosettaDevice();
        rosetta.applyState(Map.of("toolchain", "go"));
        rack.addDevice(rosetta);
        assertThat(rack.getToolchainOverride()).isEqualTo("GO");

        rack.removeDevice(rosetta);

        assertThat(rack.getToolchainOverride()).isNull();
        rack.shutdown();
    }
}
