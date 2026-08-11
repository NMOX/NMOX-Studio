package org.nmox.studio.editor.design;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-proof seam law (v1.321.0) over the design-token surfaces:
 * {@link CssTokensTest} proves the seams diverge, this gate proves the
 * call sites exist. Its own first run earned it — the highlighter's
 * token pass could be deleted with every test still green until this
 * file pinned it.
 */
class CssTokenWiringGateTest {

    private static String src(String name) throws Exception {
        return Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/design/" + name).toPath());
    }

    @Test
    @DisplayName("the highlighter paints var() usages through the token pass")
    void highlighterWired() throws Exception {
        assertThat(src("CssColorHighlighter.java"))
                .contains("CssTokens.varUsageColorSpans(");
    }

    @Test
    @DisplayName("completion and the jump are registered on all five css-family mimes")
    void fiveMimesEach() throws Exception {
        for (String file : new String[] {
            "CssVarCompletionProvider.java", "CssVarHyperlink.java"}) {
            String s = src(file);
            for (String mime : new String[] {
                "text/css", "text/scss", "text/less", "text/x-scss", "text/x-less"}) {
                assertThat(s)
                        .as("%s must register %s — the css-prep mimes are the"
                                + " ones REAL .scss/.less files resolve to"
                                + " (the v1.230.0 finding, twice bitten)", file, mime)
                        .contains("mimeType = \"" + mime + "\"");
            }
        }
    }

    @Test
    @DisplayName("the completion trigger and the jump span both ride the tested seams")
    void consumersRideTheSeams() throws Exception {
        assertThat(src("CssVarCompletionProvider.java"))
                .contains("CssTokens.varPrefix(")
                .contains("CssTokens.scanProject(");
        assertThat(src("CssVarHyperlink.java"))
                .contains("CssTokens.varNameSpanAt(")
                .contains("CssTokens.scanProject(");
    }

    @Test
    @DisplayName("the hyperlink's per-hover text read is version-cached (v1.234 law)")
    void hyperlinkTextCached() throws Exception {
        String s = src("CssVarHyperlink.java");
        assertThat(s)
                .as("isHyperlinkPoint runs per ⌘-mouse-move ON THE EDT — an"
                        + " uncached getText copies the document every hover,"
                        + " the exact class the v1.234.0 review fixed in"
                        + " CssColorHyperlink")
                .contains("cachedVersion == version")
                .contains("addDocumentListener");
    }
}
