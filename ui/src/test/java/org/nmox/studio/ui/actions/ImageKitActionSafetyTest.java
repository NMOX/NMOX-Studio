package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Image Kit's EDT law, source-gated. The 2026-07-26 night review
 * found the scan — a depth-12, up-to-500-file disk walk — running
 * directly in actionPerformed (the v1.33.1/v1.115.0 class: a wedged
 * mount freezes the paint thread on a menu click). The probe must ride
 * the RP; only the dialog belongs on the EDT.
 */
class ImageKitActionSafetyTest {

    @Test
    @DisplayName("The disk scan and the cwebp probe run on the RP, never the EDT")
    void scanIsOffTheEdt() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/actions/ImageKitAction.java"));

        int firstPost = source.indexOf("RP.post(");
        int scan = source.indexOf("ImagePress.scan(");
        int probe = source.indexOf("ToolLocator.resolve(\"cwebp\")");

        assertThat(firstPost).as("an RP lane exists").isGreaterThan(-1);
        assertThat(scan).as("the scan happens inside it, not in actionPerformed")
                .isGreaterThan(firstPost);
        assertThat(probe).as("the PATH probe rides the same lane")
                .isGreaterThan(firstPost);
        // and the dialog comes back to the EDT explicitly
        assertThat(source).contains("invokeLater(() -> showDialog(");
    }
}
