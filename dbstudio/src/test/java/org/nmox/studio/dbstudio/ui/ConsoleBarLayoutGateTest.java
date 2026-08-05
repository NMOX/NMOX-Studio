package org.nmox.studio.dbstudio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 75, first instance (recorded v1.266.0, closed v1.273.0): with
 * the DB Studio tab sharing a row with a wide neighbor, the one-row
 * console toolbar clipped Save… and the saved-queries combo off the
 * right edge — the deferral's own words called a wrap toolbar the
 * honest fix (RUN/EXPLAIN/Cancel are high-frequency and must keep
 * their one-click place, so no menu consolidation). The bar's layout
 * is pure-Swing wiring plain tests can't resize meaningfully, so this
 * gate pins the WrapLayout install at the source: removing it brings
 * the clip class back silently.
 */
class ConsoleBarLayoutGateTest {

    @Test
    @DisplayName("the console toolbar wraps instead of clipping")
    void consoleBarUsesWrapLayout() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "dbstudio", "ui", "DbStudioTopComponent.java"),
                StandardCharsets.UTF_8);
        int m = src.indexOf("private JPanel buildConsolePanel()");
        assertThat(m).as("the console panel builder exists").isPositive();
        String body = src.substring(m, src.indexOf("panel.add(bar", m));
        assertThat(body)
                .as("the bar must install WrapLayout — a one-row toolbar in"
                        + " NORTH clips its rightmost verbs at narrow widths"
                        + " (ledger 75)")
                .contains("WrapLayout(");
    }
}
