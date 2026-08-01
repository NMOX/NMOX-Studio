package org.nmox.studio.editor.lsp;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mime → LSP-language-identifier mapping (v1.218.0): the generic
 * strip-the-subtype rule, the exception table, and the wiring gate that
 * keeps every launched server on the resolver.
 */
class LspLanguageIdsTest {

    @Test
    @DisplayName("the generic rule: subtype minus x- prefix")
    void genericRule() {
        assertThat(LspLanguageIds.forMime("text/x-python")).isEqualTo("python");
        assertThat(LspLanguageIds.forMime("text/x-go")).isEqualTo("go");
        assertThat(LspLanguageIds.forMime("text/x-rust")).isEqualTo("rust");
        assertThat(LspLanguageIds.forMime("text/typescript")).isEqualTo("typescript");
        assertThat(LspLanguageIds.forMime("text/javascript")).isEqualTo("javascript");
        assertThat(LspLanguageIds.forMime("text/html")).isEqualTo("html");
        assertThat(LspLanguageIds.forMime("text/css")).isEqualTo("css");
        assertThat(LspLanguageIds.forMime("text/x-yaml")).isEqualTo("yaml");
    }

    @Test
    @DisplayName("the exceptions: where our mime name and the LSP id differ")
    void exceptions() {
        // ngserver only treats a document as an external template when
        // its languageId is html — the whole reason this class exists
        assertThat(LspLanguageIds.forMime("text/x-ng-template")).isEqualTo("html");
        assertThat(LspLanguageIds.forMime("text/sh")).isEqualTo("shellscript");
        assertThat(LspLanguageIds.forMime("text/x-php5")).isEqualTo("php");
        assertThat(LspLanguageIds.forMime("text/x-vlang")).isEqualTo("v");
    }

    @Test
    @DisplayName("degenerate input returns null so the client keeps its own fallback")
    void degenerateInput() {
        assertThat(LspLanguageIds.forMime(null)).isNull();
        assertThat(LspLanguageIds.forMime("content/unknown".replace("/", ""))).isNull();
    }

    @Test
    @DisplayName("launch() passes the resolver — the 3-arg create would silently revert to raw mimes")
    void launchCarriesTheResolver() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"));
        assertThat(src)
                .as("the server description's Lookup must carry LspLanguageIds — "
                        + "without it didOpen's languageId is the raw mime and "
                        + "id-keyed servers ignore every document")
                .contains("Lookups.fixed(new LspLanguageIds())");
        // and no create() call bypasses the 4-arg overload
        assertThat(src.split("LanguageServerDescription\\.create\\(", -1).length - 1)
                .as("exactly one create() site — the launch() choke point")
                .isEqualTo(1);
    }
}
