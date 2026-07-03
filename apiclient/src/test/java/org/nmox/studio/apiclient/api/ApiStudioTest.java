package org.nmox.studio.apiclient.api;

import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The API Studio engine: variable substitution, request building,
 * assertions, and workspace persistence - the parts that must be
 * correct for the tab to be trustworthy.
 */
class ApiStudioTest {

    // ---- variables ----

    @Test
    @DisplayName("Variables resolve from the environment; unknowns stay visible")
    void variablesResolve() {
        Map<String, String> env = Map.of("base_url", "https://api.example.com", "id", "42");
        assertThat(Variables.resolve("{{base_url}}/users/{{id}}", env))
                .isEqualTo("https://api.example.com/users/42");
        assertThat(Variables.resolve("{{missing}}/x", env)).isEqualTo("{{missing}}/x");
        assertThat(Variables.referenced("{{a}}/{{b}}/{{a}}")).containsExactly("a", "b");
    }

    // ---- request building ----

    @Test
    @DisplayName("Build resolves url, appends enabled params, applies headers and bearer auth")
    void buildAssemblesTheRequest() {
        Request r = new Request();
        r.method = "GET";
        r.url = "{{base_url}}/search";
        r.params.add(new Pair("q", "hello world"));
        Pair off = new Pair("debug", "1");
        off.enabled = false;
        r.params.add(off);
        r.headers.add(new Pair("Accept", "application/json"));
        r.authType = AuthType.BEARER;
        r.authToken = "{{token}}";

        HttpRequest built = ApiClient.build(r,
                Map.of("base_url", "https://x.dev", "token", "abc123"));

        assertThat(built.uri().toString())
                .startsWith("https://x.dev/search?q=hello")
                .doesNotContain("debug");
        assertThat(built.headers().firstValue("Accept")).hasValue("application/json");
        assertThat(built.headers().firstValue("Authorization")).hasValue("Bearer abc123");
    }

    @Test
    @DisplayName("POST with a JSON body gets a JSON Content-Type unless one is set")
    void jsonBodyContentType() {
        Request r = new Request();
        r.method = "POST";
        r.url = "https://x.dev/items";
        r.body = "{\"name\":\"n\"}";
        HttpRequest built = ApiClient.build(r, Map.of());
        assertThat(built.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(built.bodyPublisher().get().contentLength()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Basic auth encodes user:password")
    void basicAuth() {
        Request r = new Request();
        r.url = "https://x.dev";
        r.authType = AuthType.BASIC;
        r.authToken = "user:pass";
        HttpRequest built = ApiClient.build(r, Map.of());
        // base64("user:pass") = dXNlcjpwYXNz
        assertThat(built.headers().firstValue("Authorization")).hasValue("Basic dXNlcjpwYXNz");
    }

    // ---- assertions ----

    @Test
    @DisplayName("Assertions judge status, time, body, JSON path, and headers")
    void assertionsEvaluate() {
        ApiResponse resp = new ApiResponse(200, 40, 12,
                Map.of("Content-Type", java.util.List.of("application/json")),
                "{\"data\":{\"user\":{\"id\":7}},\"items\":[{\"sku\":\"a\"}]}", null);

        assertThat(pass(Assertion.Kind.STATUS_IS, "200", resp)).isTrue();
        assertThat(pass(Assertion.Kind.STATUS_IS, "404", resp)).isFalse();
        assertThat(pass(Assertion.Kind.TIME_UNDER_MS, "100", resp)).isTrue();
        assertThat(pass(Assertion.Kind.TIME_UNDER_MS, "10", resp)).isFalse();
        assertThat(pass(Assertion.Kind.BODY_CONTAINS, "user", resp)).isTrue();
        assertThat(pass(Assertion.Kind.JSON_HAS_PATH, "data.user.id", resp)).isTrue();
        assertThat(pass(Assertion.Kind.JSON_HAS_PATH, "items.0.sku", resp)).isTrue();
        assertThat(pass(Assertion.Kind.JSON_HAS_PATH, "data.user.email", resp)).isFalse();
        assertThat(pass(Assertion.Kind.HEADER_PRESENT, "content-type", resp)).isTrue();
        assertThat(pass(Assertion.Kind.HEADER_PRESENT, "x-nope", resp)).isFalse();
    }

    @Test
    @DisplayName("A failed request reaches nothing; assertions on it fail cleanly")
    void failedRequestJudged() {
        ApiResponse resp = ApiResponse.failure(9000, "Connection refused");
        assertThat(resp.reached()).isFalse();
        assertThat(pass(Assertion.Kind.STATUS_IS, "200", resp)).isFalse();
    }

    private static boolean pass(Assertion.Kind kind, String target, ApiResponse resp) {
        return TestRunner.evaluate(new Assertion(kind, target), resp).passed();
    }

    // ---- persistence ----

    @Test
    @DisplayName("A workspace round-trips through JSON with all its parts")
    void workspaceRoundTrips(@TempDir Path dir) throws Exception {
        Workspace w = Workspace.starter();
        w.collections.get(0).requests.get(0).headers.add(new Pair("X-Api-Key", "k"));
        w.collections.get(0).requests.get(0).method = "POST";

        WorkspaceIO.save(dir.toFile(), w);
        Workspace back = WorkspaceIO.load(dir.toFile());

        assertThat(back).isNotNull();
        assertThat(back.activeEnvironment).isEqualTo("Local");
        assertThat(back.active().variables).containsEntry("base_url", "http://localhost:3000");
        var req = back.collections.get(0).requests.get(0);
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.headers).anyMatch(p -> "X-Api-Key".equals(p.name) && "k".equals(p.value));
        assertThat(req.tests).anyMatch(a -> a.kind == Assertion.Kind.STATUS_IS);
    }

    @Test
    @DisplayName("Loading from an empty directory returns null, not an error")
    void loadMissingIsNull(@TempDir Path dir) throws Exception {
        assertThat(WorkspaceIO.load(dir.toFile())).isNull();
    }

    @Test
    @DisplayName("Pretty-print indents JSON, passes other bodies through")
    void prettyPrints() {
        assertThat(WorkspaceIO.pretty("{\"a\":1,\"b\":2}")).contains("\n").contains("\"a\": 1");
        assertThat(WorkspaceIO.pretty("plain")).isEqualTo("plain");
    }
}
