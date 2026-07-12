package org.nmox.studio.rack.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ORACLE model client over a canned transport — no sockets anywhere.
 * Pins prompt assembly (a pure function), the request envelope, response
 * parsing (text, refusal, error, empty), and the reconstruction of a
 * failed run from the flight recorder's already-captured context.
 */
class OracleClientTest {

    private static FailureContext ctx() {
        return new FailureContext("VERITAS", "npm test", 1,
                List.of("FAIL src/app.test.js", "Expected 2, received 3"),
                "my-app", 4200);
    }

    /** Records posts and replays a canned response; never opens a socket. */
    private static final class SpyTransport implements OracleClient.Transport {

        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        String canned;
        IOException toThrow;

        SpyTransport(String canned) {
            this.canned = canned;
        }

        @Override
        public String post(String url, String jsonBody, char[] apiKey) throws IOException {
            urls.add(url);
            bodies.add(jsonBody);
            keys.add(apiKey == null ? "" : new String(apiKey));
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

    // ---- prompt assembly: a pure function ----------------------------------

    @Test
    @DisplayName("assemblePrompt names exactly the bounded facts and nothing else")
    void promptContainsOnlyTheContext() {
        String prompt = OracleClient.assemblePrompt(ctx());
        assertThat(prompt)
                .contains("Project: my-app")
                .contains("Device (task lane): VERITAS")
                .contains("Command: npm test")
                .contains("Exit code: 1")
                .contains("FAIL src/app.test.js")
                .contains("Expected 2, received 3")
                .contains("no source files, no environment, no secrets");
    }

    @Test
    @DisplayName("assemblePrompt is deterministic and side-effect free")
    void promptIsPure() {
        FailureContext c = ctx();
        assertThat(OracleClient.assemblePrompt(c)).isEqualTo(OracleClient.assemblePrompt(c));
    }

    @Test
    @DisplayName("assemblePrompt is honest when no error lines were captured")
    void promptWithNoErrors() {
        FailureContext c = new FailureContext("FORGE", "vite build", 2,
                List.of(), "site", -1);
        assertThat(OracleClient.assemblePrompt(c)).contains("Error output: (none captured)");
    }

    // ---- the request envelope ----------------------------------------------

    @Test
    @DisplayName("requestBody carries model, a token cap, and one user turn")
    void requestBodyShape() {
        JSONObject body = new JSONObject(
                OracleClient.requestBody("claude-haiku-4-5", "why did it fail?"));
        assertThat(body.getString("model")).isEqualTo("claude-haiku-4-5");
        assertThat(body.getInt("max_tokens")).isPositive();
        JSONObject msg = body.getJSONArray("messages").getJSONObject(0);
        assertThat(msg.getString("role")).isEqualTo("user");
        assertThat(msg.getString("content")).isEqualTo("why did it fail?");
    }

    @Test
    @DisplayName("requestBody falls back to HAIKU on a blank model")
    void requestBodyDefaultsModel() {
        assertThat(new JSONObject(OracleClient.requestBody("  ", "x")).getString("model"))
                .isEqualTo(OracleClient.MODEL_HAIKU);
    }

    // ---- response parsing --------------------------------------------------

    @Test
    @DisplayName("parseExplanation returns content[0].text, trimmed")
    void parseText() throws IOException {
        assertThat(OracleClient.parseExplanation(messageResponse("  Your test asserts the wrong value.  ")))
                .isEqualTo("Your test asserts the wrong value.");
    }

    @Test
    @DisplayName("a refusal is an honest exception, never an empty answer")
    void parseRefusal() {
        String refusal = new JSONObject().put("stop_reason", "refusal")
                .put("content", new org.json.JSONArray()).toString();
        assertThatThrownBy(() -> OracleClient.parseExplanation(refusal))
                .isInstanceOf(IOException.class).hasMessageContaining("declined");
    }

    @Test
    @DisplayName("an API error object surfaces its message")
    void parseError() {
        String err = new JSONObject().put("error",
                new JSONObject().put("type", "authentication_error")
                        .put("message", "invalid x-api-key")).toString();
        assertThatThrownBy(() -> OracleClient.parseExplanation(err))
                .isInstanceOf(IOException.class).hasMessageContaining("invalid x-api-key");
    }

    @Test
    @DisplayName("a non-JSON body is an honest exception")
    void parseNotJson() {
        assertThatThrownBy(() -> OracleClient.parseExplanation("<html>502</html>"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("a response with no text block is an honest exception")
    void parseNoText() {
        String empty = new JSONObject().put("content", new org.json.JSONArray()).toString();
        assertThatThrownBy(() -> OracleClient.parseExplanation(empty))
                .isInstanceOf(IOException.class).hasMessageContaining("no explanation");
    }

    // ---- the whole call over the seam --------------------------------------

    @Test
    @DisplayName("explain posts to the HTTPS endpoint, passes the key, returns the text")
    void explainOverSeam() throws IOException {
        SpyTransport spy = new SpyTransport(messageResponse("Bump the dependency."));
        String out = new OracleClient(spy).explain(ctx(), "claude-sonnet-5", "sk-secret".toCharArray());

        assertThat(out).isEqualTo("Bump the dependency.");
        assertThat(spy.urls).containsExactly("https://api.anthropic.com/v1/messages");
        assertThat(spy.urls.get(0)).startsWith("https://"); // HTTPS only
        assertThat(spy.keys).containsExactly("sk-secret");   // key travels to the transport
        assertThat(spy.bodies.get(0)).contains("claude-sonnet-5").contains("npm test");
    }

    // ---- reconstructing the failed run from the recorder -------------------

    @Test
    @DisplayName("fromRecorder rebuilds the last failed run: command, code, sampled errors")
    void fromRecorderBuildsContext() {
        FlightRecorder rec = new FlightRecorder(fixedClock());
        rec.line("VERITAS", "$ npm test", false);
        rec.line("VERITAS", "FAIL one", true);
        rec.line("VERITAS", "FAIL two", true);
        rec.line("VERITAS", "[exit 1]", true);

        Optional<FailureContext> maybe = FailureContext.fromRecorder(rec, "proj");
        assertThat(maybe).isPresent();
        FailureContext c = maybe.get();
        assertThat(c.device()).isEqualTo("VERITAS");
        assertThat(c.command()).isEqualTo("npm test");
        assertThat(c.exitCode()).isEqualTo(1);
        assertThat(c.errorLines()).containsExactly("FAIL one", "FAIL two");
        assertThat(c.projectName()).isEqualTo("proj");
    }

    @Test
    @DisplayName("fromRecorder is empty when the last completed run passed")
    void fromRecorderEmptyOnSuccess() {
        FlightRecorder rec = new FlightRecorder(fixedClock());
        rec.line("FORGE", "$ vite build", false);
        rec.line("FORGE", "[exit 0]", false);
        assertThat(FailureContext.fromRecorder(rec, "proj")).isEmpty();
    }

    @Test
    @DisplayName("fromRecorder is empty when nothing has run")
    void fromRecorderEmptyWhenIdle() {
        assertThat(FailureContext.fromRecorder(new FlightRecorder(fixedClock()), "proj")).isEmpty();
    }

    private static java.util.function.LongSupplier fixedClock() {
        long[] t = {1_000L};
        return () -> t[0]++; // monotonic so launch precedes exit
    }
}
