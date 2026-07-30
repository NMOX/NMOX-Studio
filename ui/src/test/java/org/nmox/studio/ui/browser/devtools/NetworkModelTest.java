package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Network ring's laws: bridge JSON parsed defensively (garbage is
 * a no-row), URL capped at 500 chars, ring capped at 500 rows with
 * dropped counting.
 */
class NetworkModelTest {

    @Test
    @DisplayName("parses a well-formed bridge payload")
    void parsesPayload() {
        NetworkModel.Entry e = NetworkModel.fromJson(
                "{\"m\":\"POST\",\"u\":\"https://api.example.com/v1\",\"s\":201,"
                + "\"ok\":true,\"d\":142,\"z\":5120}");
        assertThat(e).isNotNull();
        assertThat(e.method()).isEqualTo("POST");
        assertThat(e.url()).isEqualTo("https://api.example.com/v1");
        assertThat(e.status()).isEqualTo(201);
        assertThat(e.ok()).isTrue();
        assertThat(e.durationMillis()).isEqualTo(142);
        assertThat(e.sizeBytes()).isEqualTo(5120);
    }

    @Test
    @DisplayName("missing fields default; unknown size is -1")
    void missingFieldsDefault() {
        NetworkModel.Entry e = NetworkModel.fromJson("{\"u\":\"/x\"}");
        assertThat(e).isNotNull();
        assertThat(e.method()).isEqualTo("GET");
        assertThat(e.status()).isZero();
        assertThat(e.ok()).isFalse();
        assertThat(e.sizeBytes()).isEqualTo(-1);
    }

    @Test
    @DisplayName("garbage JSON is a no-row, never a throw")
    void garbageIsNoRow() {
        assertThat(NetworkModel.fromJson(null)).isNull();
        assertThat(NetworkModel.fromJson("")).isNull();
        assertThat(NetworkModel.fromJson("not json")).isNull();
        assertThat(NetworkModel.fromJson("[1,2,3]")).isNull();
        NetworkModel m = new NetworkModel();
        m.addFromJson("garbage");
        assertThat(m.entries()).isEmpty();
    }

    @Test
    @DisplayName("hostile URL and method are capped")
    void hostileFieldsCapped() {
        NetworkModel.Entry e = NetworkModel.fromJson(
                "{\"m\":\"" + "M".repeat(100) + "\",\"u\":\"" + "u".repeat(5000) + "\"}");
        assertThat(e.url()).hasSize(NetworkModel.URL_CAP);
        assertThat(e.method()).hasSize(20);
    }

    @Test
    @DisplayName("holds exactly CAP rows; the oldest drop and are counted")
    void capIsExact() {
        NetworkModel m = new NetworkModel();
        for (int i = 0; i < NetworkModel.CAP + 3; i++) {
            m.addFromJson("{\"m\":\"GET\",\"u\":\"/r" + i + "\",\"s\":200,\"ok\":true,\"d\":1,\"z\":-1}");
        }
        assertThat(m.entries()).hasSize(NetworkModel.CAP);
        assertThat(m.droppedCount()).isEqualTo(3);
        assertThat(m.entries().get(0).url()).isEqualTo("/r3");
        m.clear();
        assertThat(m.entries()).isEmpty();
        assertThat(m.droppedCount()).isZero();
    }
}
