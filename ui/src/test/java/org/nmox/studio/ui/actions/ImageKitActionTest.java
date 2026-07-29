package org.nmox.studio.ui.actions;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.ImagePress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Image Kit's pure report surface: the dialog-choice mappings
 * (quality index → JPEG quality, downscale index → max width), the
 * per-file report line (written sibling with sizes, or the honest
 * refusal note verbatim), and the human size formatting the whole
 * report leans on.
 */
class ImageKitActionTest {

    @Test
    @DisplayName("The quality combo maps 85 / 80 / 70, defaulting to the web default")
    void qualityMapping() {
        assertThat(ImageKitAction.qualityFor(0)).isEqualTo(0.85f);
        assertThat(ImageKitAction.qualityFor(1)).isEqualTo(0.80f);
        assertThat(ImageKitAction.qualityFor(2)).isEqualTo(0.70f);
        // anything unexpected falls to the web default, never a crash
        assertThat(ImageKitAction.qualityFor(-1)).isEqualTo(0.80f);
        assertThat(ImageKitAction.qualityFor(99)).isEqualTo(0.80f);
    }

    @Test
    @DisplayName("The downscale combo maps no-resize / 2560 / 1600 / 800")
    void maxWidthMapping() {
        assertThat(ImageKitAction.maxWidthFor(0)).isZero();
        assertThat(ImageKitAction.maxWidthFor(1)).isEqualTo(2560);
        assertThat(ImageKitAction.maxWidthFor(2)).isEqualTo(1600);
        assertThat(ImageKitAction.maxWidthFor(3)).isEqualTo(800);
        // an unknown index means no resize — the safe choice
        assertThat(ImageKitAction.maxWidthFor(7)).isZero();
    }

    @Test
    @DisplayName("Sizes read as MB above a megabyte and KB below, never showing 0 KB")
    void humanSizes() {
        assertThat(ImageKitAction.mb(17_800_000L)).isEqualTo("17.8 MB");
        assertThat(ImageKitAction.mb(1_000_000L)).isEqualTo("1.0 MB");
        assertThat(ImageKitAction.mb(347_000L)).isEqualTo("347 KB");
        // a tiny-but-real file must not report as "0 KB"
        assertThat(ImageKitAction.mb(12L)).isEqualTo("1 KB");
    }

    @Test
    @DisplayName("A written press reports the sibling's name with before → after sizes")
    void lineForWrittenOutput() {
        ImagePress.Candidate c = new ImagePress.Candidate(new File("hero.jpg"), 17_800_000L);
        ImagePress.Result r = new ImagePress.Result(new File("hero.jpg"),
                new File("hero.min.jpg"), 17_800_000L, 164_000L, "written");

        assertThat(ImageKitAction.line(c, r))
                .isEqualTo("hero.jpg → hero.min.jpg (17.8 MB → 164 KB)\n");
    }

    @Test
    @DisplayName("A press that wrote nothing reports the refusal note verbatim")
    void lineForRefusal() {
        ImagePress.Candidate c = new ImagePress.Candidate(new File("tight.png"), 42_000L);
        ImagePress.Result r = new ImagePress.Result(new File("tight.png"),
                null, 42_000L, 41_000L, "already tight (under 10% savings)");

        assertThat(ImageKitAction.line(c, r))
                .isEqualTo("tight.png → already tight (under 10% savings)\n");
    }
}
