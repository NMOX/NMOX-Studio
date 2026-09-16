package org.nmox.studio.ui.browser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.fx.ComplexTextShaping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser posts the shaping install as a method reference from THIS
 * package (v2.165.0). The first in-app run died with an IllegalAccessError at
 * that reference: the install's return type was package-private one package
 * over, which javac accepted and the lambda factory refused at runtime, so the
 * Browser never built its WebView. Only running the reference from here proves
 * it; a test beside the class cannot.
 */
class ShapingInstallReferenceTest {

    @Test
    @DisplayName("the install runs through a method reference from the Browser's package and answers")
    void theBrowsersReferenceRuns() {
        Runnable install = ComplexTextShaping::install;
        install.run();
        assertThat(ComplexTextShaping.install()).isNotNull();
    }
}
