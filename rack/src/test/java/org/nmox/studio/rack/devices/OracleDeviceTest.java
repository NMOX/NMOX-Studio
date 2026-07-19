package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ORACLE's gates, mutation-proven. {@code consult} is the single path to
 * the API, so a spy transport that counts posts catches any gate that
 * stops working: remove the key gate and it sees a post with no key;
 * remove the consent gate and it sees a post without consent. Nothing here
 * opens a socket.
 */
class OracleDeviceTest {

    /** A transport that counts posts and replays a canned answer (or throws). */
    private static final class SpyTransport implements OracleClient.Transport {

        final AtomicInteger posts = new AtomicInteger();
        String canned = new JSONObject().put("content", new org.json.JSONArray().put(
                new JSONObject().put("type", "text").put("text", "It failed because X. Do Y."))).toString();
        IOException toThrow;

        @Override
        public String post(String url, String jsonBody, char[] apiKey) throws IOException {
            posts.incrementAndGet();
            if (toThrow != null) {
                throw toThrow;
            }
            return canned;
        }
    }

    private static FailureContext ctx() {
        return new FailureContext("VERITAS", "npm test", 1, List.of("FAIL"), "app", 100);
    }

    /** A device wired to a spy transport, with injectable seams. */
    private static OracleDevice wired(SpyTransport spy) {
        OracleDevice device = new OracleDevice();
        device.client = new OracleClient(spy);
        device.failureSource = () -> Optional.of(ctx());
        device.keySource = () -> "sk-test".toCharArray();
        return device;
    }

    @Test
    @DisplayName("KEY GATE: no key ⇒ no post, honest NO-API-KEY status")
    void keyGateBlocksTheCall() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.keySource = () -> new char[0]; // no key

        String status = device.consult(true); // consent granted, so only the key gate can stop it

        assertThat(spy.posts.get()).as("the key gate must keep the API un-called").isZero();
        assertThat(status).contains("NO API KEY");
    }

    @Test
    @DisplayName("CONSENT GATE: no consent ⇒ no post, honest needs-your-OK status")
    void consentGateBlocksTheCall() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy); // key present

        String status = device.consult(false); // consent NOT granted

        assertThat(spy.posts.get()).as("the consent gate must keep the API un-called").isZero();
        assertThat(status).contains("NEEDS YOUR OK");
    }

    @Test
    @DisplayName("nothing to explain ⇒ no post, honest status")
    void nothingToExplain() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.failureSource = Optional::empty;

        String status = device.consult(true);

        assertThat(spy.posts.get()).isZero();
        assertThat(status).contains("NOTHING TO EXPLAIN");
    }

    @Test
    @DisplayName("key + consent + a failure ⇒ one post, the verdict shows the answer")
    void happyPathConsults() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);

        String status = device.consult(true);

        assertThat(spy.posts.get()).isEqualTo(1);
        assertThat(status).contains("It failed because X");
    }

    @Test
    @DisplayName("a transport failure is an honest OFFLINE status, never a throw")
    void offlineDegradesHonestly() {
        SpyTransport spy = new SpyTransport();
        spy.toThrow = new IOException("no route to host");
        OracleDevice device = wired(spy);

        String status = device.consult(true);

        assertThat(spy.posts.get()).isEqualTo(1); // it tried
        assertThat(status).contains("OFFLINE");
    }

    @Test
    @DisplayName("SONNET knob position selects the stronger model")
    void modelKnobSelectsSonnet() {
        SpyTransport spy = new SpyTransport();
        // capture the model out of the request body
        String[] seenModel = new String[1];
        OracleClient.Transport capturing = (url, body, key) -> {
            seenModel[0] = new JSONObject(body).getString("model");
            return spy.canned;
        };
        OracleDevice device = new OracleDevice();
        device.client = new OracleClient(capturing);
        device.failureSource = () -> Optional.of(ctx());
        device.keySource = () -> "sk-test".toCharArray();

        // dial MODEL to SONNET (index 1) through the persisted param
        device.applyState(java.util.Map.of("model", "1"));
        device.consult(true);

        assertThat(seenModel[0]).isEqualTo(OracleClient.MODEL_SONNET);
    }

    // ---- v1.91.0: auto-explain by cable ----

    @Test
    @DisplayName("CABLE: a FAIL trigger consults hands-free when key + consent are already in place")
    void cableTriggerConsults() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.consentCheck = () -> true;
        device.autoRetryDelayMs = 0;
        String text = device.autoConsultNow();
        assertThat(spy.posts.get()).isEqualTo(1);
        assertThat(text).contains("It failed because X");
    }

    @Test
    @DisplayName("CABLE: no prior consent ⇒ no post and NO prompt — the LCD says press the button once")
    void cableNeverPrompts() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.consentCheck = () -> false;
        device.autoRetryDelayMs = 0;
        assertThat(device.autoConsultNow()).isNull();
        assertThat(spy.posts.get()).as("a cable must never reach the API without prior consent").isZero();
    }

    @Test
    @DisplayName("CABLE: consults are rate-limited — a flapping suite cannot hammer the API")
    void cableCooldown() {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.consentCheck = () -> true;
        device.autoRetryDelayMs = 0;
        long[] now = {0L};
        device.clock = () -> now[0];
        device.autoConsultNow();
        now[0] = OracleDevice.AUTO_COOLDOWN_MS - 1;
        device.autoConsultNow(); // inside the window: refused
        assertThat(spy.posts.get()).isEqualTo(1);
        now[0] = OracleDevice.AUTO_COOLDOWN_MS + 1;
        device.autoConsultNow(); // window passed: consults again
        assertThat(spy.posts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("OUT jack: a successful consult emits the full explanation as DATA")
    void outJackCarriesExplanation() throws Exception {
        SpyTransport spy = new SpyTransport();
        OracleDevice device = wired(spy);
        device.consentCheck = () -> true;
        device.autoRetryDelayMs = 0;
        org.nmox.studio.rack.model.Rack rack = new org.nmox.studio.rack.model.Rack();
        var received = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var probe = new org.nmox.studio.rack.model.RackDevice("probe", "PROBE", "PROBE", new java.awt.Color(0, 0, 0), 1) {
            {
                addInPort("data", "DATA", org.nmox.studio.rack.model.SignalType.DATA);
            }

            @Override
            public void receive(org.nmox.studio.rack.model.Port in,
                    org.nmox.studio.rack.model.Signal signal) {
                received.add(signal.payload());
            }
        };
        rack.addDevice(device);
        rack.addDevice(probe);
        rack.connect(device.getPort("out"), probe.getPort("data"));
        device.autoConsultNow();
        rack.awaitRouterIdle();
        assertThat(received).hasSize(1);
        assertThat(received.peek()).contains("It failed because X. Do Y.");
        rack.shutdown();
    }
}
