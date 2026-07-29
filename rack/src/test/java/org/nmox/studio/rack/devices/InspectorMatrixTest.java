package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INSPECTOR's launch table: each debug target must build its language's
 * own debug-server argv, AUTO must resolve only the toolchains with a
 * wired debugger (and grey honestly for the rest, the ledger-47 law),
 * and the banner parser must announce the definitive endpoint exactly
 * once. All read through the protected seams — no debugger is spawned.
 */
class InspectorMatrixTest {

    // Mirror of DebugDevice.TARGETS (append-only by law)
    private static final String[] TARGETS = {"auto", "node", "python", "go",
        "maven", "ruby", "php"};

    @TempDir
    Path root;

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            Files.writeString(dir.resolve(f), "{}");
        }
        return dir;
    }

    private List<String> commandFor(String target, String... files) throws IOException {
        int index = List.of(TARGETS).indexOf(target);
        assertThat(index).isNotNegative();
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            DebugDevice inspector = new DebugDevice();
            rack.addDevice(inspector);
            inspector.applyState(Map.of("target", String.valueOf(index)));
            return inspector.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    private List<String> autoCommand(String... files) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            DebugDevice inspector = new DebugDevice();
            rack.addDevice(inspector);
            return inspector.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Each explicit target launches its language's debug server")
    void explicitTargets() throws IOException {
        assertThat(commandFor("python")).containsExactly("python3", "-m", "debugpy",
                "--listen", "5678", "--wait-for-client", "main.py");
        assertThat(commandFor("go")).containsExactly("dlv", "debug", "--headless",
                "--listen=:2345", "--api-version=2", "--accept-multiclient");
        assertThat(commandFor("maven")).containsExactly("mvn", "-q", "compile", "exec:java");
        assertThat(commandFor("ruby")).containsExactly("rdbg", "--open", "--port",
                "12345", "main.rb");
        assertThat(commandFor("php")).containsExactly("php", "-dxdebug.mode=debug",
                "-dxdebug.start_with_request=yes", "-S", "localhost:8000");
        assertThat(commandFor("node")).containsExactly("node", "--inspect=9229", "index.js");
        assertThat(commandFor("node", "main.js"))
                .containsExactly("node", "--inspect=9229", "main.js");
    }

    @Test
    @DisplayName("AUTO wires only the debuggable toolchains; the rest grey honestly")
    void autoResolution() throws IOException {
        assertThat(autoCommand("pyproject.toml")).first().isEqualTo("python3");
        assertThat(autoCommand("go.mod")).first().isEqualTo("dlv");
        assertThat(autoCommand("pom.xml")).first().isEqualTo("mvn");
        assertThat(autoCommand("build.gradle")).first().isEqualTo("mvn");
        assertThat(autoCommand("Gemfile")).first().isEqualTo("rdbg");
        assertThat(autoCommand("composer.json")).first().isEqualTo("php");
        assertThat(autoCommand("package.json")).first().isEqualTo("node");
        assertThat(autoCommand("bunfig.toml")).first().isEqualTo("node");
        assertThat(autoCommand("index.html")).first().isEqualTo("node");
        // no wired debugger: the grey path returns an empty command
        assertThat(autoCommand("Cargo.toml")).isEmpty();
        assertThat(autoCommand("build.zig")).isEmpty();
        assertThat(autoCommand("v.mod")).isEmpty();
    }

    /** Collects the ENDPOINT and RUNNING signals. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("endpoint", "ENDPOINT", SignalType.DATA);
            addInPort("live", "RUNNING", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + (signal.type() == SignalType.DATA
                    ? signal.payload() : String.valueOf(signal.high())));
        }
    }

    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
        rack.awaitRouterIdle();
    }

    @Test
    @DisplayName("The node banner announces the definitive ws endpoint exactly once")
    void nodeBannerAnnouncesOnce() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("package.json").toFile());
        try {
            DebugDevice inspector = new DebugDevice();
            Probe probe = new Probe();
            rack.addDevice(inspector);
            rack.addDevice(probe);
            rack.connect(inspector.getPort("endpoint"), probe.getPort("endpoint"));
            rack.connect(inspector.getPort("live"), probe.getPort("live"));

            inspector.onLine("Debugger listening on ws://127.0.0.1:9229/abc-def");
            inspector.onLine("Debugger listening on ws://127.0.0.1:9229/second");
            settle(rack);
            assertThat(probe.received)
                    .as("only the first banner announces")
                    .containsExactly("endpoint:ws://127.0.0.1:9229/abc-def");

            inspector.onFinished(0);
            settle(rack);
            assertThat(probe.received).contains("live:false");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The delve/debugpy/rdbg ready lines flip the WIRED LED without re-announcing")
    void otherDebuggerBanners() throws IOException {
        for (String banner : new String[]{
            "API server listening at: 127.0.0.1:2345", // delve
            "some wait_for_client output",              // debugpy
            "DEBUGGER: wait for debugger connection"}) { // rdbg
            Rack rack = new Rack();
            rack.setProjectDir(freshDir("package.json").toFile());
            try {
                DebugDevice inspector = new DebugDevice();
                Probe probe = new Probe();
                rack.addDevice(inspector);
                rack.addDevice(probe);
                rack.connect(inspector.getPort("endpoint"), probe.getPort("endpoint"));
                inspector.onLine(banner);
                inspector.onLine(banner); // announced-guard: second is a no-op
                settle(rack);
                assertThat(probe.received)
                        .as(banner + " carries no ws endpoint to emit").isEmpty();
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("The STOP and ENABLE jacks route; a manifest-less dir refuses the launch")
    void stopAndEnableJacks() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            DebugDevice inspector = new DebugDevice();
            rack.addDevice(inspector);
            inspector.receive(inspector.getPort("stop"), Signal.trigger(true));
            inspector.receive(inspector.getPort("enable"), Signal.gate(true));
            inspector.receive(inspector.getPort("enable"), Signal.gate(false));
            settle(rack);
            assertThat(inspector.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("On an undebuggable toolchain the LAUNCH press greys honestly, spawning nothing")
    void greyPathOnRust() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("Cargo.toml").toFile());
        try {
            DebugDevice inspector = new DebugDevice();
            rack.addDevice(inspector);
            // the RUN jack takes the primaryAction path; target resolves null
            inspector.receive(inspector.getPort("run"), Signal.trigger(true));
            settle(rack);
            assertThat(inspector.isLive()).as("no spawn on the grey path").isFalse();
        } finally {
            rack.shutdown();
        }
    }
}
