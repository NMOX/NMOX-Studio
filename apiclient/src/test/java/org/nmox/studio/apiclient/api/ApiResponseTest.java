package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response value object: the reached/ok status predicates and the
 * case-insensitive header lookup the test runner and viewer lean on.
 */
class ApiResponseTest {

    private static ApiResponse withStatus(int status) {
        return new ApiResponse(status, 10, 0, Map.of(), "", null);
    }

    @Test
    @DisplayName("reached() is true for any real status and false for a failure")
    void reached() {
        assertThat(withStatus(200).reached()).isTrue();
        assertThat(withStatus(500).reached()).isTrue();
        assertThat(withStatus(0).reached()).isTrue();
        assertThat(ApiResponse.failure(9000, "refused").reached()).isFalse();
    }

    @Test
    @DisplayName("ok() spans 2xx and 3xx, and excludes 4xx, 5xx and failures")
    void ok() {
        assertThat(withStatus(200).ok()).isTrue();
        assertThat(withStatus(204).ok()).isTrue();
        assertThat(withStatus(301).ok()).isTrue();
        assertThat(withStatus(399).ok()).isTrue();
        assertThat(withStatus(400).ok()).isFalse();
        assertThat(withStatus(404).ok()).isFalse();
        assertThat(withStatus(500).ok()).isFalse();
        assertThat(withStatus(199).ok()).isFalse();
        assertThat(ApiResponse.failure(1, "x").ok()).isFalse();
    }

    @Test
    @DisplayName("hasHeader matches case-insensitively and is false when absent")
    void hasHeader() {
        ApiResponse r = new ApiResponse(200, 10, 0,
                Map.of("Content-Type", List.of("application/json"),
                        "X-Trace-Id", List.of("abc")),
                "", null);
        assertThat(r.hasHeader("content-type")).isTrue();
        assertThat(r.hasHeader("CONTENT-TYPE")).isTrue();
        assertThat(r.hasHeader("x-trace-id")).isTrue();
        assertThat(r.hasHeader("X-Missing")).isFalse();
    }

    @Test
    @DisplayName("A failure carries status -1, an empty header map, and the error text")
    void failureShape() {
        ApiResponse r = ApiResponse.failure(1234, "Connection refused");
        assertThat(r.status()).isEqualTo(-1);
        assertThat(r.millis()).isEqualTo(1234);
        assertThat(r.bytes()).isZero();
        assertThat(r.body()).isEmpty();
        assertThat(r.error()).isEqualTo("Connection refused");
        assertThat(r.hasHeader("anything")).as("empty header map matches nothing").isFalse();
    }
}
