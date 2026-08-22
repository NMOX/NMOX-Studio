package org.nmox.studio.apiclient.api;

import java.util.Map;

import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copy as fetch (v2.31.0): the snippet carries exactly what Send would
 * use — URL with params, enabled headers, auth, body — vars resolved,
 * JS string escaping complete.
 */
class FetchCodecTest {

    private static ApiModel.Request req(String method, String url) {
        ApiModel.Request r = new ApiModel.Request();
        r.method = method;
        r.url = url;
        return r;
    }

    @Test
    @DisplayName("a plain GET is one line")
    void plainGet() {
        assertThat(FetchCodec.render(req("GET", "https://api.example.com/users"), Map.of()))
                .isEqualTo("const response = await fetch(\"https://api.example.com/users\");\n");
    }

    @Test
    @DisplayName("method, headers, bearer auth, and body all land; vars resolve")
    void fullPost() {
        ApiModel.Request r = req("POST", "{{base}}/users");
        ApiModel.Pair h = new ApiModel.Pair();
        h.name = "Content-Type";
        h.value = "application/json";
        h.enabled = true;
        r.headers.add(h);
        ApiModel.Pair off = new ApiModel.Pair();
        off.name = "X-Disabled";
        off.value = "never";
        off.enabled = false;
        r.headers.add(off);
        r.authType = AuthType.BEARER;
        r.authToken = "tok{{n}}";
        r.body = "{\"name\":\"Ada\"}";
        String code = FetchCodec.render(r, Map.of("base", "https://api.x.io", "n", "42"));
        assertThat(code)
                .contains("await fetch(\"https://api.x.io/users\", {")
                .contains("method: \"POST\"")
                .contains("\"Content-Type\": \"application/json\"")
                .contains("\"Authorization\": \"Bearer tok42\"")
                .contains("body: \"{\\\"name\\\":\\\"Ada\\\"}\"")
                .doesNotContain("X-Disabled");
    }

    @Test
    @DisplayName("basic auth renders through btoa, never pre-encoded")
    void basicAuth() {
        ApiModel.Request r = req("GET", "https://x.io");
        r.authType = AuthType.BASIC;
        r.authToken = "user:pass";
        assertThat(FetchCodec.render(r, Map.of()))
                .contains("\"Basic \" + btoa(\"user:pass\")");
    }

    @Test
    @DisplayName("js() escapes quotes, backslashes, newlines, and control chars")
    void jsEscaping() {
        assertThat(FetchCodec.js("a\"b\\c\nd\te\u0007"))
                .isEqualTo("\"a\\\"b\\\\c\\nd\\te\\u0007\"");
    }
}
