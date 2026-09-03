package org.nmox.studio.ui.whatsnew;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.windows.OnShowing;

import static org.assertj.core.api.Assertions.assertThat;

/** The first-boot What's New rides the platform's post-UI hook, and only that. */
class WhatsNewHookTest {

    @Test
    @DisplayName("WhatsNewOnShowing is an @OnShowing Runnable, and Welcome no longer calls firstBoot")
    void hookIsThePlatformsAndTheOnlyOne() throws Exception {
        assertThat(WhatsNewOnShowing.class.isAnnotationPresent(OnShowing.class))
                .as("the hook must be the platform's @OnShowing").isTrue();
        assertThat(Runnable.class).isAssignableFrom(WhatsNewOnShowing.class);
        String welcome = Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio", "ui", "MainWindow.java"));
        assertThat(welcome).as("a Welcome-tab call would fire mid-boot and vanish with a closed tab")
                .doesNotContain("WhatsNew.firstBoot()");
    }
}
