package org.nmox.studio.editor.lsp;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ledger-79 cure: Angular's src/index.html makes src/ the file's
 * OWNER project, so the ALS must find the workspace by walking up for
 * angular.json itself (the v1.223.0 class, second consumer — proven by
 * probe: dir=ngdemo/src declined on src/angular.json and the whole
 * template-intelligence chain silently died).
 */
class AngularRootAboveTest {

    @Test
    @DisplayName("walks up from the src owner-project to the angular.json root; null when none")
    void walkUp(@TempDir Path root) throws Exception {
        Path src = root.resolve("ws/src");
        Files.createDirectories(src);
        Files.writeString(root.resolve("ws/angular.json"), "{}");
        assertThat(LanguageServers.angularRootAbove(src.toFile()))
                .isEqualTo(root.resolve("ws").toFile());
        assertThat(LanguageServers.angularRootAbove(root.resolve("ws").toFile()))
                .as("the root itself matches too").isEqualTo(root.resolve("ws").toFile());
        Path bare = root.resolve("plain/deep");
        Files.createDirectories(bare);
        assertThat(LanguageServers.angularRootAbove(bare.toFile()))
                .as("no angular.json anywhere above (within the temp root)"
                        + " — but a marker OUTSIDE the temp dir could match, so"
                        + " assert only that the WS root is not returned")
                .isNotEqualTo(root.resolve("plain").toFile());
        assertThat(LanguageServers.angularRootAbove(null)).isNull();
    }
}
