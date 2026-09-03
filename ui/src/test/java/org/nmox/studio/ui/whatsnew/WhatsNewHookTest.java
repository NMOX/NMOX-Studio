package org.nmox.studio.ui.whatsnew;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first-boot What's New rides the platform's post-UI hook, and only
 * that. {@code @OnShowing} is a named-service definition (path
 * Modules/UIReady) the annotation processor turns into a file under
 * META-INF/namedservices — so this gate reads the OUTCOME (the generated
 * registration), never the annotation, which reflection cannot see.
 */
class WhatsNewHookTest {

    @Test
    @DisplayName("WhatsNewOnShowing is registered under Modules/UIReady, and Welcome no longer calls firstBoot")
    void hookIsThePlatformsAndTheOnlyOne() throws Exception {
        Path registration = Path.of("target", "classes", "META-INF", "namedservices", "Modules", "UIReady",
                "java.lang.Runnable");
        assertThat(registration).as("the processor's @OnShowing registration file").exists();
        assertThat(Files.readString(registration))
                .as("WhatsNewOnShowing must be an @OnShowing Runnable")
                .contains(WhatsNewOnShowing.class.getName());
        assertThat(Runnable.class).isAssignableFrom(WhatsNewOnShowing.class);
        String welcome = Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio", "ui", "MainWindow.java"));
        assertThat(welcome).as("a Welcome-tab call would fire mid-boot and vanish with a closed tab")
                .doesNotContain("WhatsNew.firstBoot()");
    }
}
