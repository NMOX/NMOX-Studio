package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A HAR is a recording of a REAL session, so the promises under test
 * are mostly about what must NOT survive the import: session cookies,
 * page assets, stale hop-by-hop headers, and silently truncated bodies.
 */
class HarCodecTest {

    private static String har(String entries) {
        return """
               {"log":{"version":"1.2","creator":{"name":"t"},"entries":[%s]}}"""
                .formatted(entries);
    }

    private static String entry(String type, String method, String url, String extra) {
        return """
               {%s"request":{"method":"%s","url":"%s","httpVersion":"HTTP/2",
                "headers":[%s],"queryString":[],"cookies":[]},
                "response":{"status":200}}"""
                .formatted(type.isEmpty() ? "" : "\"_resourceType\":\"" + type + "\",",
                        method, url, extra);
    }

    @Test
    @DisplayName("Cookie headers are dropped and counted — never imported")
    void cookiesNeverSurvive() {
        var got = HarCodec.parse(har(entry("", "GET", "https://api.example.com/me",
                """
                {"name":"Cookie","value":"session=SECRET123"},
                {"name":"Accept","value":"application/json"}""")));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.headers).noneMatch(h -> "Cookie".equalsIgnoreCase(h.name));
        assertThat(r.headers).anyMatch(h -> "Accept".equals(h.name));
        assertThat(got.notes()).anyMatch(n -> n.contains("Cookie header dropped")
                || n.contains("Cookie headers dropped")
                || n.contains("1 Cookie header"));
    }

    @Test
    @DisplayName("A captured Bearer token lands in the Auth field, not a header row")
    void bearerLifted() {
        var got = HarCodec.parse(har(entry("", "GET", "https://api.example.com/x",
                """
                {"name":"Authorization","value":"Bearer live-token-9"}""")));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.authType).isEqualTo(AuthType.BEARER);
        assertThat(r.authToken).isEqualTo("live-token-9");
        assertThat(r.headers).noneMatch(h -> "Authorization".equalsIgnoreCase(h.name));
    }

    /**
     * v1.181.0 review find: a HAR is a RECORDING, so a non-liftable
     * Authorization value ("Token …", SigV4) is a live credential and
     * follows the Cookie rule — dropped and counted, never a plaintext
     * row. The curl import deliberately differs (the user typed it).
     */
    @Test
    @DisplayName("A captured non-Bearer/Basic Authorization is dropped, not kept")
    void opaqueAuthorizationNeverSurvives() {
        var got = HarCodec.parse(har(entry("", "GET", "https://api.example.com/x",
                """
                {"name":"Authorization","value":"Token opaque-live-secret"},
                {"name":"Accept","value":"application/json"}""")));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.headers).noneMatch(h -> "Authorization".equalsIgnoreCase(h.name));
        assertThat(r.authType).isEqualTo(AuthType.NONE);
        assertThat(got.notes()).anyMatch(n -> n.contains("DROPPED"));
        // and the secret string appears NOWHERE in the imported model
        assertThat(r.headers).noneMatch(h -> h.value.contains("opaque-live-secret"));
    }

    @Test
    @DisplayName("When the capture is typed, only XHR/fetch import — assets are counted")
    void typedCaptureKeepsApiTraffic() {
        var got = HarCodec.parse(har(String.join(",",
                entry("document", "GET", "https://app.example.com/", ""),
                entry("stylesheet", "GET", "https://app.example.com/app.css", ""),
                entry("xhr", "GET", "https://app.example.com/api/users", ""),
                entry("fetch", "POST", "https://app.example.com/api/orders", ""))));
        assertThat(got.requests()).extracting(r -> r.url).containsExactly(
                "https://app.example.com/api/users",
                "https://app.example.com/api/orders");
        assertThat(got.notes()).anyMatch(n -> n.contains("2 page-asset"));
    }

    @Test
    @DisplayName("Repeats collapse; data:/ws: entries and pseudo-headers are skipped")
    void noiseIsNamedNotImported() {
        var got = HarCodec.parse(har(String.join(",",
                entry("", "GET", "https://x.example.com/poll",
                        """
                        {"name":":authority","value":"x.example.com"},
                        {"name":"Host","value":"stale.example.com"}"""),
                entry("", "GET", "https://x.example.com/poll", ""),
                entry("", "GET", "data:image/png;base64,AAA", ""),
                entry("", "GET", "wss://x.example.com/live", ""))));
        assertThat(got.requests()).hasSize(1);
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.headers).noneMatch(h -> h.name.startsWith(":"));
        assertThat(r.headers).noneMatch(h -> "Host".equalsIgnoreCase(h.name));
        assertThat(got.notes()).anyMatch(n -> n.contains("repeated"));
        assertThat(got.notes()).anyMatch(n -> n.contains("non-HTTP"));
    }

    @Test
    @DisplayName("The query string rides the params grid; the body keeps its mime")
    void queryAndBody() {
        String e = """
                   {"request":{"method":"POST",
                    "url":"https://api.example.com/search?q=cats&page=2",
                    "headers":[],"queryString":[
                      {"name":"q","value":"cats"},{"name":"page","value":"2"}],
                    "postData":{"mimeType":"application/json",
                                "text":"{\\"filter\\": true}"}},
                    "response":{"status":200}}""";
        var got = HarCodec.parse(har(e));
        ApiModel.Request r = got.requests().get(0);
        assertThat(r.url).isEqualTo("https://api.example.com/search");
        assertThat(r.params).hasSize(2);
        assertThat(r.body).contains("filter");
        assertThat(r.headers).anyMatch(h -> "Content-Type".equalsIgnoreCase(h.name)
                && "application/json".equals(h.value));
    }

    @Test
    @DisplayName("An over-cap body is refused by name — never silently truncated")
    void hugeBodyRefused() {
        String e = """
                   {"request":{"method":"POST","url":"https://x/upload",
                    "headers":[],"queryString":[],
                    "postData":{"mimeType":"text/plain","text":"%s"}},
                    "response":{}}""".formatted("x".repeat(HarCodec.MAX_BODY_CHARS + 1));
        var got = HarCodec.parse(har(e));
        assertThat(got.requests().get(0).body).isEmpty();
        assertThat(got.notes()).anyMatch(n -> n.contains("not imported"));
    }

    @Test
    @DisplayName("Multipart bodies keep the curl import's stance: refused by name")
    void multipartRefused() {
        String e = """
                   {"request":{"method":"POST","url":"https://x/upload",
                    "headers":[],"queryString":[],
                    "postData":{"mimeType":"multipart/form-data; boundary=b",
                                "text":"--b\\r\\n..."}},
                    "response":{}}""";
        var got = HarCodec.parse(har(e));
        assertThat(got.requests().get(0).body).isEmpty();
        assertThat(got.notes()).anyMatch(n -> n.contains("multipart"));
    }

    @Test
    @DisplayName("Not-a-HAR and all-assets captures are refused with the reason")
    void honestRefusals() {
        assertThatThrownBy(() -> HarCodec.parse("{\"nope\": true}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("log.entries");
        assertThatThrownBy(() -> HarCodec.parse(
                har(entry("image", "GET", "https://x/a.png", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page assets");
        assertThatThrownBy(() -> HarCodec.parse("no json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
