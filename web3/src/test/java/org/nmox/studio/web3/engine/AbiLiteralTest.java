package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.engine.AbiLiteral.Group;
import org.nmox.studio.web3.engine.AbiLiteral.Node;
import org.nmox.studio.web3.engine.AbiLiteral.Scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The list literal a person types into an array or tuple field: structure
 * only, strict, and every refusal names the parameter and the character.
 */
class AbiLiteralTest {

    @Test
    @DisplayName("a tuple literal parses to its components, quoted and bare")
    void tupleLiteral() {
        Node node = AbiLiteral.parse("[\"0xabc\", 100, true]", "'order'");
        assertThat(node).isEqualTo(new Group(List.of(
                new Scalar("0xabc", true), new Scalar("100", false), new Scalar("true", false))));
    }

    @Test
    @DisplayName("nested lists, empty lists, and surrounding whitespace")
    void nesting() {
        Node node = AbiLiteral.parse("  [ [1, [ ]] , [\"x\"] ]  ", "x");
        assertThat(node).isEqualTo(new Group(List.of(
                new Group(List.of(new Scalar("1", false), new Group(List.of()))),
                new Group(List.of(new Scalar("x", true))))));
    }

    @Test
    @DisplayName("quoted text keeps commas and brackets and unescapes the JSON escapes")
    void escapes() {
        Node node = AbiLiteral.parse("[\"a,]\\\"b\\\\\\n\\u00e9\\/\\t\\r\\b\\f\"]", "x");
        assertThat(((Group) node).items().get(0))
                .isEqualTo(new Scalar("a,]\"b\\\n\u00e9/\t\r\b\f", true));
    }

    @Test
    @DisplayName("a bare token keeps inner spaces, trimmed at its edges")
    void bareTokenSpaces() {
        assertThat(AbiLiteral.parse("[ hello world ]", "x"))
                .isEqualTo(new Group(List.of(new Scalar("hello world", false))));
    }

    @Test
    @DisplayName("an empty element is refused with its position and the way to write \"\"")
    void emptyElementRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[1,,2]", "'nums'"))
                .hasMessage("Parameter 'nums' has an empty element at character 4"
                        + " — write \"\" for an empty string.");
    }

    @Test
    @DisplayName("a trailing comma is refused")
    void trailingCommaRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[1, 2, ]", "x"))
                .hasMessageContaining("trailing comma before the ] at character 8");
    }

    @Test
    @DisplayName("an unterminated string is refused, naming where it opened")
    void unterminatedStringRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[\"abc", "x"))
                .hasMessageContaining("unterminated string starting at character 2");
        assertThatThrownBy(() -> AbiLiteral.parse("[\"abc\\", "x"))
                .hasMessageContaining("unterminated string");
    }

    @Test
    @DisplayName("a missing closing bracket is refused, naming the open bracket")
    void unclosedListRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[1, [2, 3]", "x"))
                .hasMessageContaining("missing the ] that closes the [ at character 1");
        assertThatThrownBy(() -> AbiLiteral.parse("[", "x"))
                .hasMessageContaining("ends where a value was expected");
    }

    @Test
    @DisplayName("text after the value is refused")
    void trailingTextRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[1, 2] 3", "x"))
                .hasMessageContaining("unexpected text after the value at character 8");
        assertThatThrownBy(() -> AbiLiteral.parse("[\"a\" \"b\"]", "x"))
                .hasMessageContaining("expected , or ] at character 6");
    }

    @Test
    @DisplayName("a JSON object is refused — components go in order")
    void objectRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[{\"to\": 1}]", "'order'"))
                .hasMessageContaining("'order' has an object at character 2")
                .hasMessageContaining("in order as a list");
    }

    @Test
    @DisplayName("a quote or bracket inside a bare token is refused")
    void strayCharacterInBareRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[ab\"c\"]", "x"))
                .hasMessageContaining("unexpected \" at character 4");
        assertThatThrownBy(() -> AbiLiteral.parse("[1[2]]", "x"))
                .hasMessageContaining("unexpected [ at character 3");
    }

    @Test
    @DisplayName("an unknown escape and a short \\u escape are refused")
    void badEscapesRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("[\"a\\qb\"]", "x"))
                .hasMessageContaining("unknown escape \\q");
        assertThatThrownBy(() -> AbiLiteral.parse("[\"\\u12\"]", "x"))
                .hasMessageContaining("\\u escape without four hex digits");
        assertThatThrownBy(() -> AbiLiteral.parse("[\"\\u12zz\"]", "x"))
                .hasMessageContaining("\\u escape without four hex digits");
    }

    @Test
    @DisplayName("blank input and nesting past the depth cap are refused")
    void blankAndTooDeepRefused() {
        assertThatThrownBy(() -> AbiLiteral.parse("   ", "x"))
                .hasMessage("Parameter x is empty.");
        String deep = "[".repeat(AbiLiteral.MAX_DEPTH + 1) + "]".repeat(AbiLiteral.MAX_DEPTH + 1);
        assertThatThrownBy(() -> AbiLiteral.parse(deep, "x"))
                .hasMessageContaining("nests lists deeper than " + AbiLiteral.MAX_DEPTH);
        String atCap = "[".repeat(AbiLiteral.MAX_DEPTH) + "]".repeat(AbiLiteral.MAX_DEPTH);
        assertThat(AbiLiteral.parse(atCap, "x")).isInstanceOf(Group.class);
    }
}
