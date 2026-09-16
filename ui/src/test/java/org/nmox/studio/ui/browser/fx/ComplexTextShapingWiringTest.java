package org.nmox.studio.ui.browser.fx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shaping has one door (v2.165.0): the Browser installs it, off the EDT,
 * when it builds its WebView. A shaper nobody installs is a feature that never
 * runs, and green unit tests cannot see that.
 */
class ComplexTextShapingWiringTest {

    @Test
    @DisplayName("the Browser posts the install to its own lane before it builds the WebView panel")
    void browserInstallsBeforeBuildingThePanel() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/WebBrowserTopComponent.java"), StandardCharsets.UTF_8);
        int install = src.indexOf("SHAPING_RP.post(org.nmox.studio.ui.browser.fx.ComplexTextShaping::install)");
        int build = src.indexOf("browser = new FxBrowserPanel(");
        assertThat(install).as("the install call").isPositive();
        assertThat(build).as("the panel build").isPositive();
        assertThat(install).as("installed before the WebView exists").isLessThan(build);
    }
}
