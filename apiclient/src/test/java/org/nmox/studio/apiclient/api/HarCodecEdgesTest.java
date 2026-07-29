package org.nmox.studio.apiclient.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HAR importer's edges beyond the happy captures: entry shapes a
 * real browser export can carry (request-less pages, scheme-less URLs,
 * HTTP/2 pseudo-headers, recomputed headers), the 200-request cap with
 * its honest overflow note, Basic-auth lifting with its not-base64
 * fallback, and the Content-Type-already-present body case.
 */
class HarCodecEdgesTest {

    private static String har(String entriesJson) {
        return "{\"log\": {\"entries\": " + entriesJson + "}}";
    }

    @Test
    @DisplayName("request-less and scheme-less entries are skipped; an empty untyped capture is refused")
    void skipsMalformedEntries() {
        assertThatThrownBy(() -> HarCodec.parse(har("""
                ["stray",
                 {"noRequest": true},
                 {"request": {"method": "GET", "url": "no-scheme-here"}},
                 {"request": {"method": "GET", "url": "ws://live.example.com/feed"}}]""")))
                .hasMessageContaining("No importable requests found");
    }

    @Test
    @DisplayName("a capture past the 200-request cap keeps the first 200 and says so")
    void capsWithHonestNote() {
        JSONArray entries = new JSONArray();
        for (int i = 0; i < HarCodec.MAX_REQUESTS + 3; i++) {
            entries.put(new JSONObject().put("request", new JSONObject()
                    .put("method", "GET")
                    .put("url", "https://api.example.com/item/" + i)));
        }
        HarCodec.Imported imported = HarCodec.parse(har(entries.toString()));

        assertThat(imported.requests()).hasSize(HarCodec.MAX_REQUESTS);
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("3 entries beyond the first " + HarCodec.MAX_REQUESTS));
    }

    @Test
    @DisplayName("a 48+-char method+URL name gains an ellipsis")
    void longNamesEllipsized() {
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "GET",
                   "url": "https://api.example.com/a/very/long/path/that/keeps/going/on"}}]"""));
        assertThat(imported.requests().get(0).name).endsWith("…");
    }

    @Test
    @DisplayName("pseudo-headers, recomputed headers, and one Cookie are dropped — the cookie counted")
    void headerRules() {
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "GET", "url": "https://api.example.com/me",
                   "headers": ["stray",
                     {"name": ":authority", "value": "api.example.com"},
                     {"name": "", "value": "blank"},
                     {"name": "Host", "value": "api.example.com"},
                     {"name": "Accept-Encoding", "value": "gzip"},
                     {"name": "Cookie", "value": "session=abc"},
                     {"name": "X-Kept", "value": "yes"}]}}]"""));

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.headers).extracting(h -> h.name).containsExactly("X-Kept");
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("1 Cookie header dropped"));
    }

    @Test
    @DisplayName("a headerless request contributes zero dropped cookies")
    void headerlessRequest() {
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "GET", "url": "https://api.example.com/bare"}}]"""));
        assertThat(imported.requests()).hasSize(1);
        assertThat(imported.notes()).noneMatch(n -> n.contains("Cookie"));
    }

    @Test
    @DisplayName("a captured Basic Authorization lifts into the keychain-backed Auth field")
    void basicAuthLifted() {
        String creds = java.util.Base64.getEncoder()
                .encodeToString("ada:pw".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "GET", "url": "https://api.example.com/me",
                   "headers": [{"name": "Authorization", "value": "Basic %s"}]}}]"""
                .formatted(creds)));

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.authType).isEqualTo(AuthType.BASIC);
        assertThat(r.authToken).isEqualTo("ada:pw");
        assertThat(r.headers).noneMatch(h -> "Authorization".equalsIgnoreCase(h.name));
    }

    @Test
    @DisplayName("a non-base64 'Basic' value follows the recording rule: dropped and noted")
    void nonBase64BasicDropped() {
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "GET", "url": "https://api.example.com/me",
                   "headers": [{"name": "Authorization", "value": "Basic %%%nope"}]}}]"""));

        ApiModel.Request r = imported.requests().get(0);
        assertThat(r.authType).isEqualTo(AuthType.NONE);
        assertThat(r.headers).noneMatch(h -> "Authorization".equalsIgnoreCase(h.name));
        assertThat(imported.notes()).anySatisfy(n ->
                assertThat(n).contains("DROPPED"));
    }

    @Test
    @DisplayName("a body's mimeType never duplicates an explicitly captured Content-Type")
    void contentTypeNotDuplicated() {
        HarCodec.Imported imported = HarCodec.parse(har("""
                [{"request": {"method": "POST", "url": "https://api.example.com/new",
                   "headers": [{"name": "Content-Type", "value": "application/json"}],
                   "postData": {"mimeType": "application/json", "text": "{}"}}}]"""));

        assertThat(imported.requests().get(0).headers)
                .filteredOn(h -> "Content-Type".equalsIgnoreCase(h.name))
                .hasSize(1);
    }
}
