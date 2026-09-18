package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
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
 * Every serve device announces URL before READY, through the one home.
 *
 * <p>The presets wire {@code server.url → SCOPE.url} and {@code server.ready
 * → SCOPE.open}; with READY first, SCOPE opened its LCD's stale default on
 * every first serve (the Angular template on 4200 opened 5173 — the
 * 2026-09-17 rack walk). Nine of twelve servers had the pair the wrong way
 * round, each spelled by hand. The source law keeps {@code emit("ready"} out
 * of every device file but {@code CommandDevice}, so the order lives in
 * exactly one method; the behavioural law drives every server's own banner
 * through a real router and reads the arrival order.
 */
class ServingAnnounceOrderGateTest {

    @TempDir
    Path root;

    @Test
    @DisplayName("Source law: no device emits READY on its own — CommandDevice.announceServing is the one home")
    void onlyTheOneHomeEmitsReady() throws IOException {
        Path devices = Path.of("src/main/java/org/nmox/studio/rack/devices");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(devices)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("CommandDevice.java")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (EMIT_READY.matcher(source).find()) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }
        assertThat(offenders)
                .as("devices emitting READY by hand (route through announceServing: URL first)")
                .isEmpty();
    }

    /** {@code emit("ready"} however it is spaced — a literal match let {@code emit( "ready"} walk past. */
    private static final java.util.regex.Pattern EMIT_READY = java.util.regex.Pattern.compile("emit\\(\\s*\"ready\"");

    /**
     * The behavioural law's population is hand-kept, and a hand-kept
     * population cannot prove itself complete (the v2.144.0 ledger shape): a
     * device that declares a READY out-jack but is not in {@link #servers()}
     * could emit READY through a constant, in the wrong order, and neither
     * law would see it — the 2026-09-17 arc review ran exactly that mutant
     * and it lived. So the population is DERIVED here: every device source
     * declaring {@code addOutPort("ready"} must be driven by the behavioural
     * law, by class.
     */
    @Test
    @DisplayName("Population law: every device declaring a READY out-jack is driven by the behavioural law")
    void everyReadyJackDeviceIsDriven() throws IOException {
        java.util.Set<String> declared = new java.util.TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/org/nmox/studio/rack/devices"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (READY_JACK.matcher(source).find()) {
                    declared.add(file.getFileName().toString().replace(".java", ""));
                }
            }
        }
        java.util.Set<String> driven = new java.util.TreeSet<>();
        for (Server server : servers()) {
            CommandDevice device = server.maker().get();
            try {
                driven.add(device.getClass().getSimpleName());
            } finally {
                device.dispose();
            }
        }
        assertThat(declared).as("devices with a READY jack, read from the source").isNotEmpty();
        assertThat(driven).as("every READY-jack device has a banner in servers(); every servers() entry has a READY jack")
                .containsExactlyInAnyOrderElementsOf(declared);
    }

    private static final java.util.regex.Pattern READY_JACK =
            java.util.regex.Pattern.compile("addOutPort\\(\\s*\"ready\"");

    /** Records every arriving signal in order, as id:payload-or-high. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("url", "URL", SignalType.DATA);
            addInPort("ready", "READY", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + (signal.type() == SignalType.DATA
                    ? signal.payload() : String.valueOf(signal.high())));
        }
    }

    /** One serve device with the banner its real tool prints. */
    private record Server(String name, Supplier<CommandDevice> maker, Consumer<CommandDevice> announce) {
    }

    private static List<Server> servers() {
        return List.of(
                new Server("SURGE", DevServerDevice::new, d -> d.onLine("VITE v5  ready in 300 ms")),
                new Server("VELOCITY", ViteDevice::new, d -> d.onLine("  ➜  Local:   http://localhost:5173/")),
                new Server("HALO", AngularDevice::new, d -> d.onLine("  ➜  Local:   http://localhost:4200/")),
                new Server("ARTISAN", ArtisanDevice::new, d -> d.onLine("   INFO  Server running on [http://127.0.0.1:8000].")),
                new Server("SPECTER", SpecterDevice::new, d -> d.onLine("Serving HTML report at http://127.0.0.1:9323")),
                new Server("COSMOS", AstroDevice::new, d -> d.onLine("  ┃ Local    http://localhost:4321/")),
                new Server("NEXUS", NextDevice::new, d -> d.onLine("  ▲ Next.js  - Local: http://localhost:3000")),
                new Server("NIMBUS", NuxtDevice::new, d -> d.onLine("  ➜ Local:    http://localhost:3000/")),
                new Server("KINETIC", SvelteKitDevice::new, d -> d.onLine("  ➜  Local:   http://localhost:5173/")),
                new Server("PHOENIX", PhoenixDevice::new, d -> d.onLine("[info] Access MyAppWeb.Endpoint at http://localhost:4000")),
                new Server("IGNITION", RunDevice::new, d -> d.onLine("Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...")),
                new Server("ANVIL", AnvilDevice::new, d -> d.onLine("Listening on 127.0.0.1:8545")),
                new Server("ANCHOR", AnchorDevice::new, d -> d.onLine("JSON RPC URL: http://127.0.0.1:8899")),
                new Server("STELLAR", StellarDevice::new, d -> {
                    ((StellarDevice) d).setLaunchedVerbForTest("net-start");
                    d.onFinished(0);
                }));
    }

    @Test
    @DisplayName("Behavioural law: every server's banner reaches the cables as URL, then READY, READY once")
    void urlArrivesBeforeReadyOnEveryServer() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Server server : servers()) {
            Path dir = Files.createDirectories(root.resolve(server.name().toLowerCase(java.util.Locale.ROOT)));
            Files.writeString(dir.resolve("package.json"), "{}");
            Rack rack = new Rack();
            rack.setProjectDir(dir.toFile());
            try {
                CommandDevice device = server.maker().get();
                Probe probe = new Probe();
                rack.addDevice(device);
                rack.addDevice(probe);
                rack.connect(device.getPort("url"), probe.getPort("url"));
                rack.connect(device.getPort("ready"), probe.getPort("ready"));
                server.announce().accept(device);
                server.announce().accept(device); // the same banner twice: URL once, READY once
                rack.awaitRouterIdle();
                List<String> got = new ArrayList<>(probe.received);
                int url = indexOfPrefix(got, "url:");
                int ready = got.indexOf("ready:true");
                if (url < 0 || ready < 0 || url > ready || got.stream().filter("ready:true"::equals).count() != 1) {
                    wrong.add(server.name() + " " + got);
                }
            } finally {
                rack.shutdown();
            }
        }
        assertThat(wrong).as("servers whose cables did not read URL then READY (once)").isEmpty();
    }

    private static int indexOfPrefix(List<String> list, String prefix) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
