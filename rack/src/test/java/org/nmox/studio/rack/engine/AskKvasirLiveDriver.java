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

    /** A client pinned to one provider over the real HTTP transport. */
    private static KvasirClient live(KvasirProvider p) {
        return new KvasirClient(KvasirClient.httpTransport(), () -> p);
    }

    /**
     * v2.96.0: the Gemini wire, live ({@code -Dnmox.kvasir.live.google=1}
     * with GEMINI_API_KEY/GOOGLE_API_KEY in the environment): a code
     * question, then a two-turn conversation whose follow-up only resolves
     * if the user/model role mapping kept the history intact.
     */
    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live.google", matches = ".+")
    void liveGemini() {
        AskKvasirEngine engine = new AskKvasirEngine(live(KvasirProvider.GOOGLE),
                () -> KvasirKeys.read(KvasirProvider.GOOGLE), unused -> true);
        KvasirConversation convo = new KvasirConversation(new CodeQuestion(
                "counter.clar", "text/x-clarity",
                "(define-constant err-owner-only (err u100))\n"
                + "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                ""));
        AskKvasirEngine.Result first = engine.converse(convo,
                "What does a non-owner get back from this call?",
                KvasirProvider.GOOGLE.model(KvasirProvider.Depth.FAST));
        System.out.println("LIVE GEMINI T1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        AskKvasirEngine.Result second = engine.converse(convo,
                "What is the numeric code inside that error, as a bare number?",
                KvasirProvider.GOOGLE.model(KvasirProvider.Depth.FAST));
        System.out.println("LIVE GEMINI T2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        assertThat(second.text()).contains("100");
        assertThat(convo.exchanges()).isEqualTo(2);
    }

    /** The Gemini DEEP model and a pinned newer FAST id, live, one question each. */
    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live.google", matches = ".+")
    void liveGeminiModels() {
        AskKvasirEngine engine = new AskKvasirEngine(live(KvasirProvider.GOOGLE),
                () -> KvasirKeys.read(KvasirProvider.GOOGLE), unused -> true);
        CodeQuestion q = new CodeQuestion("a.js", "text/javascript",
                "const n = [1,2,3].reduce((a, b) => a + b, 0);", "What is n? Answer with the number only.");
        for (String model : new String[] {
            KvasirProvider.GOOGLE.model(KvasirProvider.Depth.DEEP),
            System.getProperty("nmox.kvasir.live.google.fast", KvasirProvider.GOOGLE.model(KvasirProvider.Depth.FAST))}) {
            AskKvasirEngine.Result r = engine.answer(q, model);
            System.out.println("LIVE GEMINI " + model + ": " + r.status() + " -> " + r.text());
            assertThat(r.status()).as(model).isEqualTo(AskKvasirEngine.Status.ANSWERED);
            assertThat(r.text()).contains("6");
        }
    }

    /** v2.96.0: the OpenAI wire, live ({@code -Dnmox.kvasir.live.openai=1} with OPENAI_API_KEY). */
    @Test
    @EnabledIfSystemProperty(named = "nmox.kvasir.live.openai", matches = ".+")
    void liveOpenAi() {
        AskKvasirEngine engine = new AskKvasirEngine(live(KvasirProvider.OPENAI),
                () -> KvasirKeys.read(KvasirProvider.OPENAI), unused -> true);
        KvasirConversation convo = new KvasirConversation(new CodeQuestion(
                "counter.clar", "text/x-clarity",
                "(define-constant err-owner-only (err u100))\n"
                + "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                ""));
        AskKvasirEngine.Result first = engine.converse(convo,
                "What does a non-owner get back from this call?",
                KvasirProvider.OPENAI.model(KvasirProvider.Depth.FAST));
        System.out.println("LIVE OPENAI T1: " + first.status() + "\n" + first.text());
        assertThat(first.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        AskKvasirEngine.Result second = engine.converse(convo,
                "What is the numeric code inside that error, as a bare number?",
                KvasirProvider.OPENAI.model(KvasirProvider.Depth.FAST));
        System.out.println("LIVE OPENAI T2: " + second.status() + "\n" + second.text());
        assertThat(second.status()).isEqualTo(AskKvasirEngine.Status.ANSWERED);
        assertThat(second.text()).contains("100");
    }

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
