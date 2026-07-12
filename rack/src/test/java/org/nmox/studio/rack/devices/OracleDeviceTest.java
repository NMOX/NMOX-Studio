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
}
