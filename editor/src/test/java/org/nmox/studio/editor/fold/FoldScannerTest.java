package org.nmox.studio.editor.fold;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;

import static org.assertj.core.api.Assertions.assertThat;

class FoldScannerTest {

    @Test
    @DisplayName("Multi-line brace blocks fold; single-line blocks do not")
    void bracesFoldOnlyWhenMultiLine() {
        String code = "function hi() {\n  return 1;\n}\nconst x = { a: 1 };\n";
        List<FoldScanner.Span> spans = FoldScanner.scan(code, JavaScriptTokenId.language());

        assertThat(spans).hasSize(1);
        FoldScanner.Span block = spans.get(0);
        assertThat(block.comment()).isFalse();
        assertThat(code.charAt(block.start())).isEqualTo('{');
        assertThat(code.charAt(block.end() - 1)).isEqualTo('}');
    }

    @Test
    @DisplayName("Multi-line block comments fold")
    void blockCommentsFold() {
        String code = "/**\n * docs\n */\nlet a = 1; /* inline */\n";
        List<FoldScanner.Span> spans = FoldScanner.scan(code, JavaScriptTokenId.language());

        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).comment()).isTrue();
    }

    @Test
    @DisplayName("Braces inside strings and templates never fold")
    void stringBracesIgnored() {
        String code = "const s = \"{\\n}\";\nconst t = `{\n}`;\n";
        List<FoldScanner.Span> spans = FoldScanner.scan(code, JavaScriptTokenId.language());

        assertThat(spans).isEmpty();
    }

    @Test
    @DisplayName("Nested blocks each get their own fold")
    void nestedBlocksFoldIndividually() {
        String code = "class A {\n  method() {\n    go();\n  }\n}\n";
        List<FoldScanner.Span> spans = FoldScanner.scan(code, JavaScriptTokenId.language());

        assertThat(spans).hasSize(2);
    }
}
