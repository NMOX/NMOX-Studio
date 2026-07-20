package org.nmox.studio.rack.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the v1.108.0 rack-UI review's two MED findings:
 *
 * <ol>
 *   <li><b>Listener symmetry (RackPanel).</b> The devices are shared and outlive
 *       the panel, so mouse-handler install must match OUR handler by identity
 *       ({@code dm.owner() == this}) — a bare {@code instanceof DeviceMouse}
 *       matches a different panel's handler — and {@code removeNotify} must
 *       detach them, or the panel leaks and a second panel misbinds.</li>
 *   <li><b>No blocking I/O on the EDT (RackTopComponent).</b> Load Patch and
 *       Export CI must ride {@code SAVE_RP} like the Save button beside them,
 *       not read/parse/write on the EDT.</li>
 * </ol>
 *
 * A future edit that reverts any of these re-opens the exact bug and fails here.
 */
class RackUiThreadingTest {

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("RackPanel installs mouse handlers by owner identity and detaches them on removeNotify")
    void deviceMouseIsIdentityScopedAndDetached() throws Exception {
        String src = source("src/main/java/org/nmox/studio/rack/ui/RackPanel.java");

        // scope to installInteraction's body — uninstallInteraction carries the
        // same identity line, so a file-level contains() can't prove INSTALL
        // uses it (the mutation that reverts only the install guard slips past)
        int inst = src.indexOf("private void installInteraction(");
        assertThat(inst).as("installInteraction exists").isPositive();
        String installBody = src.substring(inst, src.indexOf("\n    }", inst));
        assertThat(installBody)
                .as("the install guard matches only THIS panel's handler, not any DeviceMouse")
                .contains("l instanceof DeviceMouse dm && dm.owner() == this");

        assertThat(src)
                .as("removeNotify detaches this panel's handlers from the shared devices")
                .contains("uninstallInteraction()");
        // uninstallInteraction must actually remove from BOTH listener lists
        assertThat(src).contains("device.removeMouseListener(dm)");
        assertThat(src).contains("device.removeMouseMotionListener(dm)");

        int rn = src.indexOf("public void removeNotify()");
        assertThat(rn).as("removeNotify exists").isPositive();
        String body = src.substring(rn, src.indexOf("\n    }", rn));
        assertThat(body)
                .as("the detach happens inside removeNotify")
                .contains("uninstallInteraction()");
    }

    @Test
    @DisplayName("Load Patch and Export CI ride SAVE_RP, never blocking the EDT")
    void loadAndExportRideTheLane() throws Exception {
        String src = source("src/main/java/org/nmox/studio/rack/RackTopComponent.java");

        int load = src.indexOf("private void loadPatch(");
        assertThat(load).as("loadPatch exists").isPositive();
        String loadBody = src.substring(load, src.indexOf("\n    }", load));
        assertThat(loadBody)
                .as("loadPatch reads off the EDT via SAVE_RP + readDocument, applies fromJson on the EDT")
                .contains("SAVE_RP.post(")
                .contains("RackIO.readDocument(file)")
                .contains("java.awt.EventQueue.invokeLater");
        assertThat(loadBody)
                .as("loadPatch no longer calls the EDT-blocking RackIO.load(rack, file)")
                .doesNotContain("RackIO.load(rack, file)");

        int export = src.indexOf("exportCi.addActionListener(");
        assertThat(export).as("Export CI action exists").isPositive();
        String exportBody = src.substring(export, src.indexOf("bar.add(exportCi)", export));
        assertThat(exportBody)
                .as("the mkdir + write ride the lane")
                .contains("SAVE_RP.post(")
                .contains("Files.createDirectories")
                .contains("Files.writeString");
    }
}
