package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * curl in, curl out — pinned. The parser is judged on real pasted
 * shapes (quotes, continuations, header lifts), the renderer on exact
 * shell text, and the pair on a field-level round trip.
 */
class CurlCodecTest {

    @Test
    @DisplayName("A bare curl URL imports as a named GET")
    void bareGet() {
        var got = CurlCodec.parse("curl https://api.example.com/users");
        assertThat(got.request().method).isEqualTo("GET");
        assertThat(got.request().url).isEqualTo("https://api.example.com/users");
        assertThat(got.request().name).isEqualTo("api.example.com/users");
        assertThat(got.notes()).isEmpty();
    }

    @Test
    @DisplayName("Quoted multi-line paste: headers land, data implies POST")
    void typicalPaste() {
        var got = CurlCodec.parse("""
                curl 'https://api.example.com/users?limit=5' \\
                  -H 'Content-Type: application/json' \\
                  --data '{"name":"Ada"}'
                """);
        ApiModel.Request r = got.request();
        assertThat(r.method).isEqualTo("POST");
        assertThat(r.url).isEqualTo("https://api.example.com/users?limit=5");
        assertThat(r.headers).hasSize(1);
        assertThat(r.headers.get(0).name).isEqualTo("Content-Type");
        assertThat(r.body).isEqualTo("{\"name\":\"Ada\"}");
    }

    @Test
    @DisplayName("-X wins over the data-implies-POST rule")
    void explicitMethodWins() {
        var got = CurlCodec.parse("curl -X PUT https://h/x -d 'a=1'");
        assertThat(got.request().method).isEqualTo("PUT");
    }

    @Test
    @DisplayName("--json sets body, method and both JSON headers")
    void jsonFlag() {
        var r = CurlCodec.parse("curl --json '{\"a\":1}' https://h/x").request();
        assertThat(r.method).isEqualTo("POST");
        assertThat(r.body).isEqualTo("{\"a\":1}");
        assertThat(r.headers).extracting(h -> h.name)
                .containsExactly("Content-Type", "Accept");
    }

    @Test
    @DisplayName("An Authorization: Bearer header is lifted into the Auth field (secrets law)")
    void bearerHeaderLifted() {
        var r = CurlCodec.parse(
                "curl -H 'Authorization: Bearer abc123' https://h/x").request();
        assertThat(r.authType).isEqualTo(AuthType.BEARER);
        assertThat(r.authToken).isEqualTo("abc123");
        // the token must NOT sit in the headers list, where it would
        // serialize into the committable .nmoxapi.json in plaintext
        assertThat(r.headers).isEmpty();
    }

    @Test
    @DisplayName("Authorization: Basic decodes into user:password")
    void basicHeaderDecoded() {
        var r = CurlCodec.parse(
                "curl -H 'Authorization: Basic dXNlcjpwYXNz' https://h/x").request();
        assertThat(r.authType).isEqualTo(AuthType.BASIC);
        assertThat(r.authToken).isEqualTo("user:pass");
        assertThat(r.headers).isEmpty();
    }

    @Test
    @DisplayName("-u user:pass is basic auth")
    void dashU() {
        var r = CurlCodec.parse("curl -u user:pass https://h/x").request();
        assertThat(r.authType).isEqualTo(AuthType.BASIC);
        assertThat(r.authToken).isEqualTo("user:pass");
    }

    @Test
    @DisplayName("-G moves data into the query string")
    void getWithData() {
        var r = CurlCodec.parse("curl -G -d a=1 -d b=2 https://h/x").request();
        assertThat(r.method).isEqualTo("GET");
        assertThat(r.url).isEqualTo("https://h/x?a=1&b=2");
        assertThat(r.body).isEmpty();
    }

    @Test
    @DisplayName("Multiple -d parts join with & (curl semantics)")
    void multiData() {
        var r = CurlCodec.parse("curl https://h/x -d a=1 -d b=2").request();
        assertThat(r.body).isEqualTo("a=1&b=2");
    }

