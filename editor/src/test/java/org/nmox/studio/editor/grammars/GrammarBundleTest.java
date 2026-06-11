package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nmox.studio.editor.polyglot.LanguageComments;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TextMate grammars must ship inside the module and be valid JSON
 * with a scopeName - a missing or mangled grammar would silently kill
 * highlighting for that whole language.
 */
class GrammarBundleTest {

    @ParameterizedTest
    @ValueSource(strings = {"java", "c", "cpp", "python", "ruby", "rust", "php", "shell", "json"})
    @DisplayName("Grammar resource exists and parses with a scopeName")
    void grammarShipsAndParses(String language) throws IOException {
        String resource = language + ".tmLanguage.json";
        try (InputStream in = GrammarBundleTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource + " on classpath").isNotNull();
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject json = new org.json.JSONObject(text);
            assertThat(json.getString("scopeName")).as(resource + " scopeName").isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/x-java", "text/x-c", "text/x-cpp", "text/x-rust",
        "text/x-php5", "text/x-go", "text/x-python", "text/x-ruby", "text/sh"})
    @DisplayName("Every code language has comment-toggle syntax")
    void commentSyntaxCovered(String mime) {
        assertThat(LanguageComments.lineCommentFor(mime)).as(mime).isNotNull();
    }
}
