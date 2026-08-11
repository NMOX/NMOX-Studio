package org.nmox.studio.rack.ui.controls;

import java.io.File;
import java.nio.file.Files;

import javax.swing.JToolTip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 76's real finding, pinned: every link of the tooltip chain
 * worked (registration, delivery, query — proven by a live probe with
 * a human at a real mouse, 2026-08-11), and the "never fires" verdict
 * was a LAF tooltip so close to the rack's own black that three live
 * checks looked straight through it. The cure is a visible face:
 * {@link RackStyle#phosphorTip} styles rack tips like the LCDs they
 * sit beside. The seam is tested here; the wiring gate proves the
 * three rack surfaces actually ride it (the v1.321.0 two-proof law).
 */
class PhosphorTipTest {

    @Test
    @DisplayName("the seam styles a tip in the phosphor face, visibly bordered")
    void seamStyles() {
        JToolTip tip = RackStyle.phosphorTip(new JToolTip());
        assertThat(tip.getBackground()).isEqualTo(RackStyle.LCD_BG);
        assertThat(tip.getForeground()).isEqualTo(RackStyle.LCD_TEXT);
        assertThat(tip.isOpaque()).isTrue();
        assertThat(tip.getFont()).isEqualTo(RackStyle.LCD_FONT);
        assertThat(tip.getBorder())
                .as("the visible bezel is the point — an unbordered dark tip"
                        + " over a dark faceplate is how this bug hid for 60 releases")
                .isNotNull();
    }

    @Test
    @DisplayName("the widgets' createToolTip actually returns the styled tip")
    void widgetsRideTheSeam() {
        assertThat(new LcdDisplay(120, 1).createToolTip().getBackground())
                .isEqualTo(RackStyle.LCD_BG);
        assertThat(new RackButton("GO", RackStyle.LCD_TEXT).createToolTip().getBackground())
                .isEqualTo(RackStyle.LCD_BG);
    }

    @Test
    @DisplayName("all three rack surfaces are wired (RackDevice by source — it needs a live rack)")
    void allSurfacesWired() throws Exception {
        for (String path : new String[] {
            "src/main/java/org/nmox/studio/rack/ui/controls/LcdDisplay.java",
            "src/main/java/org/nmox/studio/rack/ui/controls/RackButton.java",
            "src/main/java/org/nmox/studio/rack/model/RackDevice.java"}) {
            assertThat(Files.readString(new File(path).toPath()))
                    .as("%s must override createToolTip through the seam", path)
                    .contains("phosphorTip(super.createToolTip())");
        }
    }
}
