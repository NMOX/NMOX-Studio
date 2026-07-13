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

    // Drain both async paths before asserting: the EDT and the rack's
    // single-threaded signal router (which delivers to receivers off-thread).
    // A bare EDT flush leaves the router race that a loaded CI runner loses.
    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
        rack.awaitRouterIdle();
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
            settle(rack);

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
            settle(rack);
            assertThat(probe.gate).anyMatch(s -> !s.high());
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- VELOCITY / ViteDevice ----------------

    @Test
    @DisplayName("VELOCITY build command is the production bundle")
    void viteBuild() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            ViteDevice vite = new ViteDevice();
            rack.addDevice(vite);
            assertThat(vite.buildCommand()).containsExactly("npx", "vite", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VELOCITY onLine broadcasts the local URL once and fires READY once")
    void viteOnLineUrl() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            ViteDevice vite = new ViteDevice();
            Probe probe = new Probe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("url"), probe.getPort("data"));
            rack.connect(vite.getPort("ready"), probe.getPort("trig"));

            vite.onLine("  ➜  Local:   http://localhost:5173/");
            vite.onLine("  ➜  press h to show help http://localhost:5173/"); // same URL: no re-emit
            settle(rack);

            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://localhost:5173/");
            assertThat(probe.trig).as("READY fires exactly once").hasSize(1);
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- COSMOS / AstroDevice ----------------

    @Test
    @DisplayName("COSMOS build command is the static site build")
    void astroBuild() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            AstroDevice astro = new AstroDevice();
            rack.addDevice(astro);
            assertThat(astro.buildCommand()).containsExactly("npx", "astro", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("COSMOS onLine broadcasts the local URL once and fires READY once")
    void astroOnLineUrl() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            AstroDevice astro = new AstroDevice();
            Probe probe = new Probe();
            rack.addDevice(astro);
            rack.addDevice(probe);
            rack.connect(astro.getPort("url"), probe.getPort("data"));
            rack.connect(astro.getPort("ready"), probe.getPort("trig"));

            astro.onLine("  \u2503 Local    http://localhost:4321/");
            astro.onLine("  \u2503 Network  http://localhost:4321/ (same)"); // same URL: no re-emit
            settle(rack);

            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://localhost:4321/");
            assertThat(probe.trig).as("READY fires exactly once").hasSize(1);
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
            settle(rack);
            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://localhost:4000");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- ARTISAN / ArtisanDevice ----------------

    @Test
    @DisplayName("ARTISAN serve detects artisan's bracketed URL, fires READY once, no re-emit")
    void artisanServeAndUrl() throws IOException {
        Rack rack = rackWith("composer.json");
        try {
            Files.writeString(projectDir.resolve("artisan"), "#!/usr/bin/env php\n");
            ArtisanDevice artisan = new ArtisanDevice();
            Probe probe = new Probe();
            rack.addDevice(artisan);
            rack.addDevice(probe);
            rack.connect(artisan.getPort("url"), probe.getPort("data"));
            rack.connect(artisan.getPort("ready"), probe.getPort("trig"));

            // default ACTION knob position is serve
            assertThat(artisan.buildCommand()).containsExactly("php", "artisan", "serve");

            // artisan serve announces: Server running on [http://127.0.0.1:8000].
            artisan.onLine("   INFO  Server running on [http://127.0.0.1:8000].");
            artisan.onLine("Server running on [http://127.0.0.1:8000]."); // same URL: no re-emit
            settle(rack);

            // the bracket and trailing period must not leak into the URL
            assertThat(probe.data).extracting(Signal::payload)
                    .containsExactly("http://127.0.0.1:8000");
            assertThat(probe.trig).as("READY fires exactly once").hasSize(1);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ARTISAN onFinished drops the SERVING gate")
    void artisanServingGateDrops() throws IOException {
        Rack rack = rackWith("composer.json");
        try {
            ArtisanDevice artisan = new ArtisanDevice();
            Probe probe = new Probe();
            rack.addDevice(artisan);
            rack.addDevice(probe);
            rack.connect(artisan.getPort("serving"), probe.getPort("gate"));

            artisan.onFinished(0);
            settle(rack);
            assertThat(probe.gate).anyMatch(s -> !s.high());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ARTISAN version currency reads the locked laravel/framework from composer.lock")
    void artisanComposerLockVersion() throws IOException {
        Files.writeString(projectDir.resolve("composer.lock"),
                "{\"packages\":[{\"name\":\"symfony/console\",\"version\":\"v7.1.0\"},"
                + "{\"name\":\"laravel/framework\",\"version\":\"v11.9.2\"}],"
                + "\"packages-dev\":[{\"name\":\"laravel/pint\",\"version\":\"v1.16.0\"}]}");
        assertThat(ProjectInspector.composerLockVersion(projectDir.toFile(), "laravel/framework"))
                .as("composer's v prefix is stripped").isEqualTo("11.9.2");
        assertThat(ProjectInspector.composerLockVersion(projectDir.toFile(), "laravel/pint"))
                .as("packages-dev is searched too").isEqualTo("1.16.0");
        assertThat(ProjectInspector.composerLockVersion(projectDir.toFile(), "not/here"))
                .isNull();
        Path empty = Files.createDirectory(projectDir.resolve("empty"));
        assertThat(ProjectInspector.composerLockVersion(empty.toFile(), "laravel/framework"))
                .as("no lock file → unknown").isNull();
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
            settle(rack);
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
            settle(rack);
            assertThat(probe.trig).as("READY fires on the first line").isNotEmpty();
        } finally {
            rack.shutdown();
        }
    }
}
