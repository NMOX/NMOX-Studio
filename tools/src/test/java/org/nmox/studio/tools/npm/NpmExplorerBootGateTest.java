package org.nmox.studio.tools.npm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.38.0 startup measurement caught this tab spawning `npm ls -g`
 * twice on every boot — once from the constructor, once from
 * componentOpened — both of which fire during window-system load while
 * the tab sits hidden behind the selected one. The JFR process log showed
 * these as the ONLY external processes the whole IDE spawns at boot.
 *
 * This pins the fix's shape (the DB Studio Docker-offer idiom): boot-time
 * lifecycle callbacks take a note; the refresh — and with it any npm
 * spawn — waits for componentShowing, which only fires for a tab someone
 * can actually see.
 */
class NpmExplorerBootGateTest {

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("boot never refreshes: constructor and componentOpened take a note, componentShowing serves it")
    void shouldDeferRefreshToShowing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/npm/NpmExplorerTopComponent.java"),
                StandardCharsets.UTF_8);

        // the visibility-gated listener inside the constructor may NAME the
        // method; what must be absent is a direct constructor-body call
        // (statement indentation), which is what ran npm on every boot
        assertThat(method(source, "public NpmExplorerTopComponent()"))
                .as("constructing at window-system load must not refresh — the "
                        + "no-project branch spawns npm for a tab nobody sees")
                .doesNotContain("\n        refreshProjectView();");

        assertThat(method(source, "public void componentOpened()"))
                .as("componentOpened fires at boot for hidden tabs; it marks, never refreshes")
                .doesNotContain("refreshProjectView()")
                .contains("refreshPending = true");

        assertThat(method(source, "protected void componentShowing()"))
                .as("the deferred refresh is served exactly when the tab becomes visible")
                .contains("refreshPending")
                .contains("refreshProjectView()");
    }

    @Test
    @DisplayName("a re-aim while hidden takes the note too — no npm behind an invisible tab")
    void shouldGateListenerRefreshOnVisibility() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/npm/NpmExplorerTopComponent.java"),
                StandardCharsets.UTF_8);

        String listener = method(source, "public void projectChanged()");
        assertThat(listener)
                .as("the rack listener refreshes only a visible tab; hidden ones mark pending")
                .contains("isShowing()")
                .contains("refreshPending = true");
    }
}
