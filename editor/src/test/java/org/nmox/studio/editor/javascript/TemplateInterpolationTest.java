package org.nmox.studio.editor.javascript;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code ${…}} inside a template literal lexes as JavaScript.
 *
 * <p>{@code TEMPLATE_EXPRESSION} was a token id, a lexer state and a colour
 * registered in {@code syntax-colors.xml} that NOTHING emitted: the lexer
 * counted {@code ${}/{@code }} depth only to swallow the whole literal into
 * one string token, so {@code `hi ${user.name}`} painted {@code user.name} as
 * string. Finished in v2.186.0 (debt ledger 114): each literal run is its own
 * {@code TEMPLATE_STRING}, the {@code ${} and its {@code }} are
 * {@code TEMPLATE_EXPRESSION}, and what sits between them goes through the
 * ordinary code path.
 *
 * <p>These tests assert whole token SEQUENCES rather than membership, because
 * the defect this feature replaces was a sequence of one.
 */
class TemplateInterpolationTest {

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
    @DisplayName("`a${b.c}d` — the interpolation's members lex as identifiers, its braces as TEMPLATE_EXPRESSION")
    void interpolationLexesAsJavaScript() {
        assertThat(lex("`a${b.c}d`")).containsExactly(
                "TEMPLATE_STRING:`a",
                "TEMPLATE_EXPRESSION:${",
                "IDENTIFIER:b",
                "DELIMITER:.",
                "IDENTIFIER:c",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:d`");
    }

    @Test
    @DisplayName("An interpolation at the very start still opens with the backtick's own run")
    void interpolationAtTheStart() {
        assertThat(lex("`${x}`")).containsExactly(
                "TEMPLATE_STRING:`",
                "TEMPLATE_EXPRESSION:${",
                "IDENTIFIER:x",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:`");
    }

    @Test
    @DisplayName("Keywords, numbers and operators inside an interpolation are themselves")
    void codeInsideIsRealCode() {
        assertThat(lex("`${typeof x + 1}`")).containsExactly(
                "TEMPLATE_STRING:`",
                "TEMPLATE_EXPRESSION:${",
                "KEYWORD:typeof",
                "WHITESPACE: ",
                "IDENTIFIER:x",
                "WHITESPACE: ",
                "OPERATOR:+",
                "WHITESPACE: ",
                "NUMBER:1",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:`");
    }

    @Test
    @DisplayName("A template nested inside an interpolation keeps both levels straight")
    void nestedTemplate() {
        assertThat(lex("`a${`b${x}c`}d`")).containsExactly(
                "TEMPLATE_STRING:`a",
                "TEMPLATE_EXPRESSION:${",
                "TEMPLATE_STRING:`b",
                "TEMPLATE_EXPRESSION:${",
                "IDENTIFIER:x",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:c`",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:d`");
    }

    @Test
    @DisplayName("An object literal inside an interpolation: its '}' closes the object, the next closes the expression")
    void braceDepthInsideAnInterpolation() {
        assertThat(lex("`v${ {a: 1}.a }w`")).containsExactly(
                "TEMPLATE_STRING:`v",
                "TEMPLATE_EXPRESSION:${",
                "WHITESPACE: ",
                "DELIMITER:{",
                "IDENTIFIER:a",
                "OPERATOR::",
                "WHITESPACE: ",
                "NUMBER:1",
                "DELIMITER:}",
                "DELIMITER:.",
                "IDENTIFIER:a",
                "WHITESPACE: ",
                "TEMPLATE_EXPRESSION:}",
                "TEMPLATE_STRING:w`");
    }

    @Test
    @DisplayName("A '}' outside any interpolation stays an ordinary delimiter")
    void braceOutsideATemplateIsADelimiter() {
        assertThat(lex("function f() { return 1; }"))
                .contains("DELIMITER:{", "DELIMITER:}")
                .noneMatch(t -> t.startsWith("TEMPLATE"));
    }

    @Test
    @DisplayName("\\${ is an escape, not an interpolation: the whole literal stays one string")
    void escapedDollarBraceIsNotAnInterpolation() {
        assertThat(lex("`a\\${b}c`")).containsExactly("TEMPLATE_STRING:`a\\${b}c`");
    }

    @Test
    @DisplayName("An escaped backtick does not end the literal")
    void escapedBacktick() {
        assertThat(lex("`a\\`b`")).containsExactly("TEMPLATE_STRING:`a\\`b`");
    }

    @Test
    @DisplayName("A lone '$' is literal text")
    void loneDollarIsText() {
        assertThat(lex("`cost $5`")).containsExactly("TEMPLATE_STRING:`cost $5`");
    }

    @Test
    @DisplayName("Unterminated after '${': the expression's tokens stand, nothing runs away")
    void unterminatedInterpolation() {
        assertThat(lex("`a${b")).containsExactly(
                "TEMPLATE_STRING:`a",
                "TEMPLATE_EXPRESSION:${",
                "IDENTIFIER:b");
    }

    @Test
    @DisplayName("Unterminated '${' at EOF: the two characters are still their own token")
    void unterminatedAtTheOpeningBraces() {
        assertThat(lex("`a${")).containsExactly(
                "TEMPLATE_STRING:`a",
                "TEMPLATE_EXPRESSION:${");
    }

    @Test
    @DisplayName("Unterminated literal run: the text ends at EOF instead of looping")
    void unterminatedText() {
        assertThat(lex("`abc")).containsExactly("TEMPLATE_STRING:`abc");
    }

    @Test
    @DisplayName("A trailing backslash at EOF terminates rather than reading past the end")
    void trailingBackslashAtEof() {
        assertThat(lex("`ab\\")).containsExactly("TEMPLATE_STRING:`ab\\");
    }

    @Test
    @DisplayName("Every malformed template still makes progress: as many tokens as characters, at most")
    void malformedTemplatesAlwaysAdvance() {
        // the lexer must consume at least one character per token; a runaway
        // would hang the editor rather than fail a test, so the bound is on
        // the token COUNT of inputs built to sit on every boundary
        for (String source : List.of("`", "`${", "`}", "${", "`${}", "`${`", "`${${",
                "}`", "`$", "`\\", "`${{", "`${}}", "``", "`${`${")) {
            assertThat(lex(source))
                    .as("tokens of %s", source)
                    .hasSizeLessThanOrEqualTo(source.length());
        }
    }

    @Test
    @DisplayName("A template is a value, so the '/' after it divides")
    void divisionAfterATemplate() {
        assertThat(lex("`a${b}c` / 2"))
                .contains("OPERATOR:/")
                .noneMatch(t -> t.startsWith("REGEX"));
    }

    @Test
    @DisplayName("A '/' inside an interpolation, where an expression begins, is a regex")
    void regexInsideAnInterpolation() {
        assertThat(lex("`${/ab/.test(s)}`")).contains("REGEX:/ab/");
    }
}
