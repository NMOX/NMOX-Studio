package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.DocsFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The script every language's breakpoint picture pauses in. The picture is
 * only right if the paused line is the accumulating one in EVERY language,
 * so the shape is pinned against the real fixture file, and the reader's
 * words must never be able to break out of their quotes.
 */
class DocsDebugTest {

    private static String fixtures() throws Exception {
        return Files.readString(Path.of("../docs/i18n/forge-fixtures.json"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("in every fixture language the breakpoint line is the accumulating statement")
    void breakpointLineIsFixedInEveryLanguage(@TempDir Path home) throws Exception {
        String fixtures = fixtures();
        for (String lang : new JSONObject(fixtures).keySet()) {
            if (lang.startsWith("_")) {
                continue;
            }
            File dir = new DocsDebug().stage(home.resolve(lang).toFile(), fixtures, lang);
            List<String> lines = Files.readAllLines(new File(dir, DocsDebug.SCRIPT).toPath(), StandardCharsets.UTF_8);
            assertThat(lines.get(DocsDebug.BREAKPOINT_LINE - 1)).as(lang).isEqualTo("  totalValue += value;");
            assertThat(lines.get(0)).as(lang)
                    .isEqualTo("// " + DocsFixtures.text(fixtures, lang, "inventory", "comment"));
            assertThat(String.join("\n", lines)).as(lang)
                    .contains(DocsFixtures.strings(fixtures, lang, "inventory", "items").get(0));
        }
    }

    @Test
    @DisplayName("quotes, backticks and template openers in the reader's words stay inside their strings")
    void wordsCannotBreakOutOfTheirQuotes() {
        String js = DocsDebug.source("a\nb", List.of("O'Brien", "b", "c", "d"), "x`y", "${oops}", "z\\");
        assertThat(js).contains("name: 'O\\'Brien'");
        assertThat(js).contains("console.log(`x\\`y: ${totalValue");
        assertThat(js).contains("console.log(`\\${oops}: ${restockBudget");
        assertThat(js).startsWith("// a b\n");
        assertThat(js.split("\n", -1)[DocsDebug.BREAKPOINT_LINE - 1]).isEqualTo("  totalValue += value;");
    }

    @Test
    @DisplayName("without a staged script the scene never reports ready")
    void notReadyBeforeStaging() {
        DocsDebug scene = new DocsDebug();
        scene.arrange();
        assertThat(scene.ready()).isFalse();
        assertThat(scene.id()).isEqualTo("debug-javascript");
    }
}
