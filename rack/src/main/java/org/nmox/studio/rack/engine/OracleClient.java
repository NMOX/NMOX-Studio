package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.http.HttpClientFactory;

/**
 * The ORACLE model call: it asks the Anthropic Messages API to explain a
 * failed run, in plain HTTPS+JSON over the IDE's shared HTTP pool. No SDK,
 * no key material held here beyond the single call.
 *
 * <p><b>The seam</b> ({@link Transport}) is the {@code JsonRpcClient}
 * idiom: production posts over {@link HttpClientFactory#shared()}; tests
 * inject a canned transport that never opens a socket, so prompt assembly
 * and response parsing are unit-testable with zero network.
 *
 * <p><b>The key</b> travels only in the {@code x-api-key} request header,
 * never in the URL (the endpoint is a fixed HTTPS constant) and never in
 * the request body (which carries only the failure context). It is never
 * logged, thrown, or {@link #toString()}ed. The prompt sent to the model
 * is exactly {@link FailureContext} — a failing command, its exit code,
 * up to five sampled error lines, the device name and the project name —
 * and nothing else: no source, no environment, no secrets.
 *
 * <p>Synchronous; never call from the EDT.
 */
public final class OracleClient {

    /** The Anthropic Messages endpoint — HTTPS, fixed, no secrets in the URL. */
    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    /** The API version pin the Messages API requires. */
    static final String API_VERSION = "2023-06-01";
    /** The cheap default; the SONNET knob position swaps in {@link #MODEL_SONNET}. */
    public static final String MODEL_HAIKU = "claude-haiku-4-5";
    /** The stronger alternate — a bare alias, no date suffix (dossier §3). */
    public static final String MODEL_SONNET = "claude-sonnet-5";
    private static final int MAX_TOKENS = 1024;
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Everything ORACLE sends, and the whole of it. Assembled from the
     * flight recorder's already-captured, already-bounded context; this
     * record IS the disclosure the consent dialog and user guide promise.
     */
    public record FailureContext(String device, String command, int exitCode,
            List<String> errorLines, String projectName, long durationMs) {

        public FailureContext {
            errorLines = errorLines == null ? List.of() : List.copyOf(errorLines);
        }

        /**
         * Reconstructs the most recent failed run from the recorder: the
         * last EXIT_FAIL, the LAUNCH command that preceded it, and that
         * run's sampled ERROR lines. Empty when the last completed run
         * passed, or nothing has run yet.
         */
        public static java.util.Optional<FailureContext> fromRecorder(
                FlightRecorder rec, String projectName) {
            FlightRecorder.Event last = rec.last();
            if (last == null || last.kind() != FlightRecorder.Kind.EXIT_FAIL) {
                return java.util.Optional.empty();
            }
            String device = last.device();
            long runStart = last.at() - Math.max(0, last.durationMs());
            String command = "";
            List<String> errors = new ArrayList<>();
            for (FlightRecorder.Event e : rec.timeline()) {
                if (!e.device().equals(device)) {
                    continue;
                }
                if (e.kind() == FlightRecorder.Kind.LAUNCH && e.at() <= last.at()) {
                    command = e.text(); // keep the latest launch at or before the failure
                } else if (e.kind() == FlightRecorder.Kind.ERROR && e.at() >= runStart) {
                    errors.add(e.text());
                }
            }
            int code = FlightRecorder.parseExit(last.text());
            return java.util.Optional.of(new FailureContext(
                    device, command, code, errors, projectName, last.durationMs()));
        }
    }

    /** How a request body reaches the API; the seam tests inject. */
    public interface Transport {

        /**
         * POSTs the JSON body with the API key in the {@code x-api-key}
         * header, returning the response body.
         *
         * @throws IOException when the API can't be reached — the message
         *         must never contain the key
         */
        String post(String url, String jsonBody, char[] apiKey) throws IOException;
    }

    private final Transport transport;

    /** Production client over the IDE's shared HTTP pool. */
    public OracleClient() {
        this(httpTransport());
    }

