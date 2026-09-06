package org.nmox.studio.rack.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nmox.studio.rack.engine.KvasirClient.CodeQuestion;
import org.nmox.studio.rack.service.KvasirKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a test: the Ask KVASIR live proof, run by hand against the real
 * Anthropic API before ship ({@code -Dnmox.kvasir.live=1} with an
 * ANTHROPIC_API_KEY/CLAUDE_API_KEY in the environment). CI never sets
 * the property; the key is read through {@link KvasirKeys}' normal env
 * fallback and never logged.
 */
class AskKvasirLiveDriver {

    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live", matches = ".+")
    void liveAsk() {
        AskKvasirEngine engine = new AskKvasirEngine(new KvasirClient(),
                KvasirKeys::read, unused -> true);
        CodeQuestion q = new CodeQuestion("counter.clar", "text/x-clarity",
                "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                "Who can successfully call this function, and what happens to others?");
        AskKvasirEngine.Result r = engine.answer(q, KvasirClient.MODEL_HAIKU);
        System.out.println("LIVE STATUS: " + r.status());
        System.out.println("LIVE ANSWER:\n" + r.text());
        assertThat(r.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        assertThat(r.text()).isNotBlank();
    }

    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live", matches = ".+")
    void liveConversation() {
        AskKvasirEngine engine = new AskKvasirEngine(new KvasirClient(),
                KvasirKeys::read, unused -> true);
        KvasirConversation convo = new KvasirConversation(new CodeQuestion(
                "counter.clar", "text/x-clarity",
                "(define-constant err-owner-only (err u100))\n"
                + "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                ""));
        AskKvasirEngine.Result first = engine.converse(convo,
                "What does a non-owner get back from this call?", KvasirClient.MODEL_HAIKU);
        System.out.println("LIVE T1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);

        // the follow-up only works if the model kept the conversation:
        // "that error" refers to the previous answer, not the prompt
        AskKvasirEngine.Result second = engine.converse(convo,
                "What is the numeric code inside that error, as a bare number?",
                KvasirClient.MODEL_HAIKU);
        System.out.println("LIVE T2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        assertThat(second.text()).contains("100");
        assertThat(convo.exchanges()).isEqualTo(2);
    }

    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live", matches = ".+")
    void liveFailureConversation() {
        AskKvasirEngine engine = new AskKvasirEngine(new KvasirClient(),
                KvasirKeys::read, unused -> true);
        KvasirConversation convo = KvasirConversation.forFailure(
                new KvasirClient.FailureContext("VERITAS", "npm test", 1,
                        java.util.List.of(
                                "FAIL src/date.test.js",
                                "TypeError: Cannot read properties of undefined (reading 'toISOString')",
                                "  at formatDate (src/date.js:4:18)"),
                        "my-app", 1400));
        AskKvasirEngine.Result first = engine.converse(convo, "", KvasirClient.MODEL_HAIKU);
        System.out.println("LIVE F1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);

        AskKvasirEngine.Result second = engine.converse(convo,
                "Which file and line should I open first, per your diagnosis?",
                KvasirClient.MODEL_HAIKU);
        System.out.println("LIVE F2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        assertThat(second.text()).contains("date.js");
        assertThat(convo.exchanges()).isEqualTo(2);
    }
}
