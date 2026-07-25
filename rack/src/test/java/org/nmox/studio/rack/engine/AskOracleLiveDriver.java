package org.nmox.studio.rack.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.service.OracleKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a test: the Ask ORACLE live proof, run by hand against the real
 * Anthropic API before ship ({@code -Dnmox.oracle.live=1} with an
 * ANTHROPIC_API_KEY/CLAUDE_API_KEY in the environment). CI never sets
 * the property; the key is read through {@link OracleKeys}' normal env
 * fallback and never logged.
 */
class AskOracleLiveDriver {

    @Test
    @EnabledIfSystemProperty(named = "nmox.oracle.live", matches = ".+")
    void liveAsk() {
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, unused -> true);
        CodeQuestion q = new CodeQuestion("counter.clar", "text/x-clarity",
                "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                "Who can successfully call this function, and what happens to others?");
        AskOracleEngine.Result r = engine.answer(q, OracleClient.MODEL_HAIKU);
        System.out.println("LIVE STATUS: " + r.status());
        System.out.println("LIVE ANSWER:\n" + r.text());
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(r.text()).isNotBlank();
    }

    @Test
    @EnabledIfSystemProperty(named = "nmox.oracle.live", matches = ".+")
    void liveConversation() {
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, unused -> true);
        OracleConversation convo = new OracleConversation(new CodeQuestion(
                "counter.clar", "text/x-clarity",
                "(define-constant err-owner-only (err u100))\n"
                + "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                ""));
        AskOracleEngine.Result first = engine.converse(convo,
                "What does a non-owner get back from this call?", OracleClient.MODEL_HAIKU);
        System.out.println("LIVE T1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);

        // the follow-up only works if the model kept the conversation:
        // "that error" refers to the previous answer, not the prompt
        AskOracleEngine.Result second = engine.converse(convo,
                "What is the numeric code inside that error, as a bare number?",
                OracleClient.MODEL_HAIKU);
        System.out.println("LIVE T2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(second.text()).contains("100");
        assertThat(convo.exchanges()).isEqualTo(2);
    }

    @Test
    @EnabledIfSystemProperty(named = "nmox.oracle.live", matches = ".+")
    void liveFailureConversation() {
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, unused -> true);
        OracleConversation convo = OracleConversation.forFailure(
                new OracleClient.FailureContext("VERITAS", "npm test", 1,
                        java.util.List.of(
                                "FAIL src/date.test.js",
                                "TypeError: Cannot read properties of undefined (reading 'toISOString')",
                                "  at formatDate (src/date.js:4:18)"),
                        "my-app", 1400));
        AskOracleEngine.Result first = engine.converse(convo, "", OracleClient.MODEL_HAIKU);
        System.out.println("LIVE F1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);

        AskOracleEngine.Result second = engine.converse(convo,
                "Which file and line should I open first, per your diagnosis?",
                OracleClient.MODEL_HAIKU);
        System.out.println("LIVE F2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(second.text()).contains("date.js");
        assertThat(convo.exchanges()).isEqualTo(2);
    }
}
