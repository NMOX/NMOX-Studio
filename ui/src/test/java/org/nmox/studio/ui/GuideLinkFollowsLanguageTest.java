package org.nmox.studio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Welcome's guide links actually consult the language (v2.104.0).
 *
 * <p>The seam is proven in core by {@code UiLocaleGuideTest}; this is the
 * other half the house law asks for, because a correct seam nobody calls is
 * a payload without a gate — the exact shape that let v1.210.0 declare a
 * User Guide button and never build it, unnoticed for six releases.
 *
 * <p>Source-gated rather than driven: both call sites hand a URL to the
 * desktop browser, which is not something a test should open.
 */
class GuideLinkFollowsLanguageTest {

    private static String mainWindow() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio",
                "ui", "MainWindow.java"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the guide URL is derived from UiLocale, not a hard-coded English path")
    void theUrlAsksTheLanguage() throws Exception {
        String body = mainWindow();
        assertThat(body)
                .as("the URL must be built from the language the IDE is speaking")
                .contains("UiLocale.guideDoc()");
        assertThat(body)
                .as("a literal docs/user-guide.md would strand every translated reader")
                .doesNotContain("blob/main/docs/user-guide.md");
    }

    @Test
    @DisplayName("both doors use it — the footer link and every FIRST STEPS chapter link")
    void everyDoorFollowsTheLanguage() throws Exception {
        String body = mainWindow();
        int uses = body.split("userGuideUrl\\(\\)", -1).length - 1;
        // one declaration plus the two call sites: the Welcome footer's
        // User Guide button, and the GUIDE-kind Getting Started rows that
        // append a chapter anchor
        assertThat(uses).as("every place the product sends a reader to the manual").isEqualTo(3);
        assertThat(body).as("the anchored chapter link follows the language too")
                .contains("browse(userGuideUrl() + t.id())");
    }
}
