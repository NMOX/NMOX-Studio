package org.nmox.studio.editor.importmap;

import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.importmap.ImportMaps.PageMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The import-map laws: parse keeps key offsets page-true, resolution
 * follows the spec's order (exact wins outright, then the LONGEST
 * trailing-slash prefix), only bare specifiers are in scope, and the
 * span/prefix detectors fire only inside real import gestures.
 */
class ImportMapsTest {

    private static final String PAGE = """
            <!doctype html>
            <script type="importmap">
            {
              "imports": {
                "lit": "https://cdn.example/lit@3/index.js",
                "lit/": "https://cdn.example/lit@3/",
                "lit/directives/": "https://cdn.example/lit@3/directives/",
                "app": "./src/app.js"
              }
            }
            </script>
            """;

    @Test
    @DisplayName("Parse keeps every mapping with its key's page offset")
    void parseKeepsOffsets() {
        PageMap map = ImportMaps.parse(PAGE, new File("index.html"));
        assertThat(map).isNotNull();
        assertThat(map.imports()).containsEntry("lit", "https://cdn.example/lit@3/index.js")
                .containsEntry("app", "./src/app.js");
        // the offset points at the key's quoted spelling IN THE PAGE
        int at = map.keyOffsets().get("app");
        assertThat(PAGE.substring(at, at + 5)).isEqualTo("\"app\"");
    }

    @Test
    @DisplayName("No block, malformed JSON, and empty imports are all null")
    void honestMisses() {
        assertThat(ImportMaps.parse("<html>no map</html>", null)).isNull();
        assertThat(ImportMaps.parse(
                "<script type=\"importmap\">{oops</script>", null)).isNull();
        assertThat(ImportMaps.parse(
                "<script type=\"importmap\">{\"imports\":{}}</script>", null)).isNull();
    }

    @Test
    @DisplayName("Exact match wins outright — never shadowed by a prefix key")
    void exactBeatsPrefix() {
        Map<String, String> imports = ImportMaps.parse(PAGE, null).imports();
        assertThat(ImportMaps.resolveKey("lit", imports)).isEqualTo("lit");
    }

    @Test
    @DisplayName("The LONGEST trailing-slash prefix applies — the spec's order")
    void longestPrefixWins() {
        Map<String, String> imports = ImportMaps.parse(PAGE, null).imports();
        assertThat(ImportMaps.resolveKey("lit/directives/repeat.js", imports))
                .isEqualTo("lit/directives/");
        assertThat(ImportMaps.resolveKey("lit/html.js", imports))
                .isEqualTo("lit/");
        assertThat(ImportMaps.resolveKey("vue", imports)).isNull();
        // the law is LONGEST, not first-seen: pin it with a map whose
        // iteration order provably puts the SHORT key first (the parse
        // rides JSONObject's hash order, which let a first-match mutant
        // survive this test's parsed fixture — full-verdict lesson)
        Map<String, String> shortFirst = new java.util.LinkedHashMap<>();
        shortFirst.put("lit/", "https://cdn.example/lit@3/");
        shortFirst.put("lit/directives/", "https://cdn.example/lit@3/directives/");
        assertThat(ImportMaps.resolveKey("lit/directives/repeat.js", shortFirst))
                .isEqualTo("lit/directives/");
    }

    @Test
    @DisplayName("Only bare specifiers are the map's business")
    void bareOnly() {
        String js = "import { html } from './lit.js';\nimport { x } from 'lit';\n";
        int relative = js.indexOf("./lit.js") + 2;
        int bare = js.lastIndexOf("'lit'") + 2;
        assertThat(ImportMaps.specifierSpanAt(js, relative)).isNull();
        int[] span = ImportMaps.specifierSpanAt(js, bare);
        assertThat(span).isNotNull();
        assertThat(js.substring(span[0], span[1])).isEqualTo("lit");
    }

    @Test
    @DisplayName("The open-quote prefix fires only inside an import gesture")
    void openQuotePrefix() {
        assertThat(ImportMaps.specifierPrefixAt("import { x } from 'li")).isEqualTo("li");
        assertThat(ImportMaps.specifierPrefixAt("const m = await import('ap")).isEqualTo("ap");
        assertThat(ImportMaps.specifierPrefixAt("import 'l")).isEqualTo("l");
        // an ordinary string is not an import gesture
        assertThat(ImportMaps.specifierPrefixAt("const s = 'li")).isNull();
        // a relative prefix gets nothing from the map
        assertThat(ImportMaps.specifierPrefixAt("import { x } from './li")).isNull();
    }
}
