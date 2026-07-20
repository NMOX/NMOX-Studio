package org.nmox.studio.ui;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The screenshot forge's contract: every shot it takes belongs to a real
 * tutorial (no orphan images, no drift when tutorials are renamed), and
 * without the property the @OnStart hook is a single property read — the
 * zero-boot-cost law.
 */
class DocsShotsTest {

    @Test
    @DisplayName("Every shot maps to an existing tutorial page")
    void shotsMatchTutorials() {
        File tutorials = new File("../docs/tutorials");
        assertThat(tutorials).isDirectory();
        for (String image : DocsShots.SHOTS.values()) {
            String page = image.replace(".png", ".md");
            assertThat(new File(tutorials, page))
                    .as("shot %s illustrates a real tutorial", image)
                    .isFile();
        }
        assertThat(DocsShots.SHOTS.values())
                .as("one image per tab, no duplicates")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Without the property, the boot hook does nothing")
    void gateHoldsWithoutProperty() {
        // the zero-boot-cost law: no property → return before any window
        // system touch. In this bare unit-test JVM a WindowManager call would
        // be observable (the dummy implementation logs/throws on some paths);
        // the real assertion is simply that run() is a no-op that cannot fail.
        System.clearProperty("nmox.shots.dir");
        new DocsShots().run(); // must not throw, must not require a window system
    }
}
