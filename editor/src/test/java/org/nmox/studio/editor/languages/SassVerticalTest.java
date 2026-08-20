package org.nmox.studio.editor.languages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.nmox.studio.editor.polyglot.LanguageComments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The indented-Sass vertical (v2.20.0): .sass stops sharing the SCSS
 * grammar and becomes its own mime with the canonical indented
 * grammar. The tests pin the split (both directions), the vendored
 * bytes, the ride-along surfaces, and — just as deliberately — the
 * OUTS: Emmet, the outline, and stylelint each stay off the mime for
 * written reasons (their output or premise is braced CSS), and a
 * future registration must remove these pins consciously.
 */
class SassVerticalTest {

    private static String src(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("the vendored grammar matches its NOTICE pin, byte for byte")
    void grammarPinned() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(
                "src/main/resources/org/nmox/studio/editor/grammars/sass.tmLanguage.json"));
        String sha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        String notice = src("src/main/java/org/nmox/studio/editor/grammars/NOTICE-grammars.md");
        assertThat(notice)
                .as("NOTICE carries the exact sha256 of the vendored bytes")
                .contains("sass.tmLanguage.json | sha256 " + sha);
    }

    @Test
    @DisplayName("the split is total: scss keeps .scss, sass takes .sass")
    void mimeSplit() throws Exception {
        String scss = src("src/main/java/org/nmox/studio/editor/grammars/ScssGrammar.java");
        assertThat(scss)
                .as("ScssGrammar no longer claims .sass")
                .contains("extension = {\"scss\"}");
        String sass = src("src/main/java/org/nmox/studio/editor/grammars/SassGrammar.java");
        assertThat(sass)
                .contains("mimeType = \"text/x-sass\"")
                .contains("extension = {\"sass\"}")
                .contains("sass.tmLanguage.json");
    }

    @Test
    @DisplayName("the CSL kit exists and the comment toggle speaks //")
    void cslAndComments() {
        assertThat(new SassLanguage().getLineCommentPrefix()).isEqualTo("//");
        assertThat(LanguageComments.lineCommentFor("text/x-sass")).isEqualTo("//");
    }

    @Test
    @DisplayName("the ride-along surfaces carry the mime")
    void rideAlongs() throws Exception {
        for (String rel : new String[]{
            "src/main/java/org/nmox/studio/editor/design/CssColorHighlighter.java",
            "src/main/java/org/nmox/studio/editor/design/CssColorHyperlink.java",
            "src/main/java/org/nmox/studio/editor/design/CssVarCompletionProvider.java",
            "src/main/java/org/nmox/studio/editor/design/CssVarHyperlink.java",
            "src/main/java/org/nmox/studio/editor/completion/CssCompletionProvider.java",
            "src/main/java/org/nmox/studio/editor/spell/CodeSpellTokenListProvider.java",
            "src/main/java/org/nmox/studio/editor/sass/SassCompileAction.java",
            "src/main/java/org/nmox/studio/editor/angular/NgSwitchActions.java"}) {
            assertThat(src(rel))
                    .as(rel + " registers text/x-sass")
                    .contains("text/x-sass");
        }
    }

    @Test
    @DisplayName("the deliberate OUTS hold: Emmet, outline, stylelint")
    void deliberateOuts() throws Exception {
        String emmet = src("src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java");
        assertThat(emmet.replaceAll("(?m)^\\s*//.*$", ""))
                .as("Emmet emits braced declarations — never registered on the indented mime")
                .doesNotContain("text/x-sass");
        String outline = src("src/main/java/org/nmox/studio/editor/outline/OutlineModel.java");
        assertThat(outline.replaceAll("(?m)^\\s*//.*$", ""))
                .as("the css outline anchors on braces — the indented mime stays unmapped")
                .doesNotContain("text/x-sass");
        String servers = src("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");
        int mimes = servers.indexOf("class StylelintServer");
        String block = servers.substring(mimes, servers.indexOf("startServer", mimes));
        assertThat(block)
                .as("StylelintServer.MIMES stays off the indented mime (customSyntax premise)")
                .doesNotContain("text/x-sass");
    }
}
