package org.nmox.studio.editor.javascript;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classic JavaScript lexing ambiguity: '/' is a regex after an
 * operator or keyword, division after a value. Wrong answers paint
 * half the file as a regex - these pin the disambiguation rules.
 */
class RegexLexingTest {

    private List<String> lex(String source) {
        TokenSequence<?> ts = TokenHierarchy
                .create(source, JavaScriptTokenId.language()).tokenSequence();
        List<String> out = new ArrayList<>();
        while (ts.moveNext()) {
            out.add(ts.token().id().name() + ":" + ts.token().text());
        }
        return out;
    }

    @Test
    @DisplayName("A regex literal after '=' lexes as one REGEX token, flags included")
    void regexAfterAssignment() {
        assertThat(lex("const re = /ab+c/gi;"))
                .contains("REGEX:/ab+c/gi")
                .noneMatch(t -> t.startsWith("OPERATOR:/"));
    }

    @Test
    @DisplayName("Division between identifiers stays an operator")
    void divisionBetweenValues() {
        assertThat(lex("total / count / 2"))
                .contains("OPERATOR:/")
                .noneMatch(t -> t.startsWith("REGEX"));
    }

    @Test
    @DisplayName("After a closing paren '/' divides; after 'return' it matches")
    void parenAndKeywordContexts() {
        assertThat(lex("(a + b) / 2")).contains("OPERATOR:/");
        assertThat(lex("return /ok/.test(s);")).contains("REGEX:/ok/");
    }

    @Test
    @DisplayName("A regex as a call argument is recognized")
    void regexAsArgument() {
        assertThat(lex("s.replace(/x/g, 'y')")).contains("REGEX:/x/g");
    }

    @Test
    @DisplayName("Escapes and character classes don't end the regex early")
    void escapesAndClasses() {
        assertThat(lex("const p = /a\\/b/;")).contains("REGEX:/a\\/b/");
        assertThat(lex("const q = /[/]/;")).contains("REGEX:/[/]/");
    }

    @Test
    @DisplayName("An unterminated '/' falls back to a division operator")
    void unterminatedSlashDegrades() {
        assertThat(lex("const x = 1; // half\nconst y = a / b\nconst z = 2;"))
                .contains("OPERATOR:/")
                .noneMatch(t -> t.startsWith("REGEX"));
        // a lone slash at line end must not eat the next line
        assertThat(lex("let a = x /\nlet b = 2;")).contains("OPERATOR:/");
    }
}
