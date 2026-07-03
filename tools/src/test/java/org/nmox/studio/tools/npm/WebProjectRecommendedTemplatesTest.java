package org.nmox.studio.tools.npm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The New File wizard scoping: a web project recommends the web-stack
 * template categories and privileges the everyday files, and hands back
 * defensive copies so a caller can't corrupt the shared catalog.
 */
class WebProjectRecommendedTemplatesTest {

    private final WebProjectRecommendedTemplates templates = new WebProjectRecommendedTemplates();

    @Test
    @DisplayName("Recommended types are the web-stack categories, not the whole IDE catalog")
    void recommendedTypesAreTheWebStack() {
        assertThat(templates.getRecommendedTypes())
                .containsExactly("web", "html5", "javascript", "json", "XML", "simple-files");
    }

    @Test
    @DisplayName("Privileged templates float the everyday web files to the top")
    void privilegedTemplatesAreEverydayFiles() {
        assertThat(templates.getPrivilegedTemplates())
                .containsExactly(
                        "Templates/ClientSide/html.html",
                        "Templates/ClientSide/javascript.js",
                        "Templates/ClientSide/json.json",
                        "Templates/ClientSide/css.css",
                        "Templates/Other/file");
    }

    @Test
    @DisplayName("Both catalogs are defensively copied — mutating one call cannot poison the next")
    void catalogsAreDefensivelyCopied() {
        String[] types = templates.getRecommendedTypes();
        types[0] = "poison";
        assertThat(templates.getRecommendedTypes()).doesNotContain("poison");

        String[] privileged = templates.getPrivilegedTemplates();
        privileged[0] = "poison";
        assertThat(templates.getPrivilegedTemplates()).doesNotContain("poison");
    }
}
