package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VITALS and BEACON auto-aim: with the URL LCD blank or still factory,
 * RUN reads the serving registry for the project's live WEB server —
 * and an explicitly dialed URL always wins. Read at RUN time; nothing
 * polls.
 */
class AutoUrlGateTest {

    @TempDir
    Path projectDir;

    private Rack aimedRack() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
    }

    private Serving webServing(String url) {
        return new Serving("fixture@auto-url", "SURGE", url, Kind.WEB, projectDir.toFile());
    }

    @Test
    @DisplayName("VITALS with the factory URL auto-aims at the live WEB serving; LCD shows the pick")
    void vitalsAutoAimsFromDefault() throws IOException {
        Rack rack = aimedRack();
        ServingRegistry registry = ServingRegistry.getDefault();
        try {
            registry.register(webServing("http://localhost:4242"));
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);

            assertThat(vitals.buildCommand()).contains("http://localhost:4242");
            flushEdt();
            assertThat(vitals.getState().get("url")).isEqualTo("auto: http://localhost:4242");

            // the auto text itself stays auto: the NEXT run re-reads the registry
            registry.register(webServing("http://localhost:5001"));
            assertThat(vitals.buildCommand()).contains("http://localhost:5001");
        } finally {
            registry.deregister("fixture@auto-url");
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS explicit URL always wins over a live serving")
    void vitalsExplicitWins() throws IOException {
        Rack rack = aimedRack();
        ServingRegistry registry = ServingRegistry.getDefault();
        try {
            registry.register(webServing("http://localhost:4242"));
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);
            vitals.applyState(java.util.Map.of("url", "http://localhost:9000/dashboard"));

            assertThat(vitals.buildCommand()).contains("http://localhost:9000/dashboard");
            flushEdt();
            assertThat(vitals.getState().get("url"))
                    .as("an explicit dial is never rewritten")
                    .isEqualTo("http://localhost:9000/dashboard");
        } finally {
            registry.deregister("fixture@auto-url");
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS with nothing serving keeps its old behavior: default stays, blank stays blank")
    void vitalsFallsBackWithoutServing() throws IOException {
        Rack rack = aimedRack();
        try {
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);
            assertThat(vitals.buildCommand()).contains("http://localhost:5173");

            vitals.applyState(java.util.Map.of("url", ""));
            assertThat(vitals.buildCommand()).as("blank URL still refuses to run").isNull();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS ignores CHAIN servings — a devnet is not a web page to audit")
    void vitalsIgnoresChain() throws IOException {
        Rack rack = aimedRack();
        ServingRegistry registry = ServingRegistry.getDefault();
        try {
            registry.register(new Serving("fixture@auto-url", "ANVIL",
                    "http://127.0.0.1:8545", Kind.CHAIN, projectDir.toFile()));
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);
            assertThat(vitals.buildCommand()).contains("http://localhost:5173");
        } finally {
            registry.deregister("fixture@auto-url");
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS ignores servings from OTHER projects")
    void vitalsIgnoresOtherProjects() throws IOException {
        Rack rack = aimedRack();
        ServingRegistry registry = ServingRegistry.getDefault();
        try {
            registry.register(new Serving("fixture@auto-url", "SURGE",
                    "http://localhost:4242", Kind.WEB, new java.io.File("/somewhere/else")));
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);
            assertThat(vitals.buildCommand()).contains("http://localhost:5173");
        } finally {
            registry.deregister("fixture@auto-url");
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("BEACON auto-aims the same way: default auto-picks, explicit wins, empty registry falls back")
    void beaconAutoAim() throws IOException {
        Rack rack = aimedRack();
        ServingRegistry registry = ServingRegistry.getDefault();
        try {
            BeaconDevice beacon = new BeaconDevice();
            rack.addDevice(beacon);
            assertThat(beacon.effectiveUrl()).as("nothing serving: factory default")
                    .isEqualTo("https://example.com");

            registry.register(webServing("http://localhost:4242"));
            assertThat(beacon.effectiveUrl()).isEqualTo("http://localhost:4242");

            beacon.applyState(java.util.Map.of("url", "https://prod.example.io"));
            assertThat(beacon.effectiveUrl()).as("explicit dial wins")
                    .isEqualTo("https://prod.example.io");
        } finally {
            registry.deregister("fixture@auto-url");
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a stale 'auto:' LCD falls back to its last pick when the server is gone")
    void staleAutoFallsBackToLastPick() throws IOException {
        Rack rack = aimedRack();
        try {
            VitalsDevice vitals = new VitalsDevice();
            rack.addDevice(vitals);
            vitals.applyState(java.util.Map.of("url", "auto: http://localhost:4242"));
            assertThat(vitals.buildCommand()).contains("http://localhost:4242");
        } finally {
            rack.shutdown();
        }
    }
}
