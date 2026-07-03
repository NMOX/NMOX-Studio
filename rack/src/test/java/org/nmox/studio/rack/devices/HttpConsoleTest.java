package org.nmox.studio.rack.devices;

import java.net.http.HttpRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REST-console upgrade: header parsing, JSON pretty-printing, and
 * the precedence rule that a dialed Content-Type wins over the sniff.
 */
class HttpConsoleTest {

    @Test
    @DisplayName("Headers parse from 'Name: value; Name: value', malformed skipped")
    void headersParse() {
        Map<String, String> h = HttpDevice.parseHeaders(
                "Authorization: Bearer abc123; Accept: application/json; junk; X-Empty:");
        assertThat(h).containsEntry("Authorization", "Bearer abc123")
                .containsEntry("Accept", "application/json")
                .hasSize(2);
        assertThat(HttpDevice.parseHeaders("")).isEmpty();
        assertThat(HttpDevice.parseHeaders(null)).isEmpty();
    }

    @Test
    @DisplayName("A dialed header is applied; an explicit Content-Type beats the JSON sniff")
    void dialedHeadersWin() {
        HttpRequest r = HttpDevice.buildRequest("POST", "http://x/api", "{\"a\":1}",
                Map.of("Authorization", "Bearer t", "Content-Type", "application/vnd.api+json"));
        assertThat(r.headers().firstValue("Authorization")).hasValue("Bearer t");
        assertThat(r.headers().firstValue("Content-Type")).hasValue("application/vnd.api+json");
    }

    @Test
    @DisplayName("JSON bodies pretty-print; non-JSON passes through unchanged")
    void prettyPrint() {
        assertThat(HttpDevice.prettyJson("{\"a\":1,\"b\":2}")).contains("\n").contains("\"a\": 1");
        assertThat(HttpDevice.prettyJson("[1,2,3]")).contains("\n");
        assertThat(HttpDevice.prettyJson("plain text")).isEqualTo("plain text");
        assertThat(HttpDevice.prettyJson("{not json")).isEqualTo("{not json");
        assertThat(HttpDevice.prettyJson(null)).isEmpty();
    }
}
