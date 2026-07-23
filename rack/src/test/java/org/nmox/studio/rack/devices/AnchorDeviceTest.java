package org.nmox.studio.rack.devices;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANCHOR, the Solana console (53rd device): solana-test-validator as a
 * TRUE long-runner (its RPC banner was pinned live against Agave 4.1.2),
 * anchor build/test on the ACTION knob. Unlike STELLAR's detached
 * quickstart, the validator dies with its process — so this device DOES
 * declare a SERVING gate, and it stays truthful.
 */
class AnchorDeviceTest {

    @Test
    @DisplayName("START is solana-test-validator; ACTION dials anchor build/test")
    void commandShapes() {
        AnchorDevice device = new AnchorDevice();
        try {
            assertThat(device.buildCommand())
                    .containsExactly("solana-test-validator");
            assertThat(dialTo(device, "build"))
                    .containsExactly("anchor", "build");
            assertThat(dialTo(device, "test"))
                    .containsExactly("anchor", "test");
        } finally {
            device.dispose();
        }
    }

    private static List<String> dialTo(AnchorDevice device, String action) {
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
    @DisplayName("The SERVING gate is declared — the validator is a real long-runner")
    void servingGateDeclared() {
        AnchorDevice device = new AnchorDevice();
        try {
            assertThat(device.getPorts().stream()
                    .map(org.nmox.studio.rack.model.Port::getId))
                    .contains("serving", "url", "ready", "run", "stop", "enable");
        } finally {
            device.dispose();
        }
    }

    @Test
    @DisplayName("The live-pinned RPC banner registers the chain; stop deregisters")
    void bannerRegistersServing(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        // the exact line a real Agave 4.1.2 validator printed on this
        // machine (2026-07-23): the parse must survive it verbatim
        java.nio.file.Files.writeString(dir.resolve("Cargo.toml"), "[package]");
        org.nmox.studio.rack.model.Rack rack = new org.nmox.studio.rack.model.Rack();
        rack.setProjectDir(dir.toFile());
        try {
            AnchorDevice device = new AnchorDevice();
            rack.addDevice(device);
            device.onLine("JSON RPC URL: http://127.0.0.1:8899");
            assertThat(mine(dir)).extracting(
                    org.nmox.studio.rack.service.ServingRegistry.Serving::url,
                    org.nmox.studio.rack.service.ServingRegistry.Serving::kind)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(
                            "http://127.0.0.1:8899",
                            org.nmox.studio.rack.service.ServingRegistry.Kind.CHAIN));

            device.onFinished(143); // the STOP button's SIGTERM exit
            assertThat(mine(dir)).as("stop deregisters").isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    /** This project's servings only — the registry is a JVM singleton. */
    private static List<org.nmox.studio.rack.service.ServingRegistry.Serving> mine(
            java.nio.file.Path dir) {
        java.io.File f = dir.toFile();
        return org.nmox.studio.rack.service.ServingRegistry.getDefault().snapshot().stream()
                .filter(s -> s.projectDir().equals(f)).toList();
    }
}
