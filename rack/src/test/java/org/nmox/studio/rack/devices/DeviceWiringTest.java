package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.FlightRecorder;
import org.nmox.studio.rack.engine.PortScanner.PortInfo;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the device controls cleaned up in the wiring audit do
 * what their faceplates claim: a knob/toggle that is read where it
 * changes the command, and an OUT jack that actually carries a usable
 * signal. These are the cases that looked wired but weren't.
 */
class DeviceWiringTest {

    // ---- VERITAS: COVER must apply when AUTO resolves to a coverage-capable runner ----

    @Test
    @DisplayName("COVER adds --coverage when AUTO resolves to jest (the bug: it keyed on the raw knob)")
    void coverageFiresOnAutoResolvedJest(@TempDir Path dir) throws IOException {
        // a Node project whose only test framework is a jest dependency,
        // no "test" script - so AUTO resolves to jest, not npm-script
        Files.writeString(dir.resolve("package.json"),
                "{\"devDependencies\":{\"jest\":\"^29\"}}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        TestDevice tests = new TestDevice();
        tests.applyState(Map.of("coverage", "true")); // framework left on AUTO
        rack.addDevice(tests);

        assertThat(tests.buildCommand()).containsSubsequence("npx", "jest", "--coverage");
        rack.shutdown();
    }

    @Test
    @DisplayName("COVER adds --cov when AUTO resolves to pytest")
    void coverageFiresOnAutoResolvedPytest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("pyproject.toml"), "[project]\nname = \"demo\"\n");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        TestDevice tests = new TestDevice();
        tests.applyState(Map.of("coverage", "true"));
        rack.addDevice(tests);

        assertThat(tests.buildCommand()).contains("--cov");
        rack.shutdown();
    }

    @Test
    @DisplayName("COVER off leaves the command bare")
    void coverageOffAddsNothing(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"devDependencies\":{\"jest\":\"^29\"}}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        TestDevice tests = new TestDevice();
        rack.addDevice(tests);

        assertThat(tests.buildCommand()).doesNotContain("--coverage");
        rack.shutdown();
    }

    // ---- PING: POST/PUT must carry the body field; everything else stays body-less ----

    @Test
    @DisplayName("POST with a JSON body sends it with a JSON Content-Type")
    void postCarriesJsonBody() {
        HttpRequest r = HttpDevice.buildRequest("POST", "http://localhost:3000/api", "{\"a\":1}");
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.bodyPublisher()).isPresent();
        assertThat(r.bodyPublisher().get().contentLength()).isGreaterThan(0);
        assertThat(r.headers().firstValue("Content-Type")).hasValue("application/json");
    }

    @Test
    @DisplayName("POST with a non-JSON body sends it without forcing a JSON Content-Type")
    void postPlainBodyHasNoJsonHeader() {
        HttpRequest r = HttpDevice.buildRequest("PUT", "http://localhost:3000/api", "hello=world");
        assertThat(r.bodyPublisher().get().contentLength()).isGreaterThan(0);
        assertThat(r.headers().firstValue("Content-Type")).isEmpty();
    }

    @Test
    @DisplayName("GET ignores any body; POST with a blank body sends none")
    void readMethodsAndBlankBodiesAreBodyLess() {
        HttpRequest get = HttpDevice.buildRequest("GET", "http://localhost:3000", "{\"a\":1}");
        assertThat(get.bodyPublisher().get().contentLength()).isZero();

        HttpRequest blank = HttpDevice.buildRequest("POST", "http://localhost:3000", "   ");
        assertThat(blank.bodyPublisher().get().contentLength()).isZero();
    }

    // ---- SONAR: OUT must carry a machine-usable port list, not prose ----

    @Test
    @DisplayName("SONAR emits ports as a sorted, de-duplicated, comma-separated list")
    void sonarEmitsMachineUsablePorts() {
        List<PortInfo> ports = List.of(
                new PortInfo(8080, 1, "node"),
                new PortInfo(3000, 2, "node"),
                new PortInfo(3000, 3, "other"), // duplicate port
                new PortInfo(5173, 4, "vite"));
        assertThat(SonarDevice.listeningPorts(ports)).isEqualTo("3000,5173,8080");
    }

    // ---- BLACKBOX: the OUT jack was declared but never emitted; now it taps the recorder ----

    @Test
    @DisplayName("BLACKBOX OUT broadcasts the newest flight-recorder event down the cable")
    void blackboxEmitsRecorderEvents(@TempDir Path dir) throws InterruptedException {
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        BlackboxDevice blackbox = new BlackboxDevice();
        Probe probe = new Probe();
        rack.addDevice(blackbox);
        rack.addDevice(probe);
        rack.connect(blackbox.getPort("out"), probe.getPort("data"));

        // a real recorder event (a launch line) notifies listeners
        FlightRecorder.getDefault().line("FORGE", "$ npm run build", false);

        assertThat(probe.hit.await(3, TimeUnit.SECONDS)).as("blackbox delivered an event").isTrue();
        assertThat(probe.received).anyMatch(s -> s.contains("FORGE") && s.contains("LAUNCH"));
        rack.shutdown();
    }

    /** Captures whatever is patched into it; the cable is delivered async. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();
        final CountDownLatch hit = new CountDownLatch(1);

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("data", "DATA", SignalType.DATA);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + signal.type() + ":" + signal.payload());
            hit.countDown();
        }
    }
}
