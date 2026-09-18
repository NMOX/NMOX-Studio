package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.model.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chain consoles' rear jacks say what they do.
 *
 * <p>STELLAR's ENABLE gated {@code runAction} — the ACTION knob's verb —
 * a contract that held for none of the three (a test exits on its own,
 * net-start detaches a container the low edge could never stop, net-stop
 * is itself a stop); and its RUN fired the knob's verb while the GO
 * button is BUILD, the one console where RUN by cable meant something
 * other than GO. ANCHOR's long-runner (the validator, with URL/READY/
 * SERVING) had no trigger jack at all. The 2026-09-17 rack audit.
 */
class ChainConsoleJacksTest {

    @TempDir
    Path root;

    private final Predicate<java.io.File> originalTrust = CommandDevice.trustCheck;

    @AfterEach
    void restoreTrust() {
        CommandDevice.trustCheck = originalTrust;
    }

    private static void flushEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    private Path sorobanProject() throws Exception {
        Files.writeString(root.resolve("Cargo.toml"), """
                [package]
                name = "hello"
                [dependencies]
                soroban-sdk = "23"
                """);
        return root;
    }

    @Test
    @DisplayName("STELLAR: RUN fires BUILD like every CommandDevice; ACTION fires the knob's verb; no ENABLE")
    void stellarRunIsBuildAndActionIsTheKnob() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(sorobanProject().toFile());
        try {
            StellarDevice stellar = new StellarDevice();
            rack.addDevice(stellar);
            assertThat(stellar.getPorts().stream().map(Port::getId))
                    .contains("run", "action", "stop")
                    .doesNotContain("enable");
            // no CLI on this rack and Keep Safe: BUILD refuses at the CLI probe
            // (its own message), the knob's default verb `cargo test` needs no
            // CLI and refuses one gate later at Workspace Trust — two different
            // sentences, so the jack that fired each path is named by the LCD
            stellar.cliPresent = tool -> false;
            CommandDevice.trustCheck = f -> false;

            stellar.receive(stellar.getPort("run"), Signal.trigger());
            flushEdt();
            assertThat(stellar.statusText()).as("RUN → BUILD (stellar contract build)")
                    .contains("stellar not found");

            stellar.receive(stellar.getPort("action"), Signal.trigger());
            flushEdt();
            assertThat(stellar.statusText()).as("ACTION → the knob's verb (cargo test)")
                    .contains("UNTRUSTED WORKSPACE");
            assertThat(stellar.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ANCHOR: START boots the validator (its long-runner), RUN keeps firing the ACTION verb")
    void anchorStartJack() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(root.toFile());
        try {
            AnchorDevice anchor = new AnchorDevice();
            rack.addDevice(anchor);
            assertThat(anchor.getPorts().stream().map(Port::getId))
                    .contains("start", "stop", "enable", "run");
            anchor.cliPresent = tool -> false; // this box has solana: never boot it from a test

            anchor.receive(anchor.getPort("start"), Signal.trigger());
            flushEdt();
            assertThat(anchor.statusLcd.getText()).as("START → startValidator")
                    .contains("solana-test-validator not found");

            anchor.receive(anchor.getPort("run"), Signal.trigger());
            flushEdt();
            assertThat(anchor.statusLcd.getText()).as("RUN → the ACTION verb (anchor build/test)")
                    .contains("NO Anchor.toml");
            assertThat(anchor.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A patch saved with a cable into STELLAR's removed ENABLE loads: cable skipped, nothing thrown")
    void legacyStellarEnableCableIsSkipped() {
        JSONObject patch = new JSONObject()
                .put("version", 1)
                .put("devices", new JSONArray()
                        .put(new JSONObject().put("type", "dev-server").put("state", new JSONObject()))
                        .put(new JSONObject().put("type", "stellar").put("state", new JSONObject())))
                .put("cables", new JSONArray()
                        .put(new JSONObject().put("fromDevice", 0).put("fromPort", "running")
                                .put("toDevice", 1).put("toPort", "enable"))
                        .put(new JSONObject().put("fromDevice", 0).put("fromPort", "ready")
                                .put("toDevice", 1).put("toPort", "run")));
        Rack rack = new Rack();
        try {
            RackIO.fromJson(rack, patch);
            assertThat(rack.getDevices()).hasSize(2);
            assertThat(rack.getDevices().get(1)).isInstanceOf(StellarDevice.class);
            assertThat(rack.getCables()).as("the ENABLE cable is dropped; the RUN cable survives").hasSize(1);
            assertThat(rack.getCables().get(0).getTo().getId()).isEqualTo("run");
        } finally {
            rack.shutdown();
        }
    }
}
