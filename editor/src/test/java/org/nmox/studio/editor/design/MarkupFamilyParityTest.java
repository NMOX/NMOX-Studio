package org.nmox.studio.editor.design;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The markup-family parity law (v2.25.0): HtmlStyleRegions.MARKUP_MIMES
 * is the ONE definition of which formats get the style-region
 * treatment, and every surface that rides it — swatches, the picker,
 * var( completion, the var jump — must register EVERY family mime.
 * A surface missing one mime works everywhere you test it and fails
 * only for the framework you didn't (the KeymapProfileParityTest
 * idiom, applied to mime registrations).
 */
class MarkupFamilyParityTest {

    private static final String[] SURFACES = {
        "src/main/java/org/nmox/studio/editor/design/CssColorHighlighter.java",
        "src/main/java/org/nmox/studio/editor/design/CssColorHyperlink.java",
        "src/main/java/org/nmox/studio/editor/design/CssVarCompletionProvider.java",
        "src/main/java/org/nmox/studio/editor/design/CssVarHyperlink.java",
        // the Emmet expansion action serves CSS inside family style
        // regions (v2.24.0/v2.25.0) — same family law, same gate
        "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java",
    };

    @Test
    @DisplayName("every design surface registers every markup-family mime")
    void registrationsCoverTheFamily() throws Exception {
        for (String rel : SURFACES) {
            String src = Files.readString(Path.of(rel), StandardCharsets.UTF_8);
            for (String mime : HtmlStyleRegions.MARKUP_MIMES) {
                assertThat(src)
                        .as(rel + " registers " + mime)
                        .contains("mimeType = \"" + mime + "\"");
            }
            assertThat(src)
                    .as(rel + " consults the SHARED family, not a hand list")
                    .contains("HtmlStyleRegions.isMarkup(");
        }
    }

    @Test
    @DisplayName("the family set holds exactly the intended four")
    void familyMembers() {
        assertThat(HtmlStyleRegions.MARKUP_MIMES).containsExactlyInAnyOrder(
                "text/html", "text/x-vue", "text/x-svelte", "text/x-ng-template");
    }

    @Test
    @DisplayName("a Vue SFC's style block regions like HTML's")
    void vueSfcRegions() {
        String vue = "<template>\n  <p>prose tomato</p>\n"
                + "  <h1 style=\"color: tomato\">x</h1>\n</template>\n"
                + "<style scoped lang=\"scss\">\n.a { color: #1E90FF; }\n</style>\n";
        var spans = HtmlStyleRegions.scan(vue);
        assertThat(spans).hasSize(2);
        assertThat(HtmlStyleRegions.inStyle(vue, vue.indexOf("prose tomato") + 6))
                .isFalse();
    }
}
