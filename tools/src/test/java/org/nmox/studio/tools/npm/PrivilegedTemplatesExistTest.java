package org.nmox.studio.tools.npm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The privileged-templates list points at templates that exist
 * (v1.286.0, the project-starter persona walk).
 *
 * <p>{@code WebProjectRecommendedTemplates.PRIVILEGED} is the "float
 * the everyday files to the top" feature — and from the day the class
 * was written, four of its five entries named paths no shipped module
 * ever registered ({@code Templates/ClientSide/javascript.js} among
 * them), so a JavaScript IDE offered no JavaScript file in New File.
 * A dangling entry is invisible in every review: the wizard just
 * silently doesn't float it. This gate makes the list honest:
 *
 * <ul>
 *   <li>every {@code Templates/ClientSide/} entry we own must appear in
 *       the editor module's generated layer (reactor order builds
 *       editor before tools, so the layer file is on disk), and</li>
 *   <li>platform-owned entries are pinned to the exact filenames read
 *       out of the assembled app's module layers on 2026-08-06 —
 *       {@code html.html}, {@code CascadeStyleSheet.css}, {@code file}
 *       — so a silent rename in a platform upgrade fails here instead
 *       of un-floating the entry.</li>
 * </ul>
 */
class PrivilegedTemplatesExistTest {

    @Test
    @DisplayName("our ClientSide entries are registered in the editor layer")
    void ourEntriesAreRegistered() throws Exception {
        Path layer = Path.of("..", "editor", "target", "classes",
                "META-INF", "generated-layer.xml");
        assertThat(layer)
                .as("reactor order builds editor before tools")
                .exists();
        String xml = Files.readString(layer, StandardCharsets.UTF_8);

        for (String path : new WebProjectRecommendedTemplates().getPrivilegedTemplates()) {
            if (!path.startsWith("Templates/ClientSide/")) {
                continue;
            }
            String file = path.substring(path.lastIndexOf('/') + 1);
            assertThat(xml)
                    .as("%s is privileged but not registered — the wizard"
                            + " will silently not float it, which is the"
                            + " exact bug this gate exists to prevent", path)
                    .contains("<file name=\"" + file + "\"");
        }
    }

    @Test
    @DisplayName("platform entries use the platform's real filenames")
    void platformEntriesUseRealNames() {
        String[] privileged = new WebProjectRecommendedTemplates().getPrivilegedTemplates();
        assertThat(privileged)
                .as("the paths verified against the assembled app's layers;"
                        + " ClientSide/html.html and ClientSide/css.css were"
                        + " the dangling spellings")
                .contains("Templates/Other/html.html",
                        "Templates/Other/CascadeStyleSheet.css",
                        "Templates/Other/file")
                .doesNotContain("Templates/ClientSide/html.html",
                        "Templates/ClientSide/css.css");
    }
}
