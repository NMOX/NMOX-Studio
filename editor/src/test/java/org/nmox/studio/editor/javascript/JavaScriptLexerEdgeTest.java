package org.nmox.studio.editor.javascript;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lexer's edge branches: malformed input must degrade to sensible
 * tokens (never an exception, never a runaway token), and incremental
 * relexing after a document edit must restart from the saved state.
 */
class JavaScriptLexerEdgeTest {

    private static List<String> lex(String source) {
        TokenSequence<?> ts = TokenHierarchy
                .create(source, JavaScriptTokenId.language()).tokenSequence();
        List<String> out = new ArrayList<>();
        while (ts.moveNext()) {
            out.add(ts.token().id().name() + ":" + ts.token().text());
        }
        return out;
    }

    @Test
    @DisplayName("'/=' after a value is compound assignment, not a regex start")
    void slashEqualsIsAnOperator() {
        assertThat(lex("total /= 2;"))
                .contains("OPERATOR:/=")
                .noneMatch(t -> t.startsWith("REGEX"));
    }

    @Test
    @DisplayName("A character no identifier can start becomes a bounded ERROR token")
    void strayCharacterIsAnError() {
        List<String> tokens = lex("let x = #;");
        assertThat(tokens).contains("ERROR:#");
        // lexing continues normally past the error
        assertThat(tokens).contains("DELIMITER:;");
    }

    @Test
    @DisplayName("An unterminated string stops at the newline instead of eating the file")
    void unterminatedStringStopsAtNewline() {
        List<String> tokens = lex("\"abc\nnext");
        assertThat(tokens).contains("STRING:\"abc");
        assertThat(tokens).contains("IDENTIFIER:next");
    }

    @Test
    @DisplayName("A backslash escaping the line end also terminates the string token")
    void escapeAtLineEndTerminates() {
        List<String> tokens = lex("'ab\\\nrest");
        assertThat(tokens.get(0)).startsWith("STRING:'ab\\");
        assertThat(tokens).contains("IDENTIFIER:rest");
    }

    @Test
    @DisplayName("Escaped quotes stay inside the string")
    void escapedQuoteStaysInside() {
        assertThat(lex("'a\\'b'")).contains("STRING:'a\\'b'");
    }

    @Test
    @DisplayName("Template literals swallow ${} expressions, lone $ signs and escaped backticks")
    void templateLiteralShapes() {
        assertThat(lex("`a${x + 1}b`")).contains("TEMPLATE_STRING:`a${x + 1}b`");
        assertThat(lex("`cost $5`")).contains("TEMPLATE_STRING:`cost $5`");
        assertThat(lex("`a\\`b`")).contains("TEMPLATE_STRING:`a\\`b`");
        // nested braces inside the expression stay inside the template
        assertThat(lex("`v${ {a: 1}.a }w`")).contains("TEMPLATE_STRING:`v${ {a: 1}.a }w`");
    }

    @Test
    @DisplayName("Numbers: exponents with signs, decimals, and a number ending at EOF")
    void numberShapes() {
        assertThat(lex("1e+5")).contains("NUMBER:1e+5");
        assertThat(lex("2.5e-3")).contains("NUMBER:2.5e-3");
        assertThat(lex("42")).containsExactly("NUMBER:42");
    }

    @Test
    @DisplayName("A would-be regex broken by a newline backs out to a division operator")
    void brokenRegexBacksOut() {
        List<String> tokens = lex("x = /ab\ncd");
        assertThat(tokens).contains("OPERATOR:/");
        assertThat(tokens).contains("IDENTIFIER:ab", "IDENTIFIER:cd");
        assertThat(tokens).noneMatch(t -> t.startsWith("REGEX"));
    }

    @Test
    @DisplayName("'++' yields a value: 'i++ / 2' divides instead of starting a regex")
    void incrementThenDivision() {
        assertThat(lex("i++ / 2"))
                .contains("OPERATOR:++", "OPERATOR:/")
                .noneMatch(t -> t.startsWith("REGEX"));
    }

    // Document-based incremental-relex coverage (the restart constructor and
    // continueTemplateLiteral) needs the platform's DocumentUtilities, whose
    // lock probe reflects into javax.swing.text — the test JVM does not open
    // java.desktop, and pom changes are out of scope here. String-based
    // hierarchies cover every other lexer branch.
}
