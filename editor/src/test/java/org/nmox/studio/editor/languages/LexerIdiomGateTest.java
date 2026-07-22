package org.nmox.studio.editor.languages;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate: every CSL language config resolves its lexer language through
 * the cycle-guarded {@link Lexers#find} — never bare {@code Language.find} —
 * so the getLexerLanguage → find → CSL-kit → getLexerLanguage recursion that
 * blew the stack on a Nim space (and poisoned the platform's exception logger
 * for the whole session) is structurally impossible to reintroduce.
 */
class LexerIdiomGateTest {

    @Test
    @DisplayName("No language config calls bare Language.find — all ride the recursion guard")
    void allConfigsUseTheGuardedResolver() throws Exception {
        File dir = new File("src/main/java/org/nmox/studio/editor/languages");
        assertThat(dir).isDirectory();
        int guarded = 0;
        for (File f : dir.listFiles((d, n) -> n.endsWith(".java"))) {
            if (f.getName().equals("Lexers.java")) {
                continue; // the guard itself is the one legal Language::find site
            }
            String src = Files.readString(f.toPath());
            assertThat(src)
                    .as("%s must not resolve its own mime with bare Language.find "
                            + "(recursion → StackOverflow; use Lexers.find)", f.getName())
                    .doesNotContain("Language.find(");
            if (src.contains("Lexers.find(")) {
                guarded++;
            }
        }
        assertThat(guarded)
                .as("the whole language family rides the guard (50 configs at last count)")
                .isGreaterThanOrEqualTo(50);
    }
}