    /** Seam constructor — tests hand in a canned transport. */
    public OracleClient(Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * Explains a failed run. {@code model} is a bare model id
     * ({@link #MODEL_HAIKU}/{@link #MODEL_SONNET}); {@code apiKey} is the
     * Anthropic key. The caller keeps ownership of the key array.
     *
     * @throws IOException on transport failure, an API error, or a model
     *         refusal — the device turns each into an honest status line
     */
    public String explain(FailureContext ctx, String model, char[] apiKey) throws IOException {
        String body = requestBody(model, assemblePrompt(ctx));
        String response = transport.post(ENDPOINT, body, apiKey);
        return parseExplanation(response);
    }

    // ---- pure, unit-testable core -----------------------------------------

    /**
     * The prompt, a pure function of the failure context. Deterministic
     * and side-effect free so a test can assert it verbatim. Spells out
     * to the model exactly the bounded facts it is given.
     */
    static String assemblePrompt(FailureContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("A command failed while developing a software project. ")
                .append("Explain in plain language what most likely went wrong and ")
                .append("the concrete next step to fix it. Be concise: a short ")
                .append("diagnosis, then a fix. You are given only what is below — ")
                .append("no source files, no environment, no secrets.\n\n");
        sb.append("Project: ").append(nz(ctx.projectName())).append('\n');
        sb.append("Device (task lane): ").append(nz(ctx.device())).append('\n');
        sb.append("Command: ").append(nz(ctx.command())).append('\n');
        sb.append("Exit code: ").append(ctx.exitCode()).append('\n');
        if (ctx.errorLines().isEmpty()) {
            sb.append("Error output: (none captured)\n");
        } else {
            sb.append("Error output (up to 5 sampled lines):\n");
            for (String line : ctx.errorLines()) {
                sb.append("  ").append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "(unknown)" : s;
    }

    /** The Messages request envelope: model, token cap, one user turn. */
    static String requestBody(String model, String prompt) {
        return new JSONObject()
                .put("model", model == null || model.isBlank() ? MODEL_HAIKU : model)
                .put("max_tokens", MAX_TOKENS)
                .put("messages", new JSONArray().put(
                        new JSONObject().put("role", "user").put("content", prompt)))
                .toString();
    }

    /**
     * Pulls {@code content[0].text} out of a Messages response. A refusal
     * ({@code stop_reason == "refusal"}) or a response with no text block
     * is surfaced as an honest {@link IOException}, never an empty string
     * pretending to be an answer. An API error object is surfaced too.
     */
    static String parseExplanation(String responseJson) throws IOException {
        JSONObject root;
        try {
            root = new JSONObject(responseJson == null ? "" : responseJson);
        } catch (RuntimeException notJson) {
            throw new IOException("ORACLE did not answer with JSON.");
        }
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            // the API's own error message — carries no key (key is header-only)
            throw new IOException("ORACLE error: " + error.optString("message", "unknown"));
        }
        if ("refusal".equals(root.optString("stop_reason", ""))) {
            throw new IOException("ORACLE declined to answer this one.");
        }
        JSONArray content = root.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block != null && "text".equals(block.optString("type", "text"))) {
                    String text = block.optString("text", "");
                    if (!text.isBlank()) {
                        return text.trim();
                    }
                }
            }
        }
        throw new IOException("ORACLE returned no explanation.");
    }

    // ---- the production transport -----------------------------------------

    /** POST over the shared pool; failures never echo the key. */
    static Transport httpTransport() {
        return (url, jsonBody, apiKey) -> {
            // The header API takes a String; the key is stringified only for
            // the lifetime of this request and never stored, logged, or
            // returned. It rides x-api-key only — never the URL or the body.
            String keyHeader = apiKey == null ? "" : new String(apiKey);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("content-type", "application/json")
                    .header("anthropic-version", API_VERSION)
                    .header("x-api-key", keyHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response;
            try {
                response = HttpClientFactory.shared()
                        .send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reaching ORACLE.");
            } catch (IOException e) {
                // no cause chained: a transport message could echo the key
                throw new IOException("Cannot reach ORACLE — " + e.getClass().getSimpleName());
            }
            // 4xx/5xx bodies from Anthropic carry a JSON error we parse for a
            // real message; hand the body up rather than a bare status code.
            return response.body();
        };
    }
}