    @Test
    @DisplayName("Honest refusals: forms, file bodies, ANSI-C quotes, no URL")
    void refusals() {
        assertThatThrownBy(() -> CurlCodec.parse("curl -F 'f=@x' https://h"))
                .hasMessageContaining("Multipart");
        assertThatThrownBy(() -> CurlCodec.parse("curl -d @body.json https://h"))
                .hasMessageContaining("File bodies");
        assertThatThrownBy(() -> CurlCodec.parse("curl $'https://h'"))
                .hasMessageContaining("ANSI-C");
        assertThatThrownBy(() -> CurlCodec.parse("curl -s"))
                .hasMessageContaining("No URL");
        assertThatThrownBy(() -> CurlCodec.parse("wget https://h"))
                .hasMessageContaining("curl");
    }

    @Test
    @DisplayName("Unknown flags are noted, never allowed to eat the URL")
    void unknownFlagKeepsUrl() {
        var got = CurlCodec.parse("curl --funky https://h/x -sL --compressed");
        assertThat(got.request().url).isEqualTo("https://h/x");
        assertThat(got.notes()).anySatisfy(n -> assertThat(n).contains("--funky"));
    }

    @Test
    @DisplayName("Double quotes honor backslash escapes")
    void doubleQuoteEscapes() {
        var r = CurlCodec.parse("curl -H \"X-Q: say \\\"hi\\\"\" https://h").request();
        assertThat(r.headers.get(0).value).isEqualTo("say \"hi\"");
    }

    @Test
    @DisplayName("Render emits the exact command a terminal would run")
    void renderExact() {
        ApiModel.Request r = new ApiModel.Request();
        r.method = "POST";
        r.url = "{{base}}/users";
        r.headers.add(new Pair("Content-Type", "application/json"));
        Pair off = new Pair("X-Off", "no");
        off.enabled = false;
        r.headers.add(off);
        r.authType = AuthType.BEARER;
        r.authToken = "tok";
        r.body = "{\"it's\":1}";
        String curl = CurlCodec.render(r, Map.of("base", "http://localhost:3000"));
        assertThat(curl).isEqualTo("""
                curl -X POST 'http://localhost:3000/users' \\
                  -H 'Content-Type: application/json' \\
                  -H 'Authorization: Bearer tok' \\
                  --data '{"it'\\''s":1}'""");
    }

    @Test
    @DisplayName("Render: plain GET has no -X, basic auth rides -u")
    void renderGetBasic() {
        ApiModel.Request r = new ApiModel.Request();
        r.url = "https://h/x";
        r.authType = AuthType.BASIC;
        r.authToken = "u:p";
        assertThat(CurlCodec.render(r, Map.of()))
                .isEqualTo("curl 'https://h/x' \\\n  -u 'u:p'");
    }

    @Test
    @DisplayName("Enabled params are appended the way send() appends them")
    void renderParams() {
        ApiModel.Request r = new ApiModel.Request();
        r.url = "https://h/x";
        r.params.add(new Pair("q", "a b"));
        assertThat(CurlCodec.render(r, Map.of()))
                .isEqualTo("curl 'https://h/x?q=a+b'");
    }

    @Test
    @DisplayName("parse(render(r)) preserves method, url, auth, body and headers")
    void roundTrip() {
        ApiModel.Request r = new ApiModel.Request();
        r.method = "PATCH";
        r.url = "https://api.example.com/v1/items/7";
        r.headers.add(new Pair("X-Trace", "on"));
        r.authType = AuthType.BEARER;
        r.authToken = "secret";
        r.body = "{\"done\":true}";
        var back = CurlCodec.parse(CurlCodec.render(r, Map.of())).request();
        assertThat(back.method).isEqualTo("PATCH");
        assertThat(back.url).isEqualTo(r.url);
        assertThat(back.authType).isEqualTo(AuthType.BEARER);
        assertThat(back.authToken).isEqualTo("secret");
        assertThat(back.body).isEqualTo(r.body);
        assertThat(back.headers).hasSize(1);
        assertThat(back.headers.get(0).name).isEqualTo("X-Trace");
    }

    @Test
    @DisplayName("Tokenizer: continuations, mixed quoting, empty input")
    void tokenizer() {
        assertThat(CurlCodec.tokenize("a \\\n b")).containsExactly("a", "b");
        assertThat(CurlCodec.tokenize("a'b c'd")).containsExactly("ab cd");
        assertThat(CurlCodec.tokenize("")).isEmpty();
        assertThatThrownBy(() -> CurlCodec.tokenize("'open"))
                .hasMessageContaining("Unclosed");
    }
}
