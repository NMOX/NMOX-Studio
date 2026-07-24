package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STELLAR, the Soroban console (52nd device): Stellar smart contracts
 * are Rust-to-WASM driven by the stellar CLI. The command shapes, the
 * soroban-sdk detection (root and the stellar-contract-init workspace
 * layout), and the Cargo.lock version parse are pinned here; the
 * catalog-wide contract tests cover the faceplate laws.
 */
class StellarDeviceTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("BUILD is stellar contract build; the ACTION knob dials test and the quickstart net")
    void commandShapes() {
        StellarDevice device = new StellarDevice();
        try {
            assertThat(device.buildCommand())
                    .containsExactly("stellar", "contract", "build");
            // knob order is the append-only law's business; shapes by value
            assertThat(dialTo(device, "test"))
                    .containsExactly("cargo", "test");
            assertThat(dialTo(device, "net-start"))
                    .containsExactly("stellar", "container", "start", "local");
            assertThat(dialTo(device, "net-stop"))
                    .containsExactly("stellar", "container", "stop", "local");
        } finally {
            device.dispose();
        }
    }

    private static List<String> dialTo(StellarDevice device, String action) {
        org.nmox.studio.rack.ui.controls.Knob knob = device.actionKnob();
        String[] options = knob.getOptions();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(action)) {
                knob.setSelectedIndex(i);
                return device.actionCommand();
            }
        }
        throw new AssertionError("no such action: " + action);
    }

    @Test
    @DisplayName("soroban-sdk in the root Cargo.toml lights the device")
    void detectsRootManifest() throws Exception {
        Files.writeString(root.resolve("Cargo.toml"), """
                [package]
                name = "hello"
                [dependencies]
                soroban-sdk = "23"
                """);
        assertThat(ProjectInspector.hasSorobanSdk(root.toFile())).isTrue();
    }

    @Test
    @DisplayName("the stellar-contract-init workspace layout is detected two levels down")
    void detectsInitWorkspaceLayout() throws Exception {
        Files.writeString(root.resolve("Cargo.toml"), """
                [workspace]
                members = ["contracts/*"]
                """);
        Path member = Files.createDirectories(root.resolve("contracts/hello"));
        Files.writeString(member.resolve("Cargo.toml"), """
                [dependencies]
                soroban-sdk = { workspace = true }
                """);
        assertThat(ProjectInspector.hasSorobanSdk(root.toFile()))
                .as("contracts/<name>/Cargo.toml is the init layout")
                .isTrue();
    }

    @Test
    @DisplayName("a plain Rust project is NOT a Soroban project — the device greys honestly")
    void plainRustIsNotSoroban() throws Exception {
        Files.writeString(root.resolve("Cargo.toml"), """
                [package]
                name = "cli-tool"
                [dependencies]
                serde = "1"
                """);
        assertThat(ProjectInspector.hasSorobanSdk(root.toFile())).isFalse();
    }

    @Test
    @DisplayName("Cargo.lock yields the locked soroban-sdk version")
    void cargoLockVersion() throws Exception {
        Files.writeString(root.resolve("Cargo.lock"), """
                [[package]]
                name = "serde"
                version = "1.0.219"

                [[package]]
                name = "soroban-sdk"
                version = "23.0.2"
                source = "registry+https://github.com/rust-lang/crates.io-index"
                """);
        assertThat(ProjectInspector.cargoLockVersion(root.toFile(), "soroban-sdk"))
                .isEqualTo("23.0.2");
        assertThat(ProjectInspector.cargoLockVersion(root.toFile(), "absent-crate"))
                .isNull();
    }

    @Test
    @DisplayName("Exit attribution follows the LAUNCHED verb, never the knob's current position")
    void exitAttributionFollowsLaunchedVerb() {
        // the v1.135.0 arc-review finding: onFinished used to consult the
        // knob, which the user can turn mid-run — a plain test exit then
        // announced LOCAL NET UP for a network that was never started
        StellarDevice device = new StellarDevice();
        try {
            dialTo(device, "net-start"); // the knob LIES about what ran
            device.setLaunchedVerbForTest("test");
            device.onFinished(0);
            flushEdt(); // status writes marshal to the EDT
            assertThat(device.statusText())
                    .as("a test exit must never announce the local net")
                    .doesNotContain("LOCAL NET UP");

            device.setLaunchedVerbForTest("net-start");
            device.onFinished(0);
            flushEdt();
            assertThat(device.statusText()).contains("LOCAL NET UP");
        } finally {
            device.dispose();
        }
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("No SERVING gate on the plate — the quickstart net outlives the start process")
    void noServingGateDeclared() {
        // `stellar container start local` detaches and exits; a SERVING
        // gate tied to that process would lie the moment it exits 0 (the
        // v1.93.0 serving-truth law). URL + READY flow instead.
        StellarDevice device = new StellarDevice();
        try {
            assertThat(device.getPorts().stream()
                    .map(org.nmox.studio.rack.model.Port::getId))
                    .doesNotContain("serving")
                    .contains("url", "ready", "run", "stop", "enable");
        } finally {
            device.dispose();
        }
    }
}
