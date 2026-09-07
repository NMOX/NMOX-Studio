package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirClient.Turn;
import org.nmox.studio.rack.engine.KvasirProvider.Depth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KVASIR speaks three wires (v2.96.0): the OpenAI and Gemini envelopes
 * and parses, the per-provider auth headers, the cross-provider model
 * resolution (a Claude depth never rides a Gemini request), and the
 * client routing every send through the provider read at send time. No
 * sockets anywhere.
 */
class KvasirProviderTest {

    @BeforeEach
    @AfterEach
    void reset() {
        KvasirProvider.resetForTest();
    }

    private static List<Turn> convo() {
        return List.of(new Turn("user", "hello"), new Turn("assistant", "hi"),
                new Turn("user", "explain"));
    }

    // ---- OpenAI ----------------------------------------------------------

    @Test
    @DisplayName("OpenAI: chat-completions envelope — model, messages, max_completion_tokens (never max_tokens)")
    void openAiBody() {
        JSONObject body = new JSONObject(KvasirProvider.OPENAI.requestBody("gpt-5", convo()));
        assertThat(body.getString("model")).isEqualTo("gpt-5");
        assertThat(body.getInt("max_completion_tokens")).isEqualTo(KvasirProvider.OUTPUT_TOKENS);
        assertThat(body.has("max_tokens")).as("reasoning models refuse max_tokens").isFalse();
        JSONArray messages = body.getJSONArray("messages");
        assertThat(messages.length()).isEqualTo(3);
        assertThat(messages.getJSONObject(1).getString("role")).isEqualTo("assistant");
        assertThat(messages.getJSONObject(2).getString("content")).isEqualTo("explain");
    }

    private static String openAiResponse(String content, String finish, String refusal) {
        JSONObject message = new JSONObject().put("role", "assistant");
        if (content != null) {
            message.put("content", content);
        }
        if (refusal != null) {
            message.put("refusal", refusal);
        }
        return new JSONObject().put("choices", new JSONArray().put(
                new JSONObject().put("message", message).put("finish_reason", finish))).toString();
    }

