package org.nmox.studio.editor.design;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The design-token core (v1.330.0): declarations found where they are,
 * usages never mistaken for declarations, the project walk bounded, and
 * the var() caret context pure enough to pin.
 */
class CssTokensTest {

    @Test
    @DisplayName("declarations are found; usages and commented-out tokens are not")
    void declarationsVsUsages() {
        String css = """
                :root {
                  --brand: #6c5ce7;
                  --spacing-lg: 24px;
                  /* --retired: red; */
                }
                .card { color: var(--brand, blue); }
                """;
        Map<String, CssTokens.Token> t = CssTokens.declarations(css);
        assertThat(t).containsOnlyKeys("--brand", "--spacing-lg");
        assertThat(t.get("--brand").value()).isEqualTo("#6c5ce7");
        assertThat(css.substring(t.get("--brand").offset()))
                .as("the offset lands ON the declaration, not the usage")
                .startsWith("--brand: #6c5ce7");
    }

    @Test
    @DisplayName("first declaration of a name wins; comments keep offsets stable")
    void firstWinsAndOffsets() {
        String css = "/* a comment\nspanning lines */\n:root { --x: 1px; }\n.a { --x: 2px; }";
        Map<String, CssTokens.Token> t = CssTokens.declarations(css);
        assertThat(t.get("--x").value()).isEqualTo("1px");
        assertThat(css.charAt(t.get("--x").offset())).isEqualTo('-');
    }

    @Test
    @DisplayName("the project walk is bounded and cached, and skips heavy dirs")
    void projectScan(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.createDirectories(dir.resolve("node_modules/lib"));
        Files.writeString(dir.resolve("src/tokens.css"),
                ":root { --brand: rebeccapurple; }");
        Files.writeString(dir.resolve("node_modules/lib/vendor.css"),
                ":root { --vendor-noise: red; }");
        List<CssTokens.ProjectToken> tokens = CssTokens.scanProject(dir.toFile());
        assertThat(tokens).extracting(CssTokens.ProjectToken::name)
                .as("node_modules never contributes tokens")
                .containsExactly("--brand");
        assertThat(tokens.get(0).file().getName()).isEqualTo("tokens.css");
        // second scan serves from the (mtime,size) cache — same result
        assertThat(CssTokens.scanProject(dir.toFile())).hasSize(1);
    }

    @Test
    @DisplayName("varPrefix fires only inside var( — the completion trigger, pinned")
    void varPrefixContext() {
        assertThat(CssTokens.varPrefix("color: var(")).isEmpty();
        assertThat(CssTokens.varPrefix("color: var(--br")).isEqualTo("--br");
        assertThat(CssTokens.varPrefix("color: var( --br")).isEqualTo("--br");
        assertThat(CssTokens.varPrefix("color: red")).isNull();
        assertThat(CssTokens.varPrefix("margin: calc("))
                .as("other functions are not var()")
                .isNull();
        assertThat(CssTokens.varPrefix("--x: 1px; color: "))
                .as("a declaration context is not a var() context")
                .isNull();
    }

    @Test
    @DisplayName("the hyperlink span covers --name only inside var(...)")
    void varNameSpan() {
        String css = ".a { color: var(--brand); } :root { --brand: red; }";
        int inside = css.indexOf("--brand") + 3;
        int[] span = CssTokens.varNameSpanAt(css, inside);
        assertThat(span).isNotNull();
        assertThat(css.substring(span[0], span[1])).isEqualTo("--brand");
        int declaration = css.lastIndexOf("--brand") + 3;
        assertThat(CssTokens.varNameSpanAt(css, declaration))
                .as("the DECLARATION is not a link — it is the destination")
                .isNull();
    }

    @Test
    @DisplayName("a token whose value is a color resolves to a paintable swatch")
    void tokenValueAsColor() {
        Map<String, CssTokens.Token> t = CssTokens.declarations(
                ":root { --brand: #ff6b3d; --gap: 12px; }");
        assertThat(CssColors.scan(t.get("--brand").value()))
                .as("the value parses through the existing color core")
                .hasSize(1);
        assertThat(CssColors.scan(t.get("--gap").value())).isEmpty();
    }

    @Test
    @DisplayName("var() usages paint as their token's color — the swatch through the indirection")
    void varUsageSwatches() {
        String css = """
                :root { --brand: #ff6b3d; --gap: 12px; }
                .card { color: var(--brand); margin: var(--gap); border: var(--unknown); }
                """;
        var spans = CssTokens.varUsageColorSpans(css);
        assertThat(spans)
                .as("only the color-valued token paints; sizes and unknowns do not")
                .hasSize(1);
        assertThat(css.substring(spans.get(0).start(), spans.get(0).end()))
                .isEqualTo("--brand");
        assertThat(css.substring(0, spans.get(0).start()))
                .as("the span is the USAGE inside var(), not the declaration")
                .contains("color: var(");
        assertThat(spans.get(0).color().getRed()).isEqualTo(0xff);
    }

    @Test
    @DisplayName("an edited stylesheet re-parses AND replaces its cache entry")
    void cacheFollowsEdits(@TempDir Path dir) throws Exception {
        Path sheet = dir.resolve("t.css");
        Files.writeString(sheet, ":root { --a: red; }");
        assertThat(CssTokens.scanProject(dir.toFile()))
                .extracting(CssTokens.ProjectToken::name).containsExactly("--a");
        // an edit with a DIFFERENT size (mtime granularity can be 1s —
        // size divergence is what makes this deterministic)
        Files.writeString(sheet, ":root { --a: red; --b: blue; }");
        assertThat(CssTokens.scanProject(dir.toFile()))
                .as("the stale parse must not be served after the edit")
                .extracting(CssTokens.ProjectToken::name)
                .containsExactly("--a", "--b");
    }
}
