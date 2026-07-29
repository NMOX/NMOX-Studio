package org.nmox.studio.apiclient.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The curl importer's long tail: the flag families the main suite's
 * fixtures didn't reach (-I, -b, -A/-e, --oauth2-bearer,
 * --data-urlencode, ignored transport flags), the honest refusals
 * (-T, cookie files, malformed headers, a flag with no value), and the
 * tokenizer's shell grammar corners — escapes, continuations, and the
 * unclosed-quote refusals.
 */
class CurlCodecEdgesTest {

    // ---- flags ----

    @Test
    @DisplayName("-I means HEAD, explicitly")
    void headFlag() {
        assertThat(CurlCodec.parse("curl -I https://a.example.com").request().method)
                .isEqualTo("HEAD");
    }

    @Test
    @DisplayName("--oauth2-bearer lands in the keychain-backed Auth field")
    void oauth2Bearer() {
        ApiModel.Request r = CurlCodec.parse(
                "curl --oauth2-bearer tok-123 https://a.example.com").request();
        assertThat(r.authType).isEqualTo(AuthType.BEARER);
        assertThat(r.authToken).isEqualTo("tok-123");
    }

    @Test
    @DisplayName("-b name=value becomes a Cookie header; a cookie FILE is refused")
    void cookieFlag() {
        ApiModel.Request r = CurlCodec.parse(
                "curl -b 'session=abc' https://a.example.com").request();
        assertThat(r.headers).anySatisfy(h -> {
            assertThat(h.name).isEqualTo("Cookie");
            assertThat(h.value).isEqualTo("session=abc");
        });

        assertThatThrownBy(() -> CurlCodec.parse("curl -b cookies.txt https://a.example.com"))
                .hasMessageContaining("Cookie files");
    }

    @Test
    @DisplayName("-A and -e become their header rows")
    void agentAndReferer() {
        ApiModel.Request r = CurlCodec.parse(
                "curl -A 'nmox/1.0' -e 'https://from.example.com' https://a.example.com")
                .request();
        assertThat(r.headers).extracting(h -> h.name)
                .contains("User-Agent", "Referer");
    }

    @Test
    @DisplayName("--data-urlencode imports verbatim, with the honest note")
    void dataUrlencode() {
        CurlCodec.Imported imported = CurlCodec.parse(
                "curl --data-urlencode 'q=a b' https://a.example.com");
        assertThat(imported.request().body).isEqualTo("q=a b");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("--data-urlencode imported verbatim"));
    }

    @Test
    @DisplayName("--url sets the URL; a second bare argument is noted, not swallowed")
    void urlFlagAndExtraArgument() {
        CurlCodec.Imported imported = CurlCodec.parse(
                "curl --url https://a.example.com https://b.example.com");
        assertThat(imported.request().url).isEqualTo("https://a.example.com");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("URL already set"));
    }

    @Test
    @DisplayName("value-carrying transport flags are consumed and reported")
    void ignoredWithValue() {
        CurlCodec.Imported imported = CurlCodec.parse(
                "curl -o out.html https://a.example.com");
        assertThat(imported.request().url).isEqualTo("https://a.example.com");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("Ignored -o"));
    }

    @Test
    @DisplayName("-G appends the data to an URL that already has a query")
    void getWithExistingQuery() {
        ApiModel.Request r = CurlCodec.parse(
                "curl -G -d b=2 'https://a.example.com/q?a=1'").request();
        assertThat(r.url).isEqualTo("https://a.example.com/q?a=1&b=2");
        assertThat(r.body).isEmpty();
    }

    @Test
    @DisplayName("--json respects an explicit Content-Type but still adds Accept")
    void jsonKeepsExplicitContentType() {
        ApiModel.Request r = CurlCodec.parse(
                "curl -H 'Content-Type: application/vnd.api+json' --json '{}' "
                + "https://a.example.com").request();
        assertThat(r.headers).filteredOn(h -> "Content-Type".equalsIgnoreCase(h.name))
                .hasSize(1)
                .allSatisfy(h -> assertThat(h.value).isEqualTo("application/vnd.api+json"));
        assertThat(r.headers).anySatisfy(h -> assertThat(h.name).isEqualTo("Accept"));
    }

    @Test
    @DisplayName("a 48+-char host/path is ellipsized in the request name")
    void longNameEllipsized() {
        ApiModel.Request r = CurlCodec.parse("curl https://api.example.com/a/very/long"
                + "/path/that/keeps/going/and/going/and/going").request();
        assertThat(r.name).endsWith("…");
        assertThat(r.name.length()).isEqualTo(49);
    }

    // ---- refusals ----

    @Test
    @DisplayName("-T uploads, malformed headers, and a dangling flag are refused by name")
    void refusals() {
        assertThatThrownBy(() -> CurlCodec.parse("curl -T file.bin https://a.example.com"))
                .hasMessageContaining("File uploads (-T)");
        assertThatThrownBy(() -> CurlCodec.parse("curl -H 'NoColonHere' https://a.example.com"))
                .hasMessageContaining("Malformed header");
        assertThatThrownBy(() -> CurlCodec.parse("curl https://a.example.com -X"))
                .hasMessageContaining("Flag -X needs a value.");
    }

    @Test
    @DisplayName("a non-base64 Basic Authorization stays a header, with the keychain hint")
    void nonBase64BasicKeptAsHeader() {
        CurlCodec.Imported imported = CurlCodec.parse(
                "curl -H 'Authorization: Basic %%%notbase64' https://a.example.com");
        assertThat(imported.request().authType).isEqualTo(AuthType.NONE);
        assertThat(imported.request().headers)
                .anySatisfy(h -> assertThat(h.name).isEqualTo("Authorization"));
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("Authorization header kept as a header"));
    }

    // ---- tokenizer grammar ----

    @Test
    @DisplayName("backslash escapes, in and out of double quotes, read like a shell")
    void backslashEscapes() {
        assertThat(CurlCodec.tokenize("a\\ b")).containsExactly("a b");
        assertThat(CurlCodec.tokenize("\"say \\\"hi\\\"\"")).containsExactly("say \"hi\"");
        assertThat(CurlCodec.tokenize("\"one \\\ntwo\"")).containsExactly("one two");
        assertThat(CurlCodec.tokenize("end\\")).containsExactly("end");
    }

    @Test
    @DisplayName("unclosed quotes are refused, not silently swallowed")
    void unclosedQuotesRefused() {
        assertThatThrownBy(() -> CurlCodec.tokenize("curl 'oops"))
                .hasMessageContaining("Unclosed single quote");
        assertThatThrownBy(() -> CurlCodec.tokenize("curl \"oops"))
                .hasMessageContaining("Unclosed double quote");
    }

    // ---- render edges ----

    @Test
    @DisplayName("render survives a header row with a null value")
    void renderNullHeaderValue() {
        ApiModel.Request r = new ApiModel.Request();
        r.method = "GET";
        r.url = "https://a.example.com";
        r.headers.add(new Pair("X-Empty", null));

        assertThat(CurlCodec.render(r, Map.of())).contains("-H 'X-Empty: '");
    }
}
