package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirComplete.CompletionRequest;
import org.nmox.studio.rack.engine.KvasirCompleteEngine.Proposal;
import org.nmox.studio.rack.engine.KvasirCompleteEngine.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KvasirCompleteTest {

    private static final class SpyTransport implements KvasirClient.Transport {
        final List<String> bodies = new ArrayList<>();
        final String canned;

        SpyTransport(String canned) {
            this.canned = canned;
        }

        @Override
        public String post(String url, String jsonBody, char[] apiKey) throws IOException {
            bodies.add(jsonBody);
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

    private static CompletionRequest req(String before, String after) {
        return CompletionRequest.around("app.js", "text/javascript", before, after);
    }

    // ---- the request: clipped, never truncated silently -------------------

    @Test
    @DisplayName("The window clips before from the FRONT and after from the END, and says so")
    void windowClips() {
        String before = "x".repeat(KvasirComplete.MAX_BEFORE_CHARS + 10) + "TAIL";
        String after = "HEAD" + "y".repeat(KvasirComplete.MAX_AFTER_CHARS + 10);
        CompletionRequest r = req(before, after);
        assertThat(r.before()).hasSize(KvasirComplete.MAX_BEFORE_CHARS).endsWith("TAIL");
        assertThat(r.after()).hasSize(KvasirComplete.MAX_AFTER_CHARS).startsWith("HEAD");
        assertThat(r.clipped()).isTrue();
        assertThat(req("a", "b").clipped()).isFalse();
    }

    @Test
    @DisplayName("Nothing before the caret is refused — there is nothing to continue")
    void emptyBeforeRefuses() {
        assertThatThrownBy(() -> req("  \n", "rest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing to continue");
    }

    // ---- the prompt ------------------------------------------------------

    @Test
    @DisplayName("The prompt marks the caret, names the file and language, and notes a clipped window")
    void promptShape() {
        String p = KvasirComplete.assembleCompletionPrompt(req("const a = 1;\nfunction f(", ") {}"));
        assertThat(p).contains("app.js").contains("text/javascript")
                .contains("function f(" + KvasirComplete.CURSOR + ") {}")
                .contains("exactly one fenced code block")
                .doesNotContain("clipped");
        String clipped = KvasirComplete.assembleCompletionPrompt(
                req("z".repeat(KvasirComplete.MAX_BEFORE_CHARS + 1), ""));
        assertThat(clipped).contains("clipped");
    }

    // ---- the reply -------------------------------------------------------

    @Test
    @DisplayName("The insertion is the fenced block with an echoed line head trimmed and no trailing newline")
    void insertionExtraction() {
        assertThat(KvasirComplete.extractInsertion("```js\nreturn a + b;\n```", "  "))
                .isEqualTo("return a + b;");
        // the model echoed the head of the current line: trimmed, not doubled
        assertThat(KvasirComplete.extractInsertion("```js\n  const total = a + b;\n```", "  const total"))
                .isEqualTo(" = a + b;");
        assertThat(KvasirComplete.extractInsertion("```js\nconst total = a + b;\n```", "  const total"))
                .isEqualTo(" = a + b;");
        // prose is null; an empty block is null
        assertThat(KvasirComplete.extractInsertion("I cannot complete this.", "x")).isNull();
        assertThat(KvasirComplete.extractInsertion("```\n\n```", "x")).isNull();
    }

    @Test
    @DisplayName("Ghost helpers: first line shown, the rest counted")
    void ghostHelpers() {
        assertThat(KvasirComplete.firstLine("a\nb\nc")).isEqualTo("a");
        assertThat(KvasirComplete.moreLines("a\nb\nc")).isEqualTo(2);
        assertThat(KvasirComplete.moreLines("one")).isZero();
    }

    // ---- the engine: two gates in front of one send ------------------------

    @Test
    @DisplayName("No key: nothing is sent")
    void noKeySendsNothing() {
        SpyTransport spy = new SpyTransport(messageResponse("```\nx\n```"));
        KvasirCompleteEngine engine = new KvasirCompleteEngine(new KvasirClient(spy),
                () -> new char[0], r -> true);
        Proposal p = engine.propose(req("let a", ""), "let a", KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NO_KEY);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("Consent declined: nothing is sent")
    void noConsentSendsNothing() {
        SpyTransport spy = new SpyTransport(messageResponse("```\nx\n```"));
        KvasirCompleteEngine engine = new KvasirCompleteEngine(new KvasirClient(spy),
                () -> "k".toCharArray(), r -> false);
        Proposal p = engine.propose(req("let a", ""), "let a", KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NO_CONSENT);
        assertThat(spy.bodies).isEmpty();
    }

    @Test
    @DisplayName("A single fenced reply becomes the insertion; prose refuses out loud; empty is its own verdict")
    void engineVerdicts() {
        SpyTransport ok = new SpyTransport(messageResponse("```js\n = 1;\n```"));
        Proposal p = new KvasirCompleteEngine(new KvasirClient(ok), () -> "k".toCharArray(), r -> true)
                .propose(req("let a", ""), "let a", KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.PROPOSED);
        assertThat(p.insertion()).isEqualTo(" = 1;");
        assertThat(ok.bodies).hasSize(1);
        assertThat(ok.bodies.get(0)).contains(KvasirComplete.CURSOR);

        SpyTransport prose = new SpyTransport(messageResponse("I would not add anything here."));
        Proposal q = new KvasirCompleteEngine(new KvasirClient(prose), () -> "k".toCharArray(), r -> true)
                .propose(req("let a", ""), "let a", KvasirClient.MODEL_HAIKU);
        assertThat(q.status()).isEqualTo(Status.NOT_CODE);
        assertThat(q.message()).contains("I would not add anything here.");

        SpyTransport echo = new SpyTransport(messageResponse("```js\nlet a\n```"));
        Proposal e = new KvasirCompleteEngine(new KvasirClient(echo), () -> "k".toCharArray(), r -> true)
                .propose(req("let a", ""), "let a", KvasirClient.MODEL_HAIKU);
        assertThat(e.status()).isEqualTo(Status.EMPTY);
    }
}
