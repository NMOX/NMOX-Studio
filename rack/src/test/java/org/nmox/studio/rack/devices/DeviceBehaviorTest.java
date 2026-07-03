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
 * Beyond the argv: the reactions. What a device does when a signal
 * arrives on an input jack, the verdicts behind its OK/FAIL LEDs, and
 * the pure helper functions that parse tool output - all driven
 * directly, with the async plumbing flushed off the EDT where needed.
 */
class DeviceBehaviorTest {

    @TempDir
    Path projectDir;

    /** Captures every signal that arrives on its DATA input jack. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<Signal> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("in", "IN", SignalType.DATA);
            addInPort("gate", "GATE", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(signal);
        }
    }

    private Rack aimedRack(String manifest) throws IOException {
        Files.writeString(projectDir.resolve(manifest), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    // Drain both async paths before asserting: the EDT (knob updates and the
    // emit()s their change-listeners fire) and the rack's single-threaded
    // signal router (which delivers to a probe's receive() off-thread). A bare
    // EDT flush leaves the router race that a loaded CI runner loses.
    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // interrupted / already on EDT — not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    // ---------------- ATMOS / EnvDevice: env shaping ----------------

    @Test
    @DisplayName("ATMOS publishes NODE_ENV and CI into the shared env, and retracts CI when off")
    void atmosShapesEnv() {
        Rack rack = new Rack();
        try {
            EnvDevice atmos = new EnvDevice();
            rack.addDevice(atmos);
            // default: NODE_ENV=development, CI off (null → not present)
            assertThat(rack.getEnvOverrides()).containsEntry("NODE_ENV", "development");
            assertThat(rack.getEnvOverrides()).doesNotContainKey("CI");

            atmos.applyState(Map.of("nodeEnv", "2", "ci", "true")); // production + CI
            assertThat(rack.getEnvOverrides())
                    .containsEntry("NODE_ENV", "production")
                    .containsEntry("CI", "true");

            atmos.applyState(Map.of("ci", "false")); // CI back off
            assertThat(rack.getEnvOverrides()).doesNotContainKey("CI");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ATMOS emits an ENV summary describing the atmosphere it set")
    void atmosEmitsSummary() {
        Rack rack = new Rack();
        try {
            EnvDevice atmos = new EnvDevice();
            Probe probe = new Probe();
            rack.addDevice(atmos);
            rack.addDevice(probe);
            rack.connect(atmos.getPort("env"), probe.getPort("in"));
            probe.received.clear();

            atmos.applyState(Map.of("nodeEnv", "1", "ci", "true")); // test + CI
            settle(rack);
            assertThat(probe.received).isNotEmpty();
            String last = probe.received.stream().reduce((a, b) -> b).orElseThrow().payload();
            assertThat(last).contains("NODE_ENV=test").contains("CI=true");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- ROSETTA: toolchain override ----------------

    @Test
    @DisplayName("ROSETTA sets the rack toolchain override, and AUTO clears it")
    void rosettaSetsOverride() {
        Rack rack = new Rack();
        try {
            RosettaDevice rosetta = new RosettaDevice();
            rack.addDevice(rosetta);
            // TOOLCHAINS index 2 = "rust"
            rosetta.applyState(Map.of("toolchain", "rust"));
            assertThat(rack.getToolchainOverride()).isEqualTo("RUST");

            rosetta.applyState(Map.of("toolchain", "auto"));
            assertThat(rack.getToolchainOverride()).as("auto means no override").isNull();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ROSETTA emits the chosen toolchain name on its KIND jack")
    void rosettaEmitsKind() {
        Rack rack = new Rack();
        try {
            RosettaDevice rosetta = new RosettaDevice();
            Probe probe = new Probe();
            rack.addDevice(rosetta);
            rack.addDevice(probe);
            rack.connect(rosetta.getPort("kind"), probe.getPort("in"));
            probe.received.clear();

            rosetta.applyState(Map.of("toolchain", "go"));
            settle(rack);
            assertThat(probe.received).extracting(Signal::payload).contains("go");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ROSETTA override steers a downstream AUTO device to the pinned toolchain")
    void rosettaSteersAutoKnobs() throws IOException {
        // a Node project, but ROSETTA pins RUST: IGNITION AUTO must follow the pin
        Rack rack = aimedRack("package.json");
        try {
            RosettaDevice rosetta = new RosettaDevice();
            rack.addDevice(rosetta);
            rosetta.applyState(Map.of("toolchain", "rust"));

            RunDevice run = new RunDevice();
            rack.addDevice(run); // target=auto
            assertThat(run.buildCommand()).containsExactly("cargo", "run");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- NEPTUNE / DatabaseDevice: receive drives the action ----------------

    @Test
    @DisplayName("NEPTUNE MIGRATE trigger switches the built command to the migration path")
    void neptuneMigrateReceive() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            DatabaseDevice db = new DatabaseDevice();
            rack.addDevice(db);
            db.applyState(Map.of("dbType", "3")); // Prisma
            // default action is ping
            assertThat(db.buildCommand()).containsExactly("npx", "prisma", "validate");

            // a MIGRATE trigger flips lastAction; the built command follows.
            // (launch() no-ops here: npx is absent, but lastAction is set first.)
            db.receive(db.getPort("migrate"), Signal.trigger());
            assertThat(db.buildCommand()).containsExactly("npx", "prisma", "db", "push");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("NEPTUNE onFinished lights CONNECTED on success and emits a RUNNING gate")
    void neptuneOnFinishedGate() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            DatabaseDevice db = new DatabaseDevice();
            Probe probe = new Probe();
            rack.addDevice(db);
            rack.addDevice(probe);
            rack.connect(db.getPort("connected"), probe.getPort("gate"));
            probe.received.clear();

            db.onFinished(0);
            settle(rack);
            assertThat(probe.received).anyMatch(s -> s.type() == SignalType.GATE && s.high());

            probe.received.clear();
            db.onFinished(1);
            settle(rack);
            assertThat(probe.received).anyMatch(s -> s.type() == SignalType.GATE && !s.high());
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- IGNITION / RunDevice: enable gate + running gate ----------------

    @Test
    @DisplayName("IGNITION emits the RUNNING gate low when the process finishes")
    void ignitionRunningGateLowOnFinish() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            RunDevice run = new RunDevice();
            Probe probe = new Probe();
            rack.addDevice(run);
            rack.addDevice(probe);
            rack.connect(run.getPort("running"), probe.getPort("gate"));
            probe.received.clear();

            run.onFinished(0);
            settle(rack);
            assertThat(probe.received).anyMatch(s -> s.type() == SignalType.GATE && !s.high());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION STOP trigger is accepted without launching anything")
    void ignitionStopReceive() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            // no process running; a STOP trigger must be a harmless no-op
            run.receive(run.getPort("stop"), Signal.trigger());
            assertThat(run.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- BEACON: the runway verdict ----------------

    @Test
    @DisplayName("BEACON verdict: down fails, floor gates only a measured cert with runway left")
    void beaconVerdict() {
        assertThat(BeaconDevice.verdict(false, 90, 30)).as("unreachable is always fail").isFalse();
        assertThat(BeaconDevice.verdict(true, 90, 30)).as("plenty of runway").isTrue();
        assertThat(BeaconDevice.verdict(true, 10, 30)).as("inside the window").isFalse();
        assertThat(BeaconDevice.verdict(true, 30, 30)).as("exactly at the floor is ok").isTrue();
        assertThat(BeaconDevice.verdict(true, -1, 30)).as("no cert → floor N/A").isTrue();
        assertThat(BeaconDevice.verdict(true, 1, 0)).as("floor off → reachability decides").isTrue();
    }

    // ---------------- VITALS: the gate on the four scores ----------------

    @Test
    @DisplayName("VITALS gate holds only the metrics the GATE knob selects, against the floor")
    void vitalsGate() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            // MIN index 3 = "80"; GATE index 0 = "perf"
            VitalsDevice v = new VitalsDevice();
            rack.addDevice(v);
            v.applyState(Map.of("min", "3", "gate", "0"));
            assertThat(v.minimum()).isEqualTo(80);
            assertThat(v.gate()).isEqualTo("perf");

            // perf below floor, everything else high: perf gate FAILS
            v.scoresForTest(new VitalsDevice.Scores(0.50, 0.99, 0.99, 0.99));
            assertThat(v.overallSuccess(0)).isFalse();

            // perf above floor: passes even though a11y is low (not held)
            v.scoresForTest(new VitalsDevice.Scores(0.95, 0.10, 0.10, 0.10));
            assertThat(v.overallSuccess(0)).isTrue();

            // a non-zero exit is a fail regardless of scores
            assertThat(v.overallSuccess(1)).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS gate=all demands every score clear the floor")
    void vitalsGateAll() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            VitalsDevice v = new VitalsDevice();
            rack.addDevice(v);
            v.applyState(Map.of("min", "4", "gate", "5")); // 90, all
            v.scoresForTest(new VitalsDevice.Scores(0.99, 0.99, 0.99, 0.85)); // seo low
            assertThat(v.overallSuccess(0)).isFalse();
            v.scoresForTest(new VitalsDevice.Scores(0.99, 0.99, 0.99, 0.99));
            assertThat(v.overallSuccess(0)).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS with the floor off never gates on scores")
    void vitalsFloorOff() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            VitalsDevice v = new VitalsDevice();
            rack.addDevice(v);
            v.applyState(Map.of("min", "0")); // off
            v.scoresForTest(new VitalsDevice.Scores(0.01, 0.01, 0.01, 0.01));
            assertThat(v.overallSuccess(0)).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VITALS score parsing: real report yields the four categories, junk yields null")
    void vitalsParseScores() {
        String json = "{\"categories\":{"
                + "\"performance\":{\"score\":0.92},"
                + "\"accessibility\":{\"score\":0.88},"
                + "\"best-practices\":{\"score\":1.0},"
                + "\"seo\":{\"score\":0.75}}}";
        VitalsDevice.Scores s = VitalsDevice.parseScores(json);
        assertThat(s).isNotNull();
        assertThat(VitalsDevice.pct(s.performance())).isEqualTo(92);
        assertThat(VitalsDevice.pct(s.accessibility())).isEqualTo(88);
        assertThat(VitalsDevice.pct(s.bestPractices())).isEqualTo(100);
        assertThat(VitalsDevice.pct(s.seo())).isEqualTo(75);

        assertThat(VitalsDevice.parseScores("not json")).isNull();
        assertThat(VitalsDevice.parseScores("{\"other\":1}")).isNull();
    }

    // ---------------- GAUNTLET / BenchDevice: throughput floor ----------------

    @Test
    @DisplayName("GAUNTLET fails a clean run whose throughput never reached the floor")
    void gauntletThroughputFloor() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            BenchDevice bench = new BenchDevice();
            rack.addDevice(bench);
            // MIN R/S index 3 = "1k" (1000)
            bench.applyState(Map.of("min", "3"));
            // no measurement yet (lastReqPerSec = -1): unmeasured never gates
            assertThat(bench.overallSuccess(0)).isTrue();
            // a measured run under the floor fails
            bench.onLine("500 requests in 10.0s, 2 MB read"); // 50 r/s
            assertThat(bench.overallSuccess(0)).isFalse();
            // a fast run passes
            bench.onLine("50k requests in 10.0s, 24 MB read"); // 5000 r/s
            assertThat(bench.overallSuccess(0)).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- HTTP / HttpDevice: request assembly ----------------

    @Test
    @DisplayName("PING parses a semicolon header line into ordered name/value pairs")
    void httpParseHeaders() {
        Map<String, String> headers =
                HttpDevice.parseHeaders("Authorization: Bearer xyz; Content-Type: application/json");
        assertThat(headers)
                .containsEntry("Authorization", "Bearer xyz")
                .containsEntry("Content-Type", "application/json");
        // empty / malformed segments are skipped, not crashed on
        assertThat(HttpDevice.parseHeaders("bad; : nope; K: V")).containsExactly(Map.entry("K", "V"));
        assertThat(HttpDevice.parseHeaders("")).isEmpty();
    }

    @Test
    @DisplayName("PING builds a GET with no body and a POST that ships its JSON body")
    void httpBuildRequest() {
        var get = HttpDevice.buildRequest("GET", "http://localhost:3000/api", "", Map.of());
        assertThat(get.method()).isEqualTo("GET");
        assertThat(get.uri().toString()).isEqualTo("http://localhost:3000/api");
        // GET ships no body: the publisher is present but carries zero bytes
        assertThat(get.bodyPublisher().get().contentLength()).isZero();

        var post = HttpDevice.buildRequest("POST", "http://localhost:3000/api",
                "{\"name\":\"x\"}", Map.of());
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.bodyPublisher()).isPresent();
    }

    @Test
    @DisplayName("PING URL cable updates the URL LCD only for http(s) payloads")
    void httpUrlReceive() throws IOException {
        Rack rack = aimedRack("package.json");
        try {
            HttpDevice http = new HttpDevice();
            rack.addDevice(http);
            http.receive(http.getPort("url"), Signal.data("http://example.test:9999"));
            settle(rack);
            assertThat(http.getState()).containsEntry("url", "http://example.test:9999");

            // a non-http payload is ignored (stays whatever it was)
            http.receive(http.getPort("url"), Signal.data("ftp://nope"));
            settle(rack);
            assertThat(http.getState().get("url")).isEqualTo("http://example.test:9999");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- SOLDER / CommandLineDevice: argv split ----------------

    @Test
    @DisplayName("SOLDER splitArgs honors quotes and yields an empty list for blanks")
    void solderSplitArgs() {
        assertThat(CommandLineDevice.splitArgs("./scripts/fixtures.sh --reset"))
                .containsExactly("./scripts/fixtures.sh", "--reset");
        assertThat(CommandLineDevice.splitArgs("echo \"double quoted\""))
                .containsExactly("echo", "double quoted");
        assertThat(CommandLineDevice.splitArgs("")).isEmpty();
        assertThat(CommandLineDevice.splitArgs(null)).isEmpty();
    }

    // ---------------- REPL: argv split ----------------

    @Test
    @DisplayName("REPL splitArgs tokenizes an interpreter command with quoted paths")
    void replSplitArgs() {
        assertThat(ReplDevice.splitArgs("python3 -i")).containsExactly("python3", "-i");
        assertThat(ReplDevice.splitArgs("clisp \"my file.lisp\""))
                .containsExactly("clisp", "my file.lisp");
        assertThat(ReplDevice.splitArgs(null)).isEmpty();
    }

    // ---------------- HARBOR / DockerDevice: reclaimable math ----------------

    @Test
    @DisplayName("HARBOR totals reclaimable disk across df rows into a human figure")
    void dockerReclaimable() {
        List<org.nmox.studio.rack.docker.DockerClient.DfRow> rows = List.of(
                new org.nmox.studio.rack.docker.DockerClient.DfRow(
                        "Images", "12", "4", "6.5GB", "2.5GB (40%)"),
                new org.nmox.studio.rack.docker.DockerClient.DfRow(
                        "Build Cache", "88", "0", "2.1GB", "512MB"));
        String total = DockerDevice.totalReclaimable(rows);
        assertThat(total).isNotBlank();
        // 2.5GB + 0.5GB ≈ 3.0GB
        assertThat(total).contains("3").containsIgnoringCase("GB");
    }
}
