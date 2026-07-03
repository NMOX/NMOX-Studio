package org.nmox.studio.apiclient.api;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire-request builder: exact query encoding, which methods carry
 * a body, the shapes of both auth headers, and Content-Type sniffing -
 * with {{variables}} resolved everywhere along the way.
 */
class ApiClientBuildTest {

    private static Request request(String method, String url) {
        Request r = new Request();
        r.method = method;
        r.url = url;
        return r;
    }

    @Test
    @DisplayName("Query names and values are form-encoded; disabled and nameless params vanish")
    void queryParamsAreEncodedAndFiltered() {
        Request r = request("GET", "https://x.dev/search");
        r.params.add(new Pair("q", "hello world"));
        r.params.add(new Pair("filter", "a&b=c"));
        Pair off = new Pair("debug", "1");
        off.enabled = false;
        r.params.add(off);
        r.params.add(new Pair("  ", "nameless"));
        r.params.add(new Pair("empty", null));

        HttpRequest built = ApiClient.build(r, Map.of());

        assertThat(built.uri().toString())
                .isEqualTo("https://x.dev/search?q=hello+world&filter=a%26b%3Dc&empty=");
    }

    @Test
    @DisplayName("Params append with & when the URL already carries a query string")
    void paramsAppendToExistingQuery() {
        Request r = request("GET", "https://x.dev/s?page=2");
        r.params.add(new Pair("q", "term"));

        assertThat(ApiClient.build(r, Map.of()).uri().toString())
                .isEqualTo("https://x.dev/s?page=2&q=term");
    }

    @Test
    @DisplayName("POST, PUT and PATCH carry the body; GET and DELETE never do")
    void onlyBodyMethodsCarryTheBody() {
        String body = "{\"name\":\"ada\"}";
        for (String method : new String[]{"POST", "PUT", "PATCH"}) {
            Request r = request(method, "https://x.dev/items");
            r.body = body;
            HttpRequest built = ApiClient.build(r, Map.of());
            assertThat(built.method()).isEqualTo(method);
            assertThat(built.bodyPublisher().orElseThrow().contentLength())
                    .as(method + " carries its body")
                    .isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
        }
        for (String method : new String[]{"GET", "DELETE"}) {
            Request r = request(method, "https://x.dev/items");
            r.body = body;
            HttpRequest built = ApiClient.build(r, Map.of());
            assertThat(built.bodyPublisher().orElseThrow().contentLength())
                    .as(method + " sends no body even when one is typed")
                    .isZero();
        }
    }

    @Test
    @DisplayName("A blank body on POST sends nothing and sniffs no Content-Type")
    void blankBodySendsNothing() {
        Request r = request("POST", "https://x.dev/items");
        r.body = "   ";

        HttpRequest built = ApiClient.build(r, Map.of());

        assertThat(built.bodyPublisher().orElseThrow().contentLength()).isZero();
        assertThat(built.headers().firstValue("Content-Type")).isEmpty();
    }

    @Test
    @DisplayName("Bearer auth resolves its variable and trims; an empty token adds no header")
    void bearerAuth() {
        Request r = request("GET", "https://x.dev");
        r.authType = AuthType.BEARER;
        r.authToken = "{{token}}";
        HttpRequest built = ApiClient.build(r, Map.of("token", "  abc123  "));
        assertThat(built.headers().firstValue("Authorization")).hasValue("Bearer abc123");

        Request blank = request("GET", "https://x.dev");
        blank.authType = AuthType.BEARER;
        blank.authToken = "  ";
        assertThat(ApiClient.build(blank, Map.of()).headers().firstValue("Authorization"))
                .isEmpty();

        Request none = request("GET", "https://x.dev");
        none.authType = AuthType.NONE;
        none.authToken = "abc123";
        assertThat(ApiClient.build(none, Map.of()).headers().firstValue("Authorization"))
                .as("NONE ignores a leftover token").isEmpty();
    }

    @Test
    @DisplayName("Basic auth is Base64 of user:pass; a token without a colon is refused")
    void basicAuth() {
        Request r = request("GET", "https://x.dev");
        r.authType = AuthType.BASIC;
        r.authToken = "{{user}}:{{pass}}";
        HttpRequest built = ApiClient.build(r, Map.of("user", "ada", "pass", "l0v3lace"));
        String expected = Base64.getEncoder()
                .encodeToString("ada:l0v3lace".getBytes(StandardCharsets.UTF_8));
        assertThat(built.headers().firstValue("Authorization")).hasValue("Basic " + expected);

        Request noColon = request("GET", "https://x.dev");
        noColon.authType = AuthType.BASIC;
        noColon.authToken = "justtoken";
        assertThat(ApiClient.build(noColon, Map.of()).headers().firstValue("Authorization"))
                .as("basic without user:pass shape is refused").isEmpty();
    }

    @Test
    @DisplayName("A JSON body sniffs application/json only when no explicit Content-Type exists")
    void contentTypeSniffYieldsToExplicit() {
        Request sniffed = request("POST", "https://x.dev/items");
        sniffed.body = "{\"a\":1}";
        assertThat(ApiClient.build(sniffed, Map.of()).headers().firstValue("Content-Type"))
                .hasValue("application/json");

        Request explicit = request("POST", "https://x.dev/items");
        explicit.body = "{\"a\":1}";
        explicit.headers.add(new Pair("Content-Type", "text/plain"));
        assertThat(ApiClient.build(explicit, Map.of()).headers().allValues("Content-Type"))
                .as("explicit type wins, no json added beside it")
                .containsExactly("text/plain");

        Request plain = request("POST", "https://x.dev/items");
        plain.body = "just words";
        assertThat(ApiClient.build(plain, Map.of()).headers().firstValue("Content-Type"))
                .as("a non-JSON body sniffs nothing").isEmpty();
    }

    @Test
    @DisplayName("Variables resolve in URL, params, headers and body; unknowns stay visible")
    void variablesResolveEverywhere() {
        Request r = request("POST", "{{base}}/users");
        r.params.add(new Pair("team", "{{team}}"));
        r.headers.add(new Pair("X-Api-Key", "{{key}}"));
        r.headers.add(new Pair("X-Debug", "{{missing}}"));
        r.body = "{\"name\":\"{{name}}\"}";
        Map<String, String> vars = Map.of(
                "base", "https://api.x.dev",
                "team", "blue",
                "key", "k-123",
                "name", "ada");

        HttpRequest built = ApiClient.build(r, vars);

        assertThat(built.uri().toString()).isEqualTo("https://api.x.dev/users?team=blue");
        assertThat(built.headers().firstValue("X-Api-Key")).hasValue("k-123");
        assertThat(built.headers().firstValue("X-Debug"))
                .as("unknown variable is left verbatim, not blanked").hasValue("{{missing}}");
        assertThat(built.bodyPublisher().orElseThrow().contentLength())
                .isEqualTo("{\"name\":\"ada\"}".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("Disabled headers and blank header names never reach the wire")
    void disabledHeadersAreDropped() {
        Request r = request("GET", "https://x.dev");
        Pair off = new Pair("X-Off", "1");
        off.enabled = false;
        r.headers.add(off);
        r.headers.add(new Pair("   ", "nameless"));
        r.headers.add(new Pair("X-On", "yes"));

        HttpRequest built = ApiClient.build(r, Map.of());

        assertThat(built.headers().firstValue("X-Off")).isEmpty();
        assertThat(built.headers().firstValue("X-On")).hasValue("yes");
    }
}
