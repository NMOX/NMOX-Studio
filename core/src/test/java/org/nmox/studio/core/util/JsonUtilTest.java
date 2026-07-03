package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared JSON glances, now defined once: the sniff and the
 * pretty-printer that every response console leans on.
 */
class JsonUtilTest {

    @Test
    @DisplayName("looksJson sniffs objects and arrays, null- and whitespace-safe")
    void sniff() {
        assertThat(JsonUtil.looksJson("{\"a\":1}")).isTrue();
        assertThat(JsonUtil.looksJson("  [1,2]  ")).isTrue();
        assertThat(JsonUtil.looksJson("plain text")).isFalse();
        assertThat(JsonUtil.looksJson("")).isFalse();
        assertThat(JsonUtil.looksJson(null)).isFalse();
    }

    @Test
    @DisplayName("pretty indents objects and arrays; anything else passes through raw")
    void pretty() {
        assertThat(JsonUtil.pretty("{\"a\":1,\"b\":2}"))
                .contains("\n").contains("\"a\": 1");
        assertThat(JsonUtil.pretty("[1,2]")).contains("\n");
        assertThat(JsonUtil.pretty("not json at all")).isEqualTo("not json at all");
        assertThat(JsonUtil.pretty("{broken")).isEqualTo("{broken");
        assertThat(JsonUtil.pretty(null)).isEmpty();
    }
}
