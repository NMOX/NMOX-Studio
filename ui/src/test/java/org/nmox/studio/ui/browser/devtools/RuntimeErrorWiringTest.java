package org.nmox.studio.ui.browser.devtools;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-proof law's wiring half (v1.321.0): the pipeline exists AND
 * its call sites exist. err() must feed it and every page load must
 * clear it — a green RuntimeErrorsTest with either wire missing is a
 * payload without a gate.
 */
class RuntimeErrorWiringTest {

    @Test
    @DisplayName("err() feeds the pipeline; installBridge clears per load")
    void bothWiresPresent() throws Exception {
        String bridge = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/devtools/JsBridge.java"));
        assertThat(bridge).contains("runtimeErrors.onError(capped);");
        String panel = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"));
        assertThat(panel).contains("bridge.runtimeErrors().onPageLoad()");
    }
}
