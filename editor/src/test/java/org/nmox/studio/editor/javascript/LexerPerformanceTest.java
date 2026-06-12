package org.nmox.studio.editor.javascript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real projects open real files: generated bundles, vendored libraries,
 * megabyte-sized source. The lexer must chew through them without
 * making the editor breathe heavy.
 */
class LexerPerformanceTest {

    @Test
    @DisplayName("A 1MB JavaScript file lexes fully in under 2 seconds")
    void megabyteFileLexesFast() {
        String unit = """
                // module %d
                const value%d = { a: 1, b: 'text', c: `tpl ${x}` };
                function fn%d(arg) {
                    /* block comment */
                    if (arg !== null && arg >= 0.5) { return arg * 2; }
                    return [1, 2, 3].map(n => n + value%d.a);
                }
                """;
        StringBuilder source = new StringBuilder(1_200_000);
        int i = 0;
        while (source.length() < 1_000_000) {
            source.append(unit.formatted(i, i, i, i));
            i++;
        }

        long start = System.nanoTime();
        TokenHierarchy<String> hierarchy =
                TokenHierarchy.create(source.toString(), JavaScriptTokenId.language());
        TokenSequence<?> ts = hierarchy.tokenSequence();
        int tokens = 0;
        int errors = 0;
        while (ts.moveNext()) {
            tokens++;
            if ("error".equals(ts.token().id().primaryCategory())) {
                errors++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(tokens).as("the whole file must tokenize").isGreaterThan(100_000);
        assertThat(errors).as("clean source must produce no error tokens").isZero();
        assertThat(elapsedMs).as("1MB lex time (ms)").isLessThan(2_000);
    }
}