    @Test
    @DisplayName("OpenAI: choices[0].message.content is the answer; parts form joins")
    void openAiParseText() throws IOException {
        assertThat(KvasirProvider.OPENAI.parse(openAiResponse("  Fix X.  ", "stop", null)))
                .isEqualTo("Fix X.");
        String parts = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("message", new JSONObject().put("content", new JSONArray()
                        .put(new JSONObject().put("type", "text").put("text", "A "))
                        .put(new JSONObject().put("type", "text").put("text", "B"))))
                .put("finish_reason", "stop"))).toString();
        assertThat(KvasirProvider.OPENAI.parse(parts)).isEqualTo("A B");
    }

    @Test
    @DisplayName("OpenAI: a refusal field or a content_filter finish is an honest decline; errors and empties surface")
    void openAiParseFailures() {
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse(openAiResponse(null, "stop", "I can't help with that")))
                .isInstanceOf(IOException.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse(openAiResponse("partial", "content_filter", null)))
                .isInstanceOf(IOException.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse(new JSONObject().put("error",
                new JSONObject().put("message", "Incorrect API key provided")).toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("Incorrect API key provided");
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse(new JSONObject().put("choices", new JSONArray()).toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("no explanation");
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse(openAiResponse("", "stop", null)))
                .isInstanceOf(IOException.class).hasMessageContaining("no explanation");
        assertThatThrownBy(() -> KvasirProvider.OPENAI.parse("<html>502</html>"))
                .isInstanceOf(IOException.class).hasMessageContaining("JSON");
    }

    // ---- Gemini ----------------------------------------------------------

    @Test
    @DisplayName("Gemini: contents with user/model roles and text parts; the model rides the URL, not the body")
    void geminiBody() {
        JSONObject body = new JSONObject(KvasirProvider.GOOGLE.requestBody("gemini-2.5-flash", convo()));
        assertThat(body.has("model")).isFalse();
        JSONArray contents = body.getJSONArray("contents");
        assertThat(contents.length()).isEqualTo(3);
        assertThat(contents.getJSONObject(0).getString("role")).isEqualTo("user");
        assertThat(contents.getJSONObject(1).getString("role"))
                .as("Gemini's name for the assistant side").isEqualTo("model");
        assertThat(contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
                .isEqualTo("explain");
        assertThat(body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
                .isEqualTo(KvasirProvider.OUTPUT_TOKENS);
        assertThat(KvasirProvider.GOOGLE.endpoint("gemini-2.5-flash"))
                .isEqualTo(KvasirProvider.GOOGLE_ENDPOINT_PREFIX + "gemini-2.5-flash:generateContent");
    }

    private static String geminiResponse(JSONArray parts, String finish) {
        JSONObject candidate = new JSONObject().put("finishReason", finish);
        if (parts != null) {
            candidate.put("content", new JSONObject().put("role", "model").put("parts", parts));
        }
        return new JSONObject().put("candidates", new JSONArray().put(candidate)).toString();
    }

    @Test
    @DisplayName("Gemini: the text parts join; thought parts are skipped")
    void geminiParseText() throws IOException {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", "private reasoning").put("thought", true))
                .put(new JSONObject().put("text", "Fix "))
                .put(new JSONObject().put("text", "Y."));
        assertThat(KvasirProvider.GOOGLE.parse(geminiResponse(parts, "STOP"))).isEqualTo("Fix Y.");
    }

    @Test
    @DisplayName("Gemini: a SAFETY finish with no text or a blocked prompt is a decline; errors and empties surface")
    void geminiParseFailures() {
        assertThatThrownBy(() -> KvasirProvider.GOOGLE.parse(geminiResponse(null, "SAFETY")))
                .isInstanceOf(IOException.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> KvasirProvider.GOOGLE.parse(new JSONObject()
                .put("promptFeedback", new JSONObject().put("blockReason", "PROHIBITED_CONTENT")).toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> KvasirProvider.GOOGLE.parse(new JSONObject().put("error",
                new JSONObject().put("message", "API key not valid").put("status", "INVALID_ARGUMENT")).toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("API key not valid");
        assertThatThrownBy(() -> KvasirProvider.GOOGLE.parse(geminiResponse(new JSONArray(), "MAX_TOKENS")))
                .isInstanceOf(IOException.class).hasMessageContaining("no explanation");
        assertThatThrownBy(() -> KvasirProvider.GOOGLE.parse(new JSONObject().put("candidates", new JSONArray()).toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("no explanation");
    }

    // ---- auth headers ----------------------------------------------------

    @Test
    @DisplayName("Each provider carries the key in exactly one header of its own name")
    void authHeaders() {
        Map<String, String> a = KvasirProvider.ANTHROPIC.authHeaders("k-1");
        assertThat(a).containsEntry("x-api-key", "k-1").containsKey("anthropic-version");
        Map<String, String> o = KvasirProvider.OPENAI.authHeaders("k-2");
        assertThat(o).containsExactly(Map.entry("Authorization", "Bearer k-2"));
        Map<String, String> g = KvasirProvider.GOOGLE.authHeaders("k-3");
        assertThat(g).containsExactly(Map.entry("x-goog-api-key", "k-3"));
        for (KvasirProvider p : KvasirProvider.values()) {
            long carrying = p.authHeaders("SECRET").values().stream()
                    .filter(v -> v.contains("SECRET")).count();
            assertThat(carrying).as(p + " carries the key exactly once").isEqualTo(1);
            assertThat(p.endpoint(p.model(Depth.FAST))).doesNotContain("SECRET").startsWith("https://");
        }
    }

    // ---- model resolution ------------------------------------------------

    @Test
    @DisplayName("A remembered Claude depth becomes the SAME depth on another provider — never a foreign id")
    void resolveCrossesProvidersByDepth() {
        assertThat(KvasirProvider.GOOGLE.resolve(KvasirClient.MODEL_HAIKU))
                .isEqualTo(KvasirProvider.GOOGLE.model(Depth.FAST));
        assertThat(KvasirProvider.GOOGLE.resolve(KvasirClient.MODEL_SONNET))
                .isEqualTo(KvasirProvider.GOOGLE.model(Depth.DEEP));
        assertThat(KvasirProvider.OPENAI.resolve(KvasirProvider.GOOGLE.model(Depth.DEEP)))
                .isEqualTo(KvasirProvider.OPENAI.model(Depth.DEEP));
        assertThat(KvasirProvider.ANTHROPIC.resolve("gpt-5-mini")).isEqualTo(KvasirClient.MODEL_HAIKU);
        assertThat(KvasirProvider.OPENAI.resolve("gpt-5")).as("our own id passes").isEqualTo("gpt-5");
        assertThat(KvasirProvider.OPENAI.resolve("")).isEqualTo(KvasirProvider.OPENAI.model(Depth.FAST));
        assertThat(KvasirProvider.GOOGLE.resolve("gemini-9-custom")).as("an explicit custom id passes")
                .isEqualTo("gemini-9-custom");
        assertThat(KvasirProvider.GOOGLE.resolve("bad id/with spaces"))
                .as("a malformed id can never build a URL").isEqualTo(KvasirProvider.GOOGLE.model(Depth.FAST));
    }

    @Test
    @DisplayName("A pinned model id wins for its depth when URL-safe, else the shipped default")
    void pinnedModelPreference() {
        KvasirProvider.pinModelForTest(KvasirProvider.GOOGLE, Depth.FAST, "gemini-3.8-flash");
        assertThat(KvasirProvider.GOOGLE.model(Depth.FAST)).isEqualTo("gemini-3.8-flash");
        assertThat(KvasirProvider.GOOGLE.depthOf("gemini-3.8-flash")).contains(Depth.FAST);
        assertThat(KvasirProvider.GOOGLE.depthOf("gemini-2.5-flash")).as("the default still reads as FAST")
                .contains(Depth.FAST);
        KvasirProvider.pinModelForTest(KvasirProvider.GOOGLE, Depth.DEEP, "not a model id");
        assertThat(KvasirProvider.GOOGLE.model(Depth.DEEP))
                .isEqualTo(KvasirProvider.GOOGLE.defaultModel(Depth.DEEP));
    }

    @Test
    @DisplayName("The configured provider is Claude until chosen; a choice round-trips; junk reads as Claude")
    void configuredRoundTrip() {
        assertThat(KvasirProvider.configured()).isEqualTo(KvasirProvider.ANTHROPIC);
        KvasirProvider.remember(KvasirProvider.OPENAI);
        assertThat(KvasirProvider.configured()).isEqualTo(KvasirProvider.OPENAI);
        assertThat(KvasirProvider.fromId(" Google ")).contains(KvasirProvider.GOOGLE);
        assertThat(KvasirProvider.fromId("bard")).isEmpty();
        KvasirProvider.resetForTest();
        assertThat(KvasirProvider.configured()).isEqualTo(KvasirProvider.ANTHROPIC);
    }

    // ---- the client routes by provider ------------------------------------

    /** Records every post; answers in whatever shape the URL's vendor expects. */
    private static final class VendorSpy implements KvasirClient.Transport {

        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<KvasirProvider> providers = new ArrayList<>();

        @Override
        public String post(String url, String jsonBody, char[] apiKey) {
            throw new AssertionError("the client must call the provider-aware post");
        }

        @Override
        public String post(KvasirProvider p, String url, String jsonBody, char[] apiKey) {
            providers.add(p);
            urls.add(url);
            bodies.add(jsonBody);
            return switch (p) {
                case ANTHROPIC -> new JSONObject().put("content", new JSONArray().put(
                        new JSONObject().put("type", "text").put("text", "claude says"))).toString();
                case OPENAI -> openAiResponse("chatgpt says", "stop", null);
                case GOOGLE -> geminiResponse(new JSONArray().put(
                        new JSONObject().put("text", "gemini says")), "STOP");
            };
        }
    }

    @Test
    @DisplayName("The client reads the provider at send time: Gemini URL + envelope, no Claude id anywhere; a switch needs no new client")
    void clientFollowsProviderAtSendTime() throws IOException {
        VendorSpy spy = new VendorSpy();
        KvasirProvider[] current = {KvasirProvider.GOOGLE};
        KvasirClient client = new KvasirClient(spy, () -> current[0]);

        String gemini = client.converse(convo(), KvasirClient.MODEL_SONNET, "k".toCharArray());
        assertThat(gemini).isEqualTo("gemini says");
        assertThat(spy.providers).containsExactly(KvasirProvider.GOOGLE);
        assertThat(spy.urls.get(0)).isEqualTo(KvasirProvider.GOOGLE.endpoint(
                KvasirProvider.GOOGLE.model(Depth.DEEP)));
        assertThat(spy.urls.get(0) + spy.bodies.get(0)).doesNotContain("claude");
        assertThat(new JSONObject(spy.bodies.get(0)).getJSONArray("contents").length()).isEqualTo(3);

        current[0] = KvasirProvider.OPENAI;
        String chatgpt = client.ask(new KvasirClient.CodeQuestion("a.js", "text/javascript", "x", "?"),
                KvasirClient.MODEL_HAIKU, "k".toCharArray());
        assertThat(chatgpt).isEqualTo("chatgpt says");
        assertThat(spy.urls.get(1)).isEqualTo(KvasirProvider.OPENAI_ENDPOINT);
        assertThat(new JSONObject(spy.bodies.get(1)).getString("model"))
                .isEqualTo(KvasirProvider.OPENAI.model(Depth.FAST));

        current[0] = KvasirProvider.ANTHROPIC;
        assertThat(client.converse(convo(), null, "k".toCharArray())).isEqualTo("claude says");
        assertThat(spy.urls.get(2)).isEqualTo(KvasirClient.ENDPOINT);
        assertThat(new JSONObject(spy.bodies.get(2)).getString("model")).isEqualTo(KvasirClient.MODEL_HAIKU);
    }

    @Test
    @DisplayName("The transport seam's three-argument form still serves every existing spy (Claude wire)")
    void legacySpyFormStillWorks() throws IOException {
        List<String> seen = new ArrayList<>();
        KvasirClient client = new KvasirClient((url, body, key) -> {
            seen.add(url);
            return new JSONObject().put("content", new JSONArray().put(
                    new JSONObject().put("type", "text").put("text", "ok"))).toString();
        });
        assertThat(client.provider()).isEqualTo(KvasirProvider.ANTHROPIC);
        assertThat(client.converse(convo(), KvasirClient.MODEL_HAIKU, "k".toCharArray())).isEqualTo("ok");
        assertThat(seen).containsExactly(KvasirClient.ENDPOINT);
    }
}
