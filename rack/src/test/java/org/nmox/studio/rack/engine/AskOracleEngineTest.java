package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.PlainDocument;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ask ORACLE's consult body: the code prompt is a pure function, the
 * selection/key/consent gates keep the API un-called, and the key array
 * is wiped. The gate tests are the mutation proofs — remove either gate
 * in {@link AskOracleEngine#answer} and a spy transport catches the
 * attempt by name.
 */
class AskOracleEngineTest {

    private static final class SpyTransport implements OracleClient.Transport {

        final List<String> bodies = new ArrayList<>();
        String canned;
        IOException toThrow;

        SpyTransport(String canned) {
            this.canned = canned;
        }

        @Override
        public String post(String url, String jsonBody, char[] apiKey) throws IOException {
            bodies.add(jsonBody);
            if (toThrow != null) {
                throw toThrow;
            }
            return canned;
        }
    }

    private static String messageResponse(String text) {
        return new JSONObject()
                .put("stop_reason", "end_turn")
                .put("content", new org.json.JSONArray().put(
                        new JSONObject().put("type", "text").put("text", text)))
                .toString();
    }

    private static CodeQuestion q(String code, String question) {
        return new CodeQuestion("Foo.java", "text/x-java", code, question);
    }

    // ---- the prompt: pure, capped, honest ---------------------------------

    @Test
    @DisplayName("The code prompt names the file, language, question, and the selection")
    void promptCarriesTheDisclosedFacts() {
        String prompt = OracleClient.assembleCodePrompt(
                q("return a + b;", "why not a - b?"));
        assertThat(prompt)
                .contains("File: Foo.java")
                .contains("Language: text/x-java")
                .contains("Question: why not a - b?")
                .contains("return a + b;")
                .contains("ONLY the selection");
    }

    @Test
    @DisplayName("A blank question defaults to explain-this")
    void blankQuestionDefaults() {
        assertThat(OracleClient.assembleCodePrompt(q("x", "  ")))
                .contains("Explain what this code does.");
    }

    @Test
    @DisplayName("The selection cap truncates with an honest marker; the question caps too")
    void capsHold() {
        CodeQuestion big = q("y".repeat(CodeQuestion.MAX_CODE_CHARS + 500),
                "z".repeat(2_000));
        assertThat(big.code())
                .hasSize(CodeQuestion.MAX_CODE_CHARS + "\n[selection truncated]".length())
                .endsWith("[selection truncated]");
        assertThat(big.question()).hasSize(500);
    }

    // ---- the gates: each keeps the API un-called ---------------------------

    @Test
    @DisplayName("A blank selection refuses before the key or the network")
    void blankSelectionRefuses() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> true);
        AskOracleEngine.Result r = engine.answer(q("   ", "?"), OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.NO_SELECTION);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("KEY GATE: no key means no network — mutation-proven")
    void keyGateHolds() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> null, unused -> true);
        AskOracleEngine.Result r = engine.answer(q("code", "?"), OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.NO_KEY);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("CONSENT GATE: declining keeps everything local — mutation-proven")
    void consentGateHolds() {
        SpyTransport spy = new SpyTransport(messageResponse("never"));
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> false);
        AskOracleEngine.Result r = engine.answer(q("code", "?"), OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.NO_CONSENT);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("All gates green: the answer comes back and the key is wiped")
    void answersAndWipesKey() {
        SpyTransport spy = new SpyTransport(messageResponse("It adds a and b."));
        char[] key = "secret".toCharArray();
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> key, unused -> true);
        AskOracleEngine.Result r = engine.answer(q("return a + b;", ""),
                OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(r.text()).isEqualTo("It adds a and b.");
        assertThat(spy.bodies).hasSize(1);
        assertThat(spy.bodies.get(0)).contains("return a + b;");
        assertThat(key).containsOnly('\0');
    }

    @Test
    @DisplayName("A transport failure is an honest FAILED, never a crash")
    void transportFailureIsHonest() {
        SpyTransport spy = new SpyTransport("");
        spy.toThrow = new IOException("api unreachable");
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(spy),
                () -> "key".toCharArray(), unused -> true);
        AskOracleEngine.Result r = engine.answer(q("code", "?"), OracleClient.MODEL_HAIKU);
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.FAILED);
        assertThat(r.text()).contains("api unreachable");
    }

    // ---- the action's document readers -------------------------------------

    @Test
    @DisplayName("Document without NetBeans properties degrades honestly")
    void documentReadersDegrade() {
        PlainDocument doc = new PlainDocument();
        assertThat(org.nmox.studio.rack.service.AskOracleAction.fileName(doc))
                .isEqualTo("(unsaved buffer)");
        assertThat(org.nmox.studio.rack.service.AskOracleAction.language(doc))
                .isEqualTo("text/plain");
        doc.putProperty("mimeType", "text/x-ruby");
        assertThat(org.nmox.studio.rack.service.AskOracleAction.language(doc))
                .isEqualTo("text/x-ruby");
    }
}
