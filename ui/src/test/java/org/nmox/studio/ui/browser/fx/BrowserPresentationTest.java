package org.nmox.studio.ui.browser.fx;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser follows Presentation Mode (v2.87.0). The panel needs a
 * JavaFX toolkit to construct, so the wiring is pinned at the source:
 * the hook is attached in addNotify and detached in removeNotify (the
 * listener-symmetry law), a late subscriber reads the current state, and
 * leaving restores the user's own zoom rather than dividing back.
 */
class BrowserPresentationTest {

    @Test
    @DisplayName("the presentation hook is attached in addNotify, detached in removeNotify, and reads the current state on attach")
    void symmetricHook() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"));
        int add = src.indexOf("public void addNotify()");
        int remove = src.indexOf("public void removeNotify()");
        assertThat(add).isPositive();
        assertThat(remove).isGreaterThan(add);
        String addBody = src.substring(add, remove);
        assertThat(addBody).contains("Presentation.addListener(presentationHook)")
                .contains("follow(org.nmox.studio.core.util.Presentation.isOn())");
        String removeBody = src.substring(remove, src.indexOf("}", src.indexOf("super.removeNotify()")));
        assertThat(removeBody).contains("Presentation.removeListener(presentationHook)");
    }

    @Test
    @DisplayName("entering multiplies the user's zoom through the one core rule; leaving restores the remembered zoom, never a division")
    void enterAndRestore() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"));
        int follow = src.indexOf("void follow(boolean on)");
        String body = src.substring(follow, src.indexOf("/** EDT. Clamped zoom", follow));
        assertThat(body).contains("zoomBeforePresenting = zoom;")
                .contains("Presentation.browserZoom(zoom, true)")
                .contains("setZoom(zoomBeforePresenting)");
        assertThat(body).doesNotContain("/ org.nmox").doesNotContain("zoom / ");
    }
}
