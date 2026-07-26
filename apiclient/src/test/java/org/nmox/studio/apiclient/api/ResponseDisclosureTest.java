package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disclosure is a promise: whatever these tests allow through is
 * exactly what leaves the machine when a user presses Explain.
 */
class ResponseDisclosureTest {

    private static ApiResponse response(int status, Map<String, List<String>> headers, String body) {
        return new ApiResponse(status, 12L, body.length(), headers, body, null, false);
    }

    @Test
    @DisplayName("Credential headers never appear in the disclosure")
    void credentialHeadersWithheld() {
        var r = response(200, Map.of(
                "Authorization", List.of("Bearer super-secret-token"),
                "Set-Cookie", List.of("session=abc123"),
                "X-Api-Key", List.of("k-9999"),
                "Content-Type", List.of("application/json")), "{}");
        String text = ResponseDisclosure.body("GET", "https://h/x", r);
        assertThat(text)
                .doesNotContain("super-secret-token")
                .doesNotContain("abc123")
                .doesNotContain("k-9999")
                .contains("Content-Type: application/json")
                .contains("credential headers withheld");
    }

    @Test
    @DisplayName("Query values are masked, parameter names survive")
    void queryValuesMasked() {
        String masked = ResponseDisclosure.maskQuery(
                "https://api.example.com/v1/orders?api_key=SECRET123&limit=10");
        assertThat(masked)
                .doesNotContain("SECRET123")
                .doesNotContain("10")
                .contains("api_key=…")
                .contains("limit=…")
                .contains("/v1/orders");
    }

    @Test
    @DisplayName("A URL with no query passes through untouched")
    void noQueryUnchanged() {
        assertThat(ResponseDisclosure.maskQuery("https://h/health"))
                .isEqualTo("https://h/health");
    }

    @Test
    @DisplayName("The body cap is code-point-safe and marked when truncated")
    void bodyCapIsCodePointSafe() {
        String emoji = "🚀".repeat(4_000); // 2 chars each: the cap lands mid-pair
        var r = response(500, Map.of(), emoji);
        String text = ResponseDisclosure.body("POST", "https://h/x", r);
        assertThat(text).contains("[body truncated]");
        // no lone surrogate survived the cap (v1.149.0's lesson)
        String capped = ResponseDisclosure.cap(emoji, ResponseDisclosure.MAX_BODY_CHARS);
        assertThat(Character.isHighSurrogate(capped.charAt(capped.length() - 1))).isFalse();
    }

    @Test
    @DisplayName("A no-route response is disclosed as the error, not a fake status")
    void noRouteIsHonest() {
        var r = ApiResponse.failure(5L, "connection refused");
        String text = ResponseDisclosure.body("GET", "https://h/x", r);
        assertThat(text).contains("no route").contains("connection refused")
                .doesNotContain("Status:");
    }

    @Test
    @DisplayName("The consent line matches what the body actually carries")
    void consentLineIsTrue() {
        String what = ResponseDisclosure.what(null);
        assertThat(what)
                .contains("method and URL")
                .contains("query values masked")
                .contains("credentials removed")
                .contains(String.valueOf(ResponseDisclosure.MAX_BODY_CHARS));
    }
}
