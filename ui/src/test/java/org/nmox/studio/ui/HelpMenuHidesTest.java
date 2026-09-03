package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Help menu shows the product's answers, not the platform's: NetBeans'
 * "Report Issue" (files at Apache NetBeans — the wrong project) and
 * "Keyboard Shortcuts Card" (NetBeans' PDF of chords this product does not
 * ship) stay hidden beside Report a Problem… and Keyboard Shortcuts…
 * (the v2.64.0 walk's find).
 */
class HelpMenuHidesTest {

    @Test
    @DisplayName("Both platform Help entries are hidden in the ui layer")
    void platformHelpEntriesHidden() throws Exception {
        String layer = Files.readString(Path.of("src", "main", "resources", "org", "nmox", "studio", "ui", "layer.xml"));
        assertThat(layer).contains("org-netbeans-modules-utilities-ReportNBIssueAction.shadow_hidden");
        assertThat(layer).contains("shortcuts.xml_hidden");
    }
}
