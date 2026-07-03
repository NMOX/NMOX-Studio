package org.nmox.studio.editor.typing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.typing.JsTypedBreakInterceptor.Break;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smart Enter for JS/TS. The decision is made purely from the character
 * before and after the caret, the current line, and its indent — so we
 * can pin each branch (brace body, lone brace, block-comment gutter,
 * indent carry-over) directly.
 */
class JsTypedBreakInterceptorTest {

    @Test
    @DisplayName("Between '{' and '}' opens an indented body with the close brace on its own line")
    void braceBody() {
        Break br = JsTypedBreakInterceptor.computeBreak('{', '}', "if (x) {", "");
        assertThat(br).isNotNull();
        assertThat(br.text()).isEqualTo("\n    \n");
        // caret sits on the indented empty line: after '\n' + indent(0) + INDENT(4)
        assertThat(br.caret()).isEqualTo(1 + 0 + 4);
    }

    @Test
    @DisplayName("Brace body carries the existing line indent onto both new lines")
    void braceBodyKeepsIndent() {
        Break br = JsTypedBreakInterceptor.computeBreak('{', '}', "  if (x) {", "  ");
        assertThat(br.text()).isEqualTo("\n      \n  ");   // 2-space indent + 4-space body, then 2-space close
        assertThat(br.caret()).isEqualTo(1 + 2 + 4);
    }

    @Test
    @DisplayName("After a lone '{' indents one stop, caret at end")
    void loneBrace() {
        Break br = JsTypedBreakInterceptor.computeBreak('{', (char) 0, "function f() {", "");
        assertThat(br.text()).isEqualTo("\n    ");
        assertThat(br.caret()).isEqualTo(br.text().length());
    }

    @Test
    @DisplayName("Inside a /* block comment, Enter continues the ' * ' gutter")
    void blockCommentStart() {
        Break br = JsTypedBreakInterceptor.computeBreak('x', (char) 0, "  /* start of a comment", "  ");
        assertThat(br.text()).isEqualTo("\n   * ");   // indent(2) + ' * '
        assertThat(br.caret()).isEqualTo(br.text().length());
    }

    @Test
    @DisplayName("On a continuation '*' line, Enter continues with '* '")
    void blockCommentContinuation() {
        Break br = JsTypedBreakInterceptor.computeBreak('t', (char) 0, "   * more text", "   ");
        assertThat(br.text()).isEqualTo("\n   * ");
    }

    @Test
    @DisplayName("A closed block comment on the line does not continue the gutter")
    void closedBlockComment() {
        // line already contains */, so we fall through to plain indent carry-over
        Break br = JsTypedBreakInterceptor.computeBreak('/', (char) 0, "  /* one liner */", "  ");
        assertThat(br.text()).isEqualTo("\n  ");   // just the indent, no ' * '
    }

    @Test
    @DisplayName("Otherwise Enter carries the current indent")
    void plainIndentCarry() {
        Break br = JsTypedBreakInterceptor.computeBreak('1', (char) 0, "    const x = 1;", "    ");
        assertThat(br.text()).isEqualTo("\n    ");
    }

    @Test
    @DisplayName("At column 0 with nothing smart to do there is no override (plain newline)")
    void nothingSmart() {
        Break br = JsTypedBreakInterceptor.computeBreak('1', (char) 0, "const x = 1;", "");
        assertThat(br).isNull();
    }
}
