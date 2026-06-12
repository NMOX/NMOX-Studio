package org.nmox.studio.editor.occurrences;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierAtTest {

    private static final String TEXT = "const total = price + tax;";

    @Test
    @DisplayName("Caret inside, at start, and just after a word all find it")
    void findsWordAroundCaret() {
        int p = TEXT.indexOf("price");
        assertThat(JsOccurrencesHighlighter.identifierAt(TEXT, p)).isEqualTo("price");
        assertThat(JsOccurrencesHighlighter.identifierAt(TEXT, p + 2)).isEqualTo("price");
        assertThat(JsOccurrencesHighlighter.identifierAt(TEXT, p + 5)).isEqualTo("price");
    }

    @Test
    @DisplayName("Caret on whitespace or punctuation finds nothing")
    void whitespaceFindsNothing() {
        // directly after a word still matches (trailing-edge rule)...
        assertThat(JsOccurrencesHighlighter.identifierAt(TEXT, TEXT.indexOf(" = "))).isEqualTo("total");
        // ...but genuinely between tokens finds nothing
        assertThat(JsOccurrencesHighlighter.identifierAt(TEXT, TEXT.indexOf("= ") + 1)).isNull();
        assertThat(JsOccurrencesHighlighter.identifierAt("a + b", 2)).isNull();
        assertThat(JsOccurrencesHighlighter.identifierAt("", 0)).isNull();
    }

    @Test
    @DisplayName("Dollar and underscore are identifier characters; numbers are not identifiers")
    void identifierCharacterRules() {
        assertThat(JsOccurrencesHighlighter.identifierAt("$el._x", 0)).isEqualTo("$el");
        assertThat(JsOccurrencesHighlighter.identifierAt("$el._x", 4)).isEqualTo("_x");
        assertThat(JsOccurrencesHighlighter.identifierAt("x 42 y", 3)).isNull();
    }
}
