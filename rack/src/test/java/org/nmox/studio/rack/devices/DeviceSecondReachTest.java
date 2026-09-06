package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirClient.FailureContext;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The second reach: jack routing, banner parsing, and verb tables the
 * per-device suites skip. Console SERVE/STOP/ENABLE jacks are driven
 * against manifest-less projects so every launch refuses safely and no
 * process ever spawns; the URL-announce and exit paths are driven
 * through onLine/onFinished directly, the exact pump entry points.
 */
class DeviceSecondReachTest {

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

    /** Collects every arriving signal as id:payload-or-high. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("url", "URL", SignalType.DATA);
            addInPort("ready", "READY", SignalType.TRIGGER);
            addInPort("serving", "SERVING", SignalType.GATE);
            addInPort("out", "OUT", SignalType.DATA);
            addInPort("connected", "RUNNING", SignalType.GATE);
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

    /**
     * Every web console shares the serve/stop/enable jack shape and the
     * ServeUrls banner announce. Drive each one's receive() (launches
     * refuse: no manifest), then its onLine/onFinished directly.
     */
    @Test
    @DisplayName("Web console jacks route and their banners announce URL/READY once")
    void webConsoleJacksAndBanners() throws Exception {
        List<Supplier<CommandDevice>> consoles = List.of(
                SvelteKitDevice::new, AstroDevice::new, NuxtDevice::new,
                AngularDevice::new, ArtisanDevice::new, PhoenixDevice::new);
        for (Supplier<CommandDevice> maker : consoles) {
            Rack rack = new Rack();
            rack.setProjectDir(freshDir().toFile());
            try {
                CommandDevice console = maker.get();
                Probe probe = new Probe();
                rack.addDevice(console);
                rack.addDevice(probe);
                String name = console.getTitle();
                rack.connect(console.getPort("url"), probe.getPort("url"));
                rack.connect(console.getPort("ready"), probe.getPort("ready"));
                rack.connect(console.getPort("serving"), probe.getPort("serving"));

                // jacks route; the manifest-less dir refuses every launch
                console.receive(console.getPort("serve"), Signal.trigger(true));
                console.receive(console.getPort("enable"), Signal.gate(true));
                console.receive(console.getPort("enable"), Signal.gate(false));
                console.receive(console.getPort("stop"), Signal.trigger(true));
                settle(rack);
                assertThat(console.isLive()).as(name + " spawned nothing").isFalse();
                assertThat(probe.received)
                        .as(name + ": a refused launch raises no serving gate")
                        .noneMatch(s -> s.equals("serving:true"));

                // the dev-server banner announces URL + READY, deduped
                console.onLine("  ➜  Local:   http://localhost:5173/");
                console.onLine("  ➜  Local:   http://localhost:5173/");
                settle(rack);
                assertThat(probe.received)
                        .as(name + " announces the URL once")
                        .filteredOn(s -> s.startsWith("url:"))
                        .containsExactly("url:http://localhost:5173/");
                assertThat(probe.received).contains("ready:true");

                // exit: the serving gate drops and the registry entry clears
                console.onFinished(0);
                settle(rack);
                assertThat(probe.received).contains("serving:false");
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("The web3 consoles' STOP jacks and manifest stances are safe without tools")
    void web3ConsoleStopJacks() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            AnchorDevice anchor = new AnchorDevice();
            StellarDevice stellar = new StellarDevice();
            AnvilDevice anvil = new AnvilDevice();
            rack.addDevice(anchor);
            rack.addDevice(stellar);
            rack.addDevice(anvil);
            // these consoles run outside project manifests by design
            assertThat(anchor.requiresProjectManifest()).isFalse();
            assertThat(anvil.requiresProjectManifest()).isFalse();
            // STOP with nothing running is a safe no-op on each
            anchor.receive(anchor.getPort("stop"), Signal.trigger(true));
            stellar.receive(stellar.getPort("stop"), Signal.trigger(true));
            anvil.receive(anvil.getPort("stop"), Signal.trigger(true));
            // enable-low routes to the stop side of the gate
            anchor.receive(anchor.getPort("enable"), Signal.gate(false));
            stellar.receive(stellar.getPort("enable"), Signal.gate(false));
            anvil.receive(anvil.getPort("enable"), Signal.gate(false));
            settle(rack);
            assertThat(anchor.isLive()).isFalse();
            assertThat(stellar.isLive()).isFalse();
            assertThat(anvil.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("TAIL follows a real file: new lines ride OUT, rotation resets cleanly")
    void tailFollowsAFile() throws Exception {
        Rack rack = new Rack();
        Path dir = freshDir();
        rack.setProjectDir(dir.toFile());
        try {
            TailDevice tail = new TailDevice();
            Probe probe = new Probe();
            rack.addDevice(tail);
            rack.addDevice(probe);
            rack.connect(tail.getPort("out"), probe.getPort("out"));

            Path log = dir.resolve("app.log");
            Files.writeString(log, "old line\n");
            tail.applyState(Map.of("path", log.toAbsolutePath().toString(),
                    "follow", "true"));
            settle(rack);
            assertThat(tail.isPolling()).isTrue();

            // give the first tick a chance to take position = end-of-file,
            // then append: only the new lines may ride the jack
            Thread.sleep(1_300);
            Files.writeString(log, "fresh one\nfresh two\n", StandardOpenOption.APPEND);
            awaitContains(rack, probe, "out:fresh two");
            assertThat(probe.received)
                    .contains("out:fresh one")
                    .doesNotContain("out:old line");

            // rotation: a shorter file resets to its head
            Files.writeString(log, "rotated\n");
            awaitContains(rack, probe, "out:rotated");

            tail.applyState(Map.of("follow", "false"));
            settle(rack);
            assertThat(tail.isPolling()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    private static void awaitContains(Rack rack, Probe probe, String wanted)
            throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            settle(rack);
            if (probe.received.contains(wanted)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("never saw " + wanted + "; got " + probe.received);
    }

    @Test
    @DisplayName("DB-9000 builds each engine's ping and migrate argv")
    void databaseVerbTable() throws Exception {
        // DB_TYPES = {Postgres, MySQL, SQLite, Prisma, Ecto, Django}
        record Row(int knob, String conn, String[] ping, String[] migrate) {
        }
        List<Row> rows = List.of(
                new Row(0, "appdb", new String[]{"psql", "-d", "appdb", "-c", "SELECT 1;"},
                        new String[]{"psql", "-d", "appdb", "-f", "migrate.sql"}),
                new Row(1, "appdb", new String[]{"mysql", "-D", "appdb", "-e", "SELECT 1;"},
                        new String[]{"mysql", "-D", "appdb", "-e", "source migrate.sql"}),
                new Row(2, "", new String[]{"sqlite3", ":memory:", "SELECT 1;"},
                        new String[]{"sqlite3", "dev.db", ".read migrate.sql"}),
                new Row(3, "", new String[]{"npx", "prisma", "validate"},
                        new String[]{"npx", "prisma", "db", "push"}),
                new Row(4, "", new String[]{"mix", "ecto.status"},
                        new String[]{"mix", "ecto.migrate"}),
                new Row(5, "", new String[]{"python", "manage.py", "check"},
                        new String[]{"python", "manage.py", "migrate"}));
        for (Row row : rows) {
            Rack rack = new Rack();
            rack.setProjectDir(freshDir().toFile()); // manifest-less: launches refuse
            try {
                DatabaseDevice db = new DatabaseDevice();
                rack.addDevice(db);
                db.applyState(Map.of("dbType", String.valueOf(row.knob()),
                        "conn", row.conn()));
                // the jack sets the verb, the refused launch spawns nothing,
                // and buildCommand tells us what WOULD have run
                db.receive(db.getPort("ping"), Signal.trigger(true));
                settle(rack);
                assertThat(db.buildCommand()).containsExactly(row.ping());
                db.receive(db.getPort("migrate"), Signal.trigger(true));
                settle(rack);
                assertThat(db.buildCommand()).containsExactly(row.migrate());
                assertThat(db.isLive()).isFalse();
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("DB-9000's exit verdict drives the RUNNING gate both ways")
    void databaseExitGate() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            DatabaseDevice db = new DatabaseDevice();
            Probe probe = new Probe();
            rack.addDevice(db);
            rack.addDevice(probe);
            rack.connect(db.getPort("connected"), probe.getPort("connected"));
            db.onFinished(0);
            db.onFinished(1);
            settle(rack);
            assertThat(probe.received).contains("connected:true", "connected:false");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("TYPEGUARD parses tsc, phpstan, and solhint output per lane")
    void typecheckOutputParsers() throws Exception {
        // TS lane: a locatable error line plus the Found-N summary
        Rack rack = new Rack();
        Path node = freshDir("package.json");
        Files.writeString(node.resolve("src.ts"), "let x: number = 'no';");
        rack.setProjectDir(node.toFile());
        try {
            TypecheckDevice guard = new TypecheckDevice();
            rack.addDevice(guard);
            guard.onLine("src.ts(1,5): error TS2322: Type 'string' is not assignable.");
            guard.onLine("Found 1 error.");
            guard.onLine("Found 0 errors.");
            settle(rack);
        } finally {
            rack.shutdown();
        }

        // PHP lane: phpstan raw file:line:message rows for real files
        Rack rack2 = new Rack();
        Path php = freshDir("composer.json");
        Files.writeString(php.resolve("index.php"), "<?php");
        rack2.setProjectDir(php.toFile());
        try {
            TypecheckDevice guard = new TypecheckDevice();
            rack2.addDevice(guard);
            guard.onLine("index.php:3:Call to undefined function bar()");
            guard.onLine("not a diagnostic line");
            settle(rack2);
        } finally {
            rack2.shutdown();
        }

        // FOUNDRY lane: the solhint summary, and the honest no-config grey
        Rack rack3 = new Rack();
        rack3.setProjectDir(freshDir("foundry.toml").toFile());
        try {
            TypecheckDevice guard = new TypecheckDevice();
            rack3.addDevice(guard);
            assertThat(guard.buildCommand())
                    .as("no .solhint.json: no command to run").isNull();
            guard.onLine("✖ 5 problems (4 errors, 1 warning)");
            // the RUN jack takes primaryAction's honest-absent path
            guard.receive(guard.getPort("run"), Signal.trigger(true));
            settle(rack3);
            assertThat(guard.isLive()).isFalse();
        } finally {
            rack3.shutdown();
        }
    }

    @Test
    @DisplayName("TYPEGUARD's strict and watch switches shape the tsc argv")
    void typecheckSwitches() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("package.json").toFile());
        try {
            TypecheckDevice guard = new TypecheckDevice();
            rack.addDevice(guard);
            guard.applyState(Map.of("strict", "true", "watch", "true"));
            assertThat(guard.buildCommand()).containsExactly(
                    "npx", "tsc", "--noEmit", "--pretty", "false", "--strict", "--watch");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("KVASIR's EXPLAIN cable consults hands-free through every gate")
    void kvasirCablePath() throws Exception {
        class SpyTransport implements KvasirClient.Transport {

            final AtomicInteger posts = new AtomicInteger();

            @Override
            public String post(String url, String jsonBody, char[] apiKey) {
                posts.incrementAndGet();
                return new org.json.JSONObject().put("content",
                        new org.json.JSONArray().put(new org.json.JSONObject()
                                .put("type", "text").put("text", "Because X.")))
                        .toString();
            }
        }
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            SpyTransport spy = new SpyTransport();
            KvasirDevice kvasir = new KvasirDevice();
            kvasir.client = new KvasirClient(spy);
            kvasir.failureSource = () -> Optional.of(new FailureContext(
                    "VERITAS", "npm test", 1, List.of("FAIL"), "app", 100));
            kvasir.keySource = () -> "sk-test".toCharArray();
            kvasir.consentCheck = () -> true; // granted earlier by a human press
            rack.addDevice(kvasir);

            kvasir.receive(kvasir.getPort("explain"), Signal.trigger(true));
            long deadline = System.currentTimeMillis() + 15_000;
            while (spy.posts.get() == 0 && System.currentTimeMillis() < deadline) {
                RackDevice.awaitDeviceBgIdle();
                settle(rack);
                Thread.sleep(25);
            }
            assertThat(spy.posts.get())
                    .as("the cable path consults once through the spy").isEqualTo(1);

            // inside the cooldown window a second trigger must not re-consult
            kvasir.receive(kvasir.getPort("explain"), Signal.trigger(true));
            RackDevice.awaitDeviceBgIdle();
            settle(rack);
            assertThat(spy.posts.get()).isEqualTo(1);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("REPL jacks and panic are safe with no session running")
    void replIdleSafety() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            ReplDevice repl = new ReplDevice();
            rack.addDevice(repl);
            // a DATA signal with no live session is swallowed, not crashed
            repl.receive(repl.getPort("eval"), Signal.data("1 + 1"));
            repl.panic(); // nothing running: both halves are no-ops
            settle(rack);
            assertThat(repl.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }
}
