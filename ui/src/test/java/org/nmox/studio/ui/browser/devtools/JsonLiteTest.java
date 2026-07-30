package org.nmox.studio.ui.browser.devtools;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonLite is the DevTools' one JSON reader and every byte it reads is
 * page-influenced — these tests pin the "never throws, never
 * overflows" contract as hard as the value mapping.
 */
class JsonLiteTest {

    @Test
    @DisplayName("parses objects, arrays, strings, numbers, booleans, null")
    void parsesAllValueKinds() {
        Object v = JsonLite.parse(
                "{\"a\":1,\"b\":\"two\",\"c\":[true,false,null],\"d\":{\"e\":-2.5e2}}");
        Map<String, Object> o = JsonLite.asObject(v);
        assertThat(o.get("a")).isEqualTo(1.0);
        assertThat(o.get("b")).isEqualTo("two");
        assertThat(JsonLite.asArray(o.get("c")))
                .containsExactly(Boolean.TRUE, Boolean.FALSE, JsonLite.NULL);
        assertThat(JsonLite.asObject(o.get("d")).get("e")).isEqualTo(-250.0);
    }

    @Test
    @DisplayName("decodes every escape including \\uXXXX")
    void decodesEscapes() {
        Object v = JsonLite.parse("\"a\\\"b\\\\c\\/d\\n\\t\\r\\b\\f\\u00e9\\u2026\"");
        assertThat(v).isEqualTo("a\"b\\c/d\n\t\r\b\fé…");
    }

    @Test
    @DisplayName("insertion order of object keys is kept")
    void keepsKeyOrder() {
        Map<String, Object> o = JsonLite.asObject(JsonLite.parse("{\"z\":1,\"a\":2,\"m\":3}"));
        assertThat(o.keySet()).containsExactly("z", "a", "m");
    }

    @Test
    @DisplayName("malformed input returns null, never throws")
    void malformedIsNull() {
        assertThat(JsonLite.parse(null)).isNull();
        assertThat(JsonLite.parse("")).isNull();
        assertThat(JsonLite.parse("{")).isNull();
        assertThat(JsonLite.parse("{\"a\":}")).isNull();
        assertThat(JsonLite.parse("[1,]")).isNull();
        assertThat(JsonLite.parse("{\"a\":1} trailing")).isNull();
        assertThat(JsonLite.parse("\"unterminated")).isNull();
        assertThat(JsonLite.parse("{'single':1}")).isNull();
        assertThat(JsonLite.parse("NaN")).isNull();
        assertThat(JsonLite.parse("\"bad\\u00zz\"")).isNull();
        assertThat(JsonLite.parse("\"raw" + (char) 1 + "control\"")).isNull();
    }

    @Test
    @DisplayName("10k-deep nesting bomb answers null — no StackOverflowError")
    void deepNestingIsNullNotOverflow() {
        StringBuilder bomb = new StringBuilder();
        bomb.append("[".repeat(10_000)).append("]".repeat(10_000));
        assertThat(JsonLite.parse(bomb.toString())).isNull();
        // and the same shape within the cap parses fine
        String ok = "[".repeat(50) + "1" + "]".repeat(50);
        assertThat(JsonLite.parse(ok)).isNotNull();
    }

    @Test
    @DisplayName("input past the 8M-char ceiling is refused")
    void hugeInputRefused() {
        String big = "\"" + "x".repeat(JsonLite.MAX_INPUT) + "\"";
        assertThat(JsonLite.parse(big)).isNull();
    }

    @Test
    @DisplayName("convenience accessors default on wrong shapes")
    void accessorsDefault() {
        Map<String, Object> o = JsonLite.asObject(JsonLite.parse("{\"n\":7,\"s\":\"x\"}"));
        assertThat(JsonLite.num(o, "n", -1)).isEqualTo(7);
        assertThat(JsonLite.num(o, "s", -1)).isEqualTo(-1);
        assertThat(JsonLite.num(o, "missing", -1)).isEqualTo(-1);
        assertThat(JsonLite.str(o, "s", "d")).isEqualTo("x");
        assertThat(JsonLite.str(o, "n", "d")).isEqualTo("d");
        assertThat(JsonLite.asObject("not a map")).isEmpty();
        assertThat(JsonLite.asArray("not a list")).isEmpty();
    }

    @Test
    @DisplayName("whitespace everywhere legal is tolerated")
    void whitespaceTolerated() {
        Object v = JsonLite.parse("  { \"a\" : [ 1 , 2 ] }  ");
        assertThat(JsonLite.asArray(JsonLite.asObject(v).get("a"))).hasSize(2);
    }
}
