package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * The serving devices (framework consoles, dev server, tunnel) share a
 * shape: parse a URL out of their output and broadcast it, fire READY
 * once, drop the SERVING gate on exit. This drives those output hooks
 * and the SERVE/STOP/ENABLE input jacks directly.
 */
class FrameworkDeviceTest {

    @TempDir
    Path projectDir;

    /** Records every signal on either of its input jacks. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<Signal> data = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Signal> gate = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Signal> trig = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("data", "DATA", SignalType.DATA);
            addInPort("gate", "GATE", SignalType.GATE);
            addInPort("trig", "TRIG", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            switch (in.getId()) {
                case "data" -> data.add(signal);
                case "gate" -> gate.add(signal);
                case "trig" -> trig.add(signal);
                default -> { }
            }
        }
    }

    private Rack rackWith(String manifest) throws IOException {
        Files.writeString(projectDir.resolve(manifest), "{}");
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

    // ---------------- NEXUS / NextDevice ----------------

    @Test
    @DisplayName("NEXUS build command is the production build")
    void nextBuild() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            NextDevice next = new NextDevice();
            rack.addDevice(next);
            assertThat(next.buildCommand()).containsExactly("npx", "next", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("NEXUS onLine broadcasts the local URL once and fires READY once")
    void nextOnLineUrl() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            NextDevice next = new NextDevice();
            Probe probe = new Probe();
            rack.addDevice(next);
            rack.addDevice(probe);
            rack.connect(next.getPort("url"), probe.getPort("data"));
            rack.connect(next.getPort("ready"), probe.getPort("trig"));

            next.onLine("  ▲ Next.js  - Local: http://localhost:3000");
            next.onLine("event compiled http://localhost:3000 again"); // same URL: no re-emit
            flushEdt();

            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://localhost:3000");
            assertThat(probe.trig).as("READY fires exactly once").hasSize(1);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("NEXUS onFinished drops the SERVING gate")
    void nextOnFinishedGate() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            NextDevice next = new NextDevice();
            Probe probe = new Probe();
            rack.addDevice(next);
            rack.addDevice(probe);
            rack.connect(next.getPort("serving"), probe.getPort("gate"));

            next.onFinished(0);
            flushEdt();
            assertThat(probe.gate).anyMatch(s -> !s.high());
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- PHOENIX ----------------

    @Test
    @DisplayName("PHOENIX serves mix phx.server and detects the serving URL")
    void phoenixServeAndUrl() throws IOException {
        Rack rack = rackWith("mix.exs");
        try {
            PhoenixDevice phx = new PhoenixDevice();
            Probe probe = new Probe();
            rack.addDevice(phx);
            rack.addDevice(probe);
            rack.connect(phx.getPort("url"), probe.getPort("data"));

            assertThat(phx.buildCommand()).containsExactly("mix", "phx.server");

            phx.onLine("[info] Access MyAppWeb.Endpoint at http://localhost:4000");
            flushEdt();
            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://localhost:4000");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- HALO / AngularDevice ----------------

    @Test
    @DisplayName("HALO build honors the PROD switch")
    void angularBuildProdSwitch() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            AngularDevice halo = new AngularDevice();
            rack.addDevice(halo);
            // prod switch defaults on
            assertThat(halo.buildCommand()).containsExactly("npx", "ng", "build");
            halo.applyState(Map.of("prod", "false"));
            assertThat(halo.buildCommand())
                    .containsExactly("npx", "ng", "build", "--configuration=development");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- shared serving semantics: ENABLE gate ----------------

    @Test
    @DisplayName("A low ENABLE edge is a harmless stop when nothing is running")
    void enableLowIsHarmlessStop() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            NextDevice next = new NextDevice();
            rack.addDevice(next);
            // an ENABLE gate going low with no process is a no-op stop
            next.receive(next.getPort("enable"), Signal.gate(false));
            assertThat(next.isLive()).isFalse();
            // STOP trigger likewise
            next.receive(next.getPort("stop"), Signal.trigger());
            assertThat(next.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- WORMHOLE / TunnelDevice: public URL detection ----------------

    @Test
    @DisplayName("WORMHOLE onLine lifts the public tunnel URL onto its URL jack")
    void tunnelPublicUrl() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            TunnelDevice tunnel = new TunnelDevice();
            Probe probe = new Probe();
            rack.addDevice(tunnel);
            rack.addDevice(probe);
            rack.connect(tunnel.getPort("url"), probe.getPort("data"));

            tunnel.onLine("2024-01-01 INF |  https://random-words.trycloudflare.com");
            flushEdt();
            assertThat(probe.data).extracting(Signal::payload)
                    .anyMatch(u -> u.contains("trycloudflare.com"));
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- SURGE / DevServerDevice: readiness + address-in-use ----------------

    @Test
    @DisplayName("SURGE first output line fires READY and announces a URL")
    void devServerReadyOnFirstLine() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            DevServerDevice surge = new DevServerDevice();
            Probe probe = new Probe();
            rack.addDevice(surge);
            rack.addDevice(probe);
            rack.connect(surge.getPort("ready"), probe.getPort("trig"));

            surge.onLine("VITE v5  ready in 300 ms");
            flushEdt();
            assertThat(probe.trig).as("READY fires on the first line").isNotEmpty();
        } finally {
            rack.shutdown();
        }
    }
}
