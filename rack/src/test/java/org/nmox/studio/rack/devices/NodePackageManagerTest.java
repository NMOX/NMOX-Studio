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
 * The v1.60.0 promise: AUTO lanes speak the project's OWN package
 * manager. npm run in a pnpm/yarn repo writes a second lockfile and a
 * broken node_modules — detection (corepack pin, then lockfile) must
 * drive every consumer.
 */
class NodePackageManagerTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("no signals → npm (the default)")
    void defaultsToNpm() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(ProjectInspector.nodePackageManager(dir.toFile())).isEqualTo("npm");
    }

    @Test
    @DisplayName("pnpm-lock.yaml → pnpm")
    void pnpmLock() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");
        assertThat(ProjectInspector.nodePackageManager(dir.toFile())).isEqualTo("pnpm");
    }

    @Test
    @DisplayName("yarn.lock → yarn")
    void yarnLock() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("yarn.lock"), "# yarn lockfile v1");
        assertThat(ProjectInspector.nodePackageManager(dir.toFile())).isEqualTo("yarn");
    }

    @Test
    @DisplayName("corepack packageManager pin BEATS a stray lockfile — the pin is the project's contract")
    void corepackPinWins() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"packageManager\": \"yarn@4.5.0\"}");
        Files.writeString(dir.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");
        assertThat(ProjectInspector.nodePackageManager(dir.toFile())).isEqualTo("yarn");
    }

    @Test
    @DisplayName("an unknown packageManager pin is ignored, the lockfile decides")
    void unknownPinFallsThrough() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"packageManager\": \"vlt@1.0.0\"}");
        Files.writeString(dir.resolve("yarn.lock"), "# yarn lockfile v1");
        assertThat(ProjectInspector.nodePackageManager(dir.toFile())).isEqualTo("yarn");
    }

    @Test
    @DisplayName("CRATE AUTO on a pnpm project installs with pnpm, not npm")
    void crateAutoSpeaksPnpm() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            PackageManagerDevice deps = new PackageManagerDevice();
            rack.addDevice(deps);
            assertThat(deps.buildCommand()).containsExactly("pnpm", "install");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("CRATE AUTO honors the corepack yarn pin, including the yarn 'upgrade' verb")
    void crateAutoYarnUpgradeVerb() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"packageManager\": \"yarn@4.5.0\"}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            PackageManagerDevice deps = new PackageManagerDevice();
            rack.addDevice(deps);
            assertThat(deps.cmd("update")).containsExactly("yarn", "upgrade");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("NPM-9000's ENGINE defaults to auto and resolves the detected manager")
    void npm9000AutoEngine() throws Exception {
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\": {\"dev\": \"vite\"}}");
        Files.writeString(dir.resolve("yarn.lock"), "# yarn lockfile v1");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            NpmScriptDevice scripts = new NpmScriptDevice();
            rack.addDevice(scripts);
            scripts.reloadScripts();
            // the SCRIPT knob options land on the EDT; drain before dialing
            // (this exact race false-failed on a loaded ubuntu runner)
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            scripts.applyState(java.util.Map.of("script", "dev"));
            assertThat(scripts.buildCommand()).containsExactly("yarn", "run", "dev");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a saved patch that pinned npm keeps npm — auto was APPENDED, indices stable")
    void savedPatchKeepsPinnedEngine() throws Exception {
        Files.writeString(dir.resolve("package.json"),
                "{\"scripts\": {\"dev\": \"vite\"}}");
        Files.writeString(dir.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            NpmScriptDevice scripts = new NpmScriptDevice();
            rack.addDevice(scripts);
            scripts.reloadScripts();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            scripts.applyState(java.util.Map.of("manager", "0", "script", "dev")); // legacy index 0 = npm
            assertThat(scripts.buildCommand()).containsExactly("npm", "run", "dev");
        } finally {
            rack.shutdown();
        }
    }
}
