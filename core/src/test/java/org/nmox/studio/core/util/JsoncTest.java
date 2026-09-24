package org.nmox.studio.core.util;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VS Code's JSON with comments, made JSON: the one stripper tasks.json,
 * launch.json and settings.json are read through.
 */
class JsoncTest {

    @Test
    @DisplayName("line and block comments go; a block comment's line breaks stay, so errors name the real line")
    void comments() {
        assertThat(Jsonc.strip("{\"a\": 1 // why\n}")).isEqualTo("{\"a\": 1 \n}");
        assertThat(Jsonc.strip("{/* one\ntwo */\"a\": 1}")).isEqualTo("{\n\"a\": 1}");
        assertThat(Jsonc.strip("{\"a\": 1} /* never closed")).isEqualTo("{\"a\": 1} ");
    }

    @Test
    @DisplayName("a trailing comma before } or ] goes, whitespace and all")
    void trailingCommas() {
        assertThat(new JSONObject(Jsonc.strip("{\"a\": [1, 2,\n ],\n \"b\": 3,\n}")).getInt("b")).isEqualTo(3);
        assertThat(Jsonc.strip("[1,]")).isEqualTo("[1]");
    }

    @Test
    @DisplayName("inside a string nothing is touched: slashes, a comma before a brace, an escaped quote")
    void stringsUntouched() {
        String json = "{\"url\": \"http://x/*y*/\", \"s\": \"a,}\", \"q\": \"say \\\"hi\\\" // no\"}";
        assertThat(Jsonc.strip(json)).isEqualTo(json);
        assertThat(new JSONObject(Jsonc.strip(json)).getString("q")).isEqualTo("say \"hi\" // no");
    }

    @Test
    @DisplayName("an unterminated string or a lone slash does not throw")
    void hostileInput() {
        assertThat(Jsonc.strip("{\"a\": \"open")).isEqualTo("{\"a\": \"open");
        assertThat(Jsonc.strip("{\"a\": \"ends in \\")).isEqualTo("{\"a\": \"ends in \\");
        assertThat(Jsonc.strip("1 / 2")).isEqualTo("1 / 2");
        assertThat(Jsonc.strip("")).isEmpty();
    }
}
