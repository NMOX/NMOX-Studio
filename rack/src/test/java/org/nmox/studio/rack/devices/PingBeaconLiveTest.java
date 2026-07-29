package org.nmox.studio.rack.devices;

import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PING and BEACON against a real in-JVM loopback server: the fire path
 * end to end — request built, response read, LEDs and status set, the
 * OK/FAIL/BODY jacks carrying the verdict — plus the no-route, bad-URL,
 * and TLS-probe failure paths. No network leaves the machine.
 */
class PingBeaconLiveTest {

    private static HttpServer server;
    private static String baseUrl;
    private static int closedPort;

    @BeforeAll
    static void startFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // a real executor: with the default (null) every handler runs on the
        // single dispatcher thread, so one slow client wedges the whole fixture
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ping-fixture");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/json", ex -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, "HEAD".equals(ex.getRequestMethod()) ? -1 : body.length);
            if (!"HEAD".equals(ex.getRequestMethod())) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
            ex.close();
        });
        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
    }

    @AfterAll
    static void stopFixture() {
        server.stop(0);
    }

    /** Collects every arriving signal as "port:payload". */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("ok", "OK", SignalType.TRIGGER);
            addInPort("fail", "FAIL", SignalType.TRIGGER);
            addInPort("body", "BODY", SignalType.DATA);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + signal.payload());
        }
    }

    /** Waits for a signal matching the prefix, draining both async paths. */
    private static String awaitSignal(Rack rack, Probe probe, String prefix)
            throws Exception {
        // past the device's own 15s request timeout, so a timing-out send
        // still gets to report its verdict before we call it missing
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            rack.awaitRouterIdle();
            for (String s : probe.received) {
                if (s.startsWith(prefix)) {
                    return s;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("no " + prefix + " signal; got " + probe.received);
    }

    private record Rig(Rack rack, HttpDevice ping, Probe probe) {

        static Rig build() {
            Rack rack = new Rack();
            HttpDevice ping = new HttpDevice();
            Probe probe = new Probe();
            rack.addDevice(ping);
            rack.addDevice(probe);
            rack.connect(ping.getPort("ok"), probe.getPort("ok"));
            rack.connect(ping.getPort("fail"), probe.getPort("fail"));
            rack.connect(ping.getPort("body"), probe.getPort("body"));
            return new Rig(rack, ping, probe);
        }
    }

    @Test
    @DisplayName("A 2xx GET fires OK and carries the body down the BODY jack")
    void getFiresOkWithBody() throws Exception {
        Rig rig = Rig.build();
        try {
            rig.ping().applyState(Map.of("url", baseUrl + "/json"));
            rig.ping().receive(rig.ping().getPort("send"), Signal.trigger(true));
            assertThat(awaitSignal(rig.rack(), rig.probe(), "body:"))
                    .contains("{\"ok\":true}");
            assertThat(awaitSignal(rig.rack(), rig.probe(), "ok:")).isNotNull();
        } finally {
            rig.rack().shutdown();
        }
    }

    @Test
    @DisplayName("A 404 fires FAIL; a closed port reports NO ROUTE; a bad URL refuses")
    void failurePaths() throws Exception {
        Rig rig = Rig.build();
        try {
            rig.ping().applyState(Map.of("url", baseUrl + "/missing"));
            rig.ping().receive(rig.ping().getPort("send"), Signal.trigger(true));
            assertThat(awaitSignal(rig.rack(), rig.probe(), "fail:")).isNotNull();
        } finally {
            rig.rack().shutdown();
        }

        Rig noRoute = Rig.build();
        try {
            noRoute.ping().applyState(Map.of("url", "http://127.0.0.1:" + closedPort + "/"));
            noRoute.ping().receive(noRoute.ping().getPort("send"), Signal.trigger(true));
            assertThat(awaitSignal(noRoute.rack(), noRoute.probe(), "fail:")).isNotNull();
        } finally {
            noRoute.rack().shutdown();
        }

        // a URL the URI parser rejects: BAD URL, synchronously, nothing emitted
        Rig bad = Rig.build();
        try {
            bad.ping().applyState(Map.of("url", "http://[not-a-host"));
            bad.ping().receive(bad.ping().getPort("send"), Signal.trigger(true));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            bad.rack().awaitRouterIdle();
            assertThat(bad.probe().received).isEmpty();
            // an empty URL is an even earlier no-op
            bad.ping().applyState(Map.of("url", "  "));
            bad.ping().receive(bad.ping().getPort("send"), Signal.trigger(true));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            bad.rack().awaitRouterIdle();
            assertThat(bad.probe().received).isEmpty();
        } finally {
            bad.rack().shutdown();
        }
    }

    @Test
    @DisplayName("POST carries the JSON body and the URL jack re-aims the probe")
    void postAndUrlJack() throws Exception {
        Rig rig = Rig.build();
        try {
            // the URL jack accepts only http(s) payloads
            rig.ping().receive(rig.ping().getPort("url"), Signal.data("not-a-url"));
            rig.ping().receive(rig.ping().getPort("url"), Signal.data(baseUrl + "/json"));
            // METHODS = {GET, HEAD, POST, PUT, DELETE}
            rig.ping().applyState(Map.of("method", "2", "body", "{\"in\":1}"));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            rig.ping().receive(rig.ping().getPort("send"), Signal.trigger(true));
            assertThat(awaitSignal(rig.rack(), rig.probe(), "ok:")).isNotNull();
        } finally {
            rig.rack().shutdown();
        }
    }

    // ---------------- BEACON ----------------

    private static final class BeaconProbe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        BeaconProbe() {
            super("probe2", "PROBE2", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("ok", "OK", SignalType.TRIGGER);
            addInPort("fail", "FAIL", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + signal.payload());
        }
    }

    private static String awaitBeacon(Rack rack, BeaconProbe probe) throws Exception {
        // generous: the TLS-probe path spends up to 8s on the handshake
        // attempt plus 10s on the HEAD timeout before it reports
        long deadline = System.currentTimeMillis() + 40_000;
        while (System.currentTimeMillis() < deadline) {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            rack.awaitRouterIdle();
            String first = probe.received.peek();
            if (first != null) {
                return first;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("beacon never reported");
    }

    @Test
    @DisplayName("BEACON reports a live http endpoint UP and a closed port DOWN")
    void beaconUpAndDown() throws Exception {
        Rack rack = new Rack();
        try {
            BeaconDevice beacon = new BeaconDevice();
            BeaconProbe probe = new BeaconProbe();
            rack.addDevice(beacon);
            rack.addDevice(probe);
            rack.connect(beacon.getPort("ok"), probe.getPort("ok"));
            rack.connect(beacon.getPort("fail"), probe.getPort("fail"));

            beacon.applyState(Map.of("url", baseUrl + "/json"));
            beacon.receive(beacon.getPort("run"), Signal.trigger(true));
            assertThat(awaitBeacon(rack, probe)).startsWith("ok:");

            probe.received.clear();
            beacon.applyState(Map.of("url", "http://127.0.0.1:" + closedPort + "/"));
            beacon.receive(beacon.getPort("run"), Signal.trigger(true));
            assertThat(awaitBeacon(rack, probe)).startsWith("fail:");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("An https probe against a dead port reports DOWN with no cert runway")
    void beaconTlsProbeFailsHonestly() throws Exception {
        // NOTE: https against an OPEN plain-http port is untestable here —
        // startHandshake() reads with no socket timeout, so both sides wait
        // forever. A closed port covers the same https branch and the
        // certDaysRemaining failure path, and fails fast.
        Rack rack = new Rack();
        try {
            BeaconDevice beacon = new BeaconDevice();
            BeaconProbe probe = new BeaconProbe();
            rack.addDevice(beacon);
            rack.addDevice(probe);
            rack.connect(beacon.getPort("fail"), probe.getPort("fail"));
            // MINIMUMS = {off, 7, 14, 30}: dial a floor so the verdict math runs
            beacon.applyState(Map.of("min", "1",
                    "url", "https://127.0.0.1:" + closedPort + "/json"));
            assertThat(beacon.minimumDays()).isEqualTo(7);
            beacon.receive(beacon.getPort("run"), Signal.trigger(true));
            // the TLS probe cannot connect (-1 days), the HEAD over https
            // fails too: DOWN, so FAIL fires
            assertThat(awaitBeacon(rack, probe)).startsWith("fail:");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The MIN DAYS knob reads off and each dialed floor")
    void minimumDaysKnob() {
        Rack rack = new Rack();
        try {
            BeaconDevice beacon = new BeaconDevice();
            rack.addDevice(beacon);
            assertThat(beacon.minimumDays()).as("factory: off").isZero();
            beacon.applyState(Map.of("min", "3"));
            assertThat(beacon.minimumDays()).isEqualTo(30);
        } finally {
            rack.shutdown();
        }
    }
}
