package org.nmox.studio.editor.typescript;

import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.lexer.TokenHierarchy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;

import static org.assertj.core.api.Assertions.assertThat;

class TypeScriptLanguageTest {

    @Test
    @DisplayName("TypeScript language binds the shared lexer to text/typescript")
    void shouldBindTypeScriptMime() {
        Language<JavaScriptTokenId> language = TypeScriptLanguage.language();
        assertThat(language.mimeType()).isEqualTo("text/typescript");
        // distinct Language instance from the JavaScript binding
        assertThat(language).isNotSameAs(JavaScriptTokenId.language());
        assertThat(JavaScriptTokenId.language().mimeType()).isEqualTo("text/javascript");
    }

    @Test
    @DisplayName("TypeScript source tokenizes: TS keywords colored as keywords")
    void shouldTokenizeTypeScript() {
        String code = "interface User { name: string }";
        TokenHierarchy<String> hierarchy = TokenHierarchy.create(code, TypeScriptLanguage.language());
        TokenSequence<JavaScriptTokenId> ts = hierarchy.tokenSequence(TypeScriptLanguage.language());

        assertThat(ts.moveNext()).isTrue();
        assertThat(ts.token().id()).isEqualTo(JavaScriptTokenId.KEYWORD); // interface
        assertThat(ts.token().text().toString()).isEqualTo("interface");
    }
}
