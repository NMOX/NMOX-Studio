package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.engine.OracleClient.Turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The conversation layer: the pure OracleConversation state (first turn
 * carries the full disclosed prompt, follow-ups stay plain, the exchange
 * cap is honest), the Messages envelope for multi-turn, and the engine's
 * converse path keeping the same gates as a fresh ask.
 */
class OracleConversationTest {

    private static CodeQuestion subject() {
        return new CodeQuestion("Foo.java", "text/x-java", "return a + b;", "");
    }

    private static final class SpyTransport implements OracleClient.Transport {

        final List<String> bodies = new ArrayList<>();
        String canned;

        SpyTransport(String canned) {
            this.canned = canned;
        }

        @Override
        public String post(String url, String jsonBody, char[] apiKey) {
            bodies.add(jsonBody);
            return canned;
        }
    }

    private static String messageResponse(String text) {
        return new JSONObject()
                .put("stop_reason", "end_turn")
                .put("content", new JSONArray().put(
                        new JSONObject().put("type", "text").put("text", text)))
                .toString();
    }

    @Test
    @DisplayName("First outgoing turn is the full disclosed prompt; follow-ups are plain text")
    void firstTurnDiscloses() {
        OracleConversation convo = new OracleConversation(subject());
        List<Turn> first = convo.outgoing("what does this do?");
        assertThat(first).hasSize(1);
        assertThat(first.get(0).text())
                .contains("File: Foo.java").contains("return a + b;");

        convo.record("what does this do?", "It adds.");
        List<Turn> second = convo.outgoing("and subtraction?");
        assertThat(second).hasSize(3);
        assertThat(second.get(1)).isEqualTo(new Turn("assistant", "It adds."));
        assertThat(second.get(2).text()).isEqualTo("and subtraction?"); // no re-disclosure
    }

    @Test
    @DisplayName("The exchange cap goes honest, not silent — canAsk flips at the limit")
    void capIsHonest() {
        OracleConversation convo = new OracleConversation(subject());
        for (int i = 0; i < OracleConversation.MAX_EXCHANGES; i++) {
            assertThat(convo.canAsk()).isTrue();
            convo.record("q" + i, "a" + i);
        }
        assertThat(convo.canAsk()).isFalse();
        assertThat(convo.exchanges()).isEqualTo(OracleConversation.MAX_EXCHANGES);
    }

    @Test
    @DisplayName("Follow-ups cap at MAX_FOLLOW_UP_CHARS")
    void followUpCap() {
        OracleConversation convo = new OracleConversation(subject());
        convo.record("q", "a");
        List<Turn> out = convo.outgoing("x".repeat(10_000));
        assertThat(out.get(out.size() - 1).text())
                .hasSize(OracleConversation.MAX_FOLLOW_UP_CHARS);
    }

    @Test
    @DisplayName("The multi-turn envelope carries every turn in order with its role")
    void envelopeShape() {
        String body = OracleClient.requestBodyConversation(OracleClient.MODEL_HAIKU,
                List.of(new Turn("user", "u1"), new Turn("assistant", "a1"),
                        new Turn("user", "u2")));
        JSONArray messages = new JSONObject(body).getJSONArray("messages");
        assertThat(messages.length()).isEqualTo(3);
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("user");
        assertThat(messages.getJSONObject(1).getString("role")).isEqualTo("assistant");
        assertThat(messages.getJSONObject(2).getString("content")).isEqualTo("u2");
    }

    @Test
    @DisplayName("A Turn only speaks the API's two roles")
    void turnRolesEnforced() {
        assertThatThrownBy(() -> new Turn("system", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- the engine's converse path keeps the gates ------------------------

    @Test
    @DisplayName("CONSENT GATE on converse: declining keeps every send local — mutation-proven")
    void converseConsentGate() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> false);
        AskOracleEngine.Result r = engine.converse(
                new OracleConversation(subject()), "?", OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.NO_CONSENT);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("KEY GATE on converse: no key, no network — mutation-proven")
    void converseKeyGate() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> null, unused -> true);
        AskOracleEngine.Result r = engine.converse(
                new OracleConversation(subject()), "?", OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.NO_KEY);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("A capped conversation refuses before the network")
    void converseCapRefuses() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> true);
        OracleConversation convo = new OracleConversation(subject());
        for (int i = 0; i < OracleConversation.MAX_EXCHANGES; i++) {
            convo.record("q" + i, "a" + i);
        }
        AskOracleEngine.Result r = engine.converse(convo, "one more?",
                OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.FAILED);
        assertThat(r.text()).contains("cap");
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("Two green exchanges: history grows, second body carries the first answer")
    void twoExchanges() throws IOException {
        SpyTransport spy = new SpyTransport(messageResponse("It adds."));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> true);
        OracleConversation convo = new OracleConversation(subject());

        AskOracleEngine.Result first = engine.converse(convo, "", OracleClient.MODEL_HAIKU);
        assertThat(first.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(convo.exchanges()).isEqualTo(1);

        spy.canned = messageResponse("Then use minus.");
        AskOracleEngine.Result second = engine.converse(convo, "and subtraction?",
                OracleClient.MODEL_HAIKU);
        assertThat(second.text()).isEqualTo("Then use minus.");
        assertThat(convo.exchanges()).isEqualTo(2);
        // the second request replays the first answer as an assistant turn
        assertThat(spy.bodies.get(1)).contains("It adds.").contains("and subtraction?");
    }
}
