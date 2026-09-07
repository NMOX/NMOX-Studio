package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.rack.engine.KvasirClient.Turn;
import org.openide.util.NbPreferences;

/**
 * The model behind KVASIR: Claude (Anthropic), ChatGPT (OpenAI) or Gemini
 * (Google). One provider is configured for the whole product at a time —
 * the device's KEY… dialog and Options ▸ Rack &amp; Cloud pick it — and
 * every KVASIR face (EXPLAIN, Ask, Edit, Complete, Draft Commit Message,
 * the studio explainers) follows that choice on its next send.
 *
 * <p>Each constant owns the whole wire contract for its vendor: the
 * endpoint, the header the key rides in, the request envelope built from
 * the same {@link Turn} list every engine already produces, and the
 * response parse with the same honest failure modes (a refusal, an API
 * error, an empty answer — each an {@link IOException} the UI turns into a
 * status line, never an empty string pretending to be an answer).
 * The prompts themselves are provider-independent by construction: the
 * engines assemble text, this enum only wraps it.
 *
 * <p><b>What stays the same across providers:</b> the key is keychain-only
 * (one entry per provider — {@link #keyringName()}), the key travels in a
 * request header only (never the URL, never the body), every consent is
 * granted per provider (a yes given to send output to Anthropic is not a
 * yes to send it to Google), and nothing reaches the network without the
 * button press.
 *
 * <p><b>Model ids rot.</b> The two ids per provider ({@link Depth#FAST},
 * {@link Depth#DEEP}) are the vendors' stable aliases at ship time; a user
 * whose vendor retired one can pin another without waiting for a release
 * through the preference {@code kvasir.model.<provider>.<fast|deep>} in
 * this class's NbPreferences node (an id that is not URL-safe is ignored,
 * so a typo can never build a bad request).
 */
public enum KvasirProvider {

    /** Claude — the Anthropic Messages API (the original KVASIR wire). */
    ANTHROPIC("anthropic", "Claude (Anthropic)", "Anthropic", "Claude",
            "claude-haiku-4-5", "Haiku", "claude-sonnet-5", "Sonnet",
            "nmox.kvasir.apikey", "ANTHROPIC_API_KEY", "CLAUDE_API_KEY"),
    /** ChatGPT — the OpenAI Chat Completions API. */
    OPENAI("openai", "ChatGPT (OpenAI)", "OpenAI", "ChatGPT",
            "gpt-5-mini", "GPT-5 mini", "gpt-5", "GPT-5",
            "nmox.kvasir.openai.apikey", "OPENAI_API_KEY"),
    /** Gemini — the Google Generative Language API. */
    GOOGLE("google", "Gemini (Google)", "Google", "Gemini",
            // measured 2026-09-07 against the live API: 2.5-pro answers 404
            // "no longer available to new users"; 3.1-pro-preview answers
            "gemini-2.5-flash", "Flash", "gemini-3.1-pro-preview", "Pro",
            "nmox.kvasir.google.apikey", "GEMINI_API_KEY", "GOOGLE_API_KEY");

    /** The two depths every provider offers: cheap-and-quick, or stronger. */
    public enum Depth { FAST, DEEP }

