package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The export half of the .http dialect. The standing law is the round
 * trip — {@code parse(render(c))} preserves every request's effective
 * shape — with ONE written exception: auth is deliberately lossy,
 * because the secret lives in the OS keychain and a shareable text
 * file is exactly where it must never land.
 */
class HttpFileRenderTest {

    private static ApiModel.Collection collection() {
        ApiModel.Collection c = new ApiModel.Collection();
        c.name = "Orders";

        ApiModel.Request search = new ApiModel.Request();
        search.name = "Search";
        search.method = "GET";
        search.url = "{{baseUrl}}/orders";
        search.params.add(new Pair("status", "open"));
        Pair off = new Pair("debug", "1");
        off.enabled = false;
        search.params.add(off);
        search.headers.add(new Pair("Accept", "application/json"));
        c.requests.add(search);

        ApiModel.Request create = new ApiModel.Request();
        create.name = "Create";
        create.method = "POST";
        create.url = "{{baseUrl}}/orders";
        create.headers.add(new Pair("Content-Type", "application/json"));
        create.body = "{\n  \"item\": \"widget\"\n}";
        create.authType = AuthType.BEARER;
        create.authToken = "sk-live-SECRET";
        c.requests.add(create);
        return c;
    }

    @Test
    @DisplayName("The round trip preserves method, URL+params, headers, and body")
    void roundTrip() {
        String text = HttpFileCodec.render(collection());
        HttpFileCodec.Imported back = HttpFileCodec.parse(text);

        assertThat(back.requests()).hasSize(2);
        var search = back.requests().get(0);
        assertThat(search.name).isEqualTo("Search");
        assertThat(search.method).isEqualTo("GET");
        // enabled params rejoined the URL; the disabled one is gone
        assertThat(search.url).isEqualTo("{{baseUrl}}/orders?status=open");
        assertThat(search.headers).anyMatch(h -> "Accept".equals(h.name));

        var create = back.requests().get(1);
        assertThat(create.method).isEqualTo("POST");
        assertThat(create.body).contains("\"item\": \"widget\"");
    }

    @Test
    @DisplayName("The secret NEVER lands in the rendered text — the written exception")
    void authIsDeliberatelyLossy() {
        String text = HttpFileCodec.render(collection());
        assertThat(text).doesNotContain("sk-live-SECRET");
        // and the file says so where the auth would have been
        assertThat(text).contains("Auth not exported (OS keychain)");
        // the re-import therefore carries no auth — lossy BY DESIGN
        HttpFileCodec.Imported back = HttpFileCodec.parse(text);
        assertThat(back.requests().get(1).authType).isEqualTo(AuthType.NONE);
    }

    @Test
    @DisplayName("{{variables}} render verbatim — both dialects share the syntax")
    void variablesVerbatim() {
        String text = HttpFileCodec.render(collection());
        assertThat(text).contains("GET {{baseUrl}}/orders?status=open");
    }

    /**
     * v1.181.0 review find, proven failing-first on the shipped v1.179.0:
     * a body line starting with {@code ###} rendered into a file that
     * re-imported as TWO requests with the body destroyed. The dialect
     * has no escape, so the fix is the auth idiom — omit and say so.
     */
    @Test
    @DisplayName("A ###-carrying body cannot split the file — omitted and said")
    void hashBodySurvivesAsOneRequest() {
        ApiModel.Collection c = new ApiModel.Collection();
        ApiModel.Request r = new ApiModel.Request();
        r.name = "Markdown\npayload"; // and a hostile multi-line name
        r.method = "POST";
        r.url = "https://x.example.com/md";
        r.body = "line one\n### a heading inside the body\nline three";
        c.requests.add(r);

        String text = HttpFileCodec.render(c);
        HttpFileCodec.Imported back = HttpFileCodec.parse(text);

        assertThat(back.requests()).as("one request in, one request out").hasSize(1);
        assertThat(back.requests().get(0).name).isEqualTo("Markdown payload");
        assertThat(back.requests().get(0).body).isEmpty();
        assertThat(text).contains("Body not exported");
    }

    @Test
    @DisplayName("A URL that already has a query gets & not a second ?")
    void queryAppendJoins() {
        ApiModel.Collection c = new ApiModel.Collection();
        ApiModel.Request r = new ApiModel.Request();
        r.name = "Q";
        r.url = "https://x.example.com/s?fixed=1";
        r.params.add(new Pair("extra", "2"));
        c.requests.add(r);
        assertThat(HttpFileCodec.render(c))
                .contains("GET https://x.example.com/s?fixed=1&extra=2");
    }
}