    /** The OpenAI Chat Completions endpoint — HTTPS, fixed, no secrets in the URL. */
    static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    /** The Gemini endpoint prefix; the model id joins the path, the key never does. */
    static final String GOOGLE_ENDPOINT_PREFIX =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    /** Output cap for the two vendors whose models spend part of it thinking. */
    static final int OUTPUT_TOKENS = 4096;
    /** The preference naming the configured provider (by {@link #id()}). */
    static final String PROVIDER_PREF = "kvasir.provider";
    /** A model id that can join a URL path and a JSON string without escaping. */
    private static final Pattern URL_SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,120}");

    private final String id;
    private final String label;
    private final String vendor;
    private final String product;
    private final String fastModel;
    private final String fastLabel;
    private final String deepModel;
    private final String deepLabel;
    private final String keyringName;
    private final String[] envVars;

    KvasirProvider(String id, String label, String vendor, String product,
            String fastModel, String fastLabel, String deepModel, String deepLabel,
            String keyringName, String... envVars) {
        this.id = id;
        this.label = label;
        this.vendor = vendor;
        this.product = product;
        this.fastModel = fastModel;
        this.fastLabel = fastLabel;
        this.deepModel = deepModel;
        this.deepLabel = deepLabel;
        this.keyringName = keyringName;
        this.envVars = envVars;
    }

    /** The stable lowercase id the preference stores ({@code anthropic|openai|google}). */
    public String id() {
        return id;
    }

    /** The combo-box label: product first, vendor in parentheses. */
    public String label() {
        return label;
    }

    /** The company whose API receives the request — what the consent dialog names. */
    public String vendor() {
        return vendor;
    }

    /** The product name users know: Claude, ChatGPT, Gemini. */
    public String product() {
        return product;
    }

    /** The keychain entry this provider's key lives under. */
    public String keyringName() {
        return keyringName;
    }

    /** The environment fallbacks, first non-blank wins. */
    public String[] envVars() {
        return envVars.clone();
    }

    /** The env-var names joined for a status line: {@code A / B}. */
    public String envHint() {
        return String.join(" / ", envVars);
    }

    /** The vendor's own name for a depth ("Haiku", "GPT-5 mini", "Flash"). */
    public String depthLabel(Depth depth) {
        return depth == Depth.DEEP ? deepLabel : fastLabel;
    }

    /** The shipped default id for a depth — what {@link #model} returns unless pinned. */
    public String defaultModel(Depth depth) {
        return depth == Depth.DEEP ? deepModel : fastModel;
    }

    /**
     * The model id for a depth: the user's pinned override when it is a
     * well-formed id, else the shipped default. Never blank, always
     * URL-safe, so a request built from it is always well-formed.
     */
    public String model(Depth depth) {
        String pinned = Prefs.node().get(modelPref(depth), "");
        return pinned != null && URL_SAFE_ID.matcher(pinned.trim()).matches()
                ? pinned.trim() : defaultModel(depth);
    }

    /** The preference key a user pins a model under. */
    String modelPref(Depth depth) {
        return "kvasir.model." + id + "." + depth.name().toLowerCase(Locale.ROOT);
    }

    /** Which depth a model id is FOR THIS provider, if it is one of ours. */
    public Optional<Depth> depthOf(String modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        for (Depth d : Depth.values()) {
            if (modelId.equals(model(d)) || modelId.equals(defaultModel(d))) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** Which depth a model id is for ANY provider — the cross-provider bridge. */
    public static Optional<Depth> depthOfAny(String modelId) {
        for (KvasirProvider p : values()) {
            Optional<Depth> d = p.depthOf(modelId);
            if (d.isPresent()) {
                return d;
            }
        }
        return Optional.empty();
    }

    /**
     * The id this provider actually sends for a requested model: blank →
     * FAST; one of ours → itself; another provider's id → OUR id at the
     * same depth (a remembered Claude depth becomes the matching Gemini
     * depth, never a Claude id in a Gemini request); anything else →
     * passed through as an explicit custom id, provided it is URL-safe.
     */
    public String resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return model(Depth.FAST);
        }
        String r = requested.trim();
        if (depthOf(r).isPresent()) {
            return r;
        }
        Optional<Depth> foreign = depthOfAny(r);
        if (foreign.isPresent()) {
            return model(foreign.get());
        }
        return URL_SAFE_ID.matcher(r).matches() ? r : model(Depth.FAST);
    }

    // ---- the wire: endpoint, auth, envelope, parse ------------------------

    /** The POST target for a (resolved) model id. Never carries a secret. */
    public String endpoint(String model) {
        return switch (this) {
            case ANTHROPIC -> KvasirClient.ENDPOINT;
            case OPENAI -> OPENAI_ENDPOINT;
            case GOOGLE -> GOOGLE_ENDPOINT_PREFIX + resolve(model) + ":generateContent";
        };
    }

    /**
     * The request headers that carry the key — and only them. The key
     * appears in exactly one header value; the transport adds
     * content-type itself. The String form of the key lives only for the
     * request's lifetime.
     */
    public Map<String, String> authHeaders(String apiKey) {
        Map<String, String> h = new LinkedHashMap<>();
        String key = apiKey == null ? "" : apiKey;
        switch (this) {
            case ANTHROPIC -> {
                h.put("x-api-key", key);
                h.put("anthropic-version", KvasirClient.API_VERSION);
            }
            case OPENAI -> h.put("Authorization", "Bearer " + key);
            case GOOGLE -> h.put("x-goog-api-key", key);
        }
        return h;
    }

    /** The request envelope for a whole conversation, in this vendor's shape. */
    public String requestBody(String model, List<Turn> turns) {
        String m = resolve(model);
        return switch (this) {
            case ANTHROPIC -> KvasirClient.requestBodyConversation(m, turns);
            case OPENAI -> openAiBody(m, turns);
            case GOOGLE -> geminiBody(turns);
        };
    }

    /** The answer text out of this vendor's response, or an honest exception. */
    public String parse(String responseJson) throws IOException {
        return switch (this) {
            case ANTHROPIC -> KvasirClient.parseExplanation(responseJson);
            case OPENAI -> parseOpenAi(responseJson);
            case GOOGLE -> parseGemini(responseJson);
        };
    }

    // ---- OpenAI Chat Completions ------------------------------------------

    /** {@code {model, max_completion_tokens, messages:[{role,content}]}}. */
    static String openAiBody(String model, List<Turn> turns) {
        JSONArray messages = new JSONArray();
        for (Turn t : turns) {
            messages.put(new JSONObject().put("role", t.role()).put("content", t.text()));
        }
        return new JSONObject()
                .put("model", model)
                // max_tokens is refused by the reasoning models; this is the
                // name every current chat model accepts
                .put("max_completion_tokens", OUTPUT_TOKENS)
                .put("messages", messages)
                .toString();
    }

    /**
     * {@code choices[0].message.content}; a non-empty {@code refusal} or a
     * {@code content_filter} finish is a refusal; an {@code error} object
     * is the API's own message (key-free — the key is header-only).
     */
    static String parseOpenAi(String responseJson) throws IOException {
        JSONObject root = rootOf(responseJson);
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            throw new IOException("KVASIR error: " + error.optString("message", "unknown"));
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("KVASIR returned no explanation.");
        }
        JSONObject first = choices.optJSONObject(0);
        JSONObject message = first == null ? null : first.optJSONObject("message");
        if (message == null) {
            throw new IOException("KVASIR returned no explanation.");
        }
        String refusal = message.optString("refusal", "");
        if (!refusal.isBlank() || "content_filter".equals(first.optString("finish_reason", ""))) {
            throw new IOException("KVASIR declined to answer this one.");
        }
        Object content = message.opt("content");
        String text;
        if (content instanceof JSONArray parts) {
            // the content-parts form some compatible servers answer with
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) {
                    sb.append(part.optString("text", ""));
                }
            }
            text = sb.toString();
        } else {
            text = message.optString("content", "");
        }
        if (text.isBlank()) {
            throw new IOException("KVASIR returned no explanation.");
        }
        return text.trim();
    }

    // ---- Google Gemini generateContent ------------------------------------

    /**
     * {@code {contents:[{role:user|model, parts:[{text}]}], generationConfig}}.
     * Gemini calls the assistant side {@code model}; the model id rides
     * the URL, not the body.
     */
    static String geminiBody(List<Turn> turns) {
        JSONArray contents = new JSONArray();
        for (Turn t : turns) {
            contents.put(new JSONObject()
                    .put("role", "assistant".equals(t.role()) ? "model" : "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", t.text()))));
        }
        return new JSONObject()
                .put("contents", contents)
                .put("generationConfig", new JSONObject().put("maxOutputTokens", OUTPUT_TOKENS))
                .toString();
    }

    /** The finish reasons that mean the model would not answer. */
    private static final List<String> GEMINI_REFUSALS = List.of(
            "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY");

    /**
     * The text parts of {@code candidates[0]} (thought parts skipped); a
     * blocked prompt or a safety finish with no text is a refusal; an
     * {@code error} object is the API's own message.
     */
    static String parseGemini(String responseJson) throws IOException {
        JSONObject root = rootOf(responseJson);
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            throw new IOException("KVASIR error: " + error.optString("message", "unknown"));
        }
        JSONObject feedback = root.optJSONObject("promptFeedback");
        if (feedback != null && !feedback.optString("blockReason", "").isBlank()) {
            throw new IOException("KVASIR declined to answer this one.");
        }
        JSONArray candidates = root.optJSONArray("candidates");
        JSONObject first = candidates == null || candidates.isEmpty()
                ? null : candidates.optJSONObject(0);
        if (first == null) {
            throw new IOException("KVASIR returned no explanation.");
        }
        StringBuilder sb = new StringBuilder();
        JSONObject content = first.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && !part.optBoolean("thought", false)) {
                    sb.append(part.optString("text", ""));
                }
            }
        }
        String text = sb.toString();
        if (text.isBlank()) {
            if (GEMINI_REFUSALS.contains(first.optString("finishReason", ""))) {
                throw new IOException("KVASIR declined to answer this one.");
            }
            throw new IOException("KVASIR returned no explanation.");
        }
        return text.trim();
    }

    private static JSONObject rootOf(String responseJson) throws IOException {
        try {
            return new JSONObject(responseJson == null ? "" : responseJson);
        } catch (RuntimeException notJson) {
            throw new IOException("KVASIR did not answer with JSON.");
        }
    }

    // ---- the configured provider ------------------------------------------

    /** The provider every KVASIR face uses now; ANTHROPIC until chosen otherwise. */
    public static KvasirProvider configured() {
        return fromId(Prefs.node().get(PROVIDER_PREF, "")).orElse(ANTHROPIC);
    }

    /** Remembers the provider for every later send. */
    public static void remember(KvasirProvider provider) {
        Prefs.node().put(PROVIDER_PREF, provider.id);
    }

    /** The provider for a stored id; empty for junk. */
    public static Optional<KvasirProvider> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (KvasirProvider p : values()) {
            if (p.id.equals(wanted)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Test hook: forget the provider choice and every pinned model id. */
    static void resetForTest() {
        Prefs.node().remove(PROVIDER_PREF);
        for (KvasirProvider p : values()) {
            for (Depth d : Depth.values()) {
                Prefs.node().remove(p.modelPref(d));
            }
        }
    }

    /** Test hook: pin a model id the way a user's preference would. */
    static void pinModelForTest(KvasirProvider p, Depth d, String modelId) {
        Prefs.node().put(p.modelPref(d), modelId);
    }

    /** Lazy holder: the node is touched on first use, never at class load. */
    private static final class Prefs {

        static Preferences node() {
            return NbPreferences.forModule(KvasirProvider.class);
        }
    }
}
