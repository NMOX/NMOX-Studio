package org.nmox.studio.infra.ops.ui;

import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The camera's rules, as arithmetic: what stays put, what is clamped, what a fit does and does not do. */
class HudCameraTest {

    private static final Rectangle2D CONTENT = new Rectangle2D.Double(100, 80, 600, 300);

    @Test
    @DisplayName("an untouched camera is the identity: the picture is exactly the layout's")
    void identity() {
        HudCamera c = new HudCamera();
        assertThat(c.identity()).isTrue();
        assertThat(c.manual()).isFalse();
        assertThat(c.sx(123.5)).isEqualTo(123.5);
        assertThat(c.sy(-7)).isEqualTo(-7);
        assertThat(c.wx(c.sx(42))).isEqualTo(42);
    }

    @Test
    @DisplayName("a zoom keeps the point under the cursor exactly where it was")
    void zoomKeepsTheAnchor() {
        HudCamera c = new HudCamera();
        c.panBy(37, -12, false);
        double wx = c.wx(300);
        double wy = c.wy(200);
        assertThat(c.zoomAbout(300, 200, 1.6, false)).isTrue();
        assertThat(c.zoom()).isCloseTo(1.6, within(1e-9));
        assertThat(c.sx(wx)).isCloseTo(300, within(1e-6));
        assertThat(c.sy(wy)).isCloseTo(200, within(1e-6));
        assertThat(c.manual()).isTrue();
        // and back out again lands where it began
        c.zoomAbout(300, 200, 1 / 1.6, false);
        assertThat(c.zoom()).isCloseTo(1.0, within(1e-9));
        assertThat(c.panX()).isCloseTo(37, within(1e-6));
    }

    @Test
    @DisplayName("the anchor holds through the animation, not only at its end")
    void anchorHoldsWhileAnimating() {
        HudCamera c = new HudCamera();
        double wx = c.wx(500);
        double wy = c.wy(120);
        c.zoomAbout(500, 120, 2.0, true);
        assertThat(c.settled()).isFalse();
        for (int i = 0; i < 400 && !c.settled(); i++) {
            c.step(0.016);
            assertThat(c.sx(wx)).as("frame " + i).isCloseTo(500, within(1.0));
            assertThat(c.sy(wy)).as("frame " + i).isCloseTo(120, within(1.0));
        }
        assertThat(c.settled()).as("the spring arrives").isTrue();
        assertThat(c.zoom()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    @DisplayName("two quick presses compound: the second works from where the first is heading")
    void pressesCompound() {
        HudCamera c = new HudCamera();
        c.zoomAbout(400, 300, HudCamera.KEY_STEP, true);
        c.zoomAbout(400, 300, HudCamera.KEY_STEP, true);
        assertThat(c.targetZoom()).isCloseTo(HudCamera.KEY_STEP * HudCamera.KEY_STEP, within(1e-9));
    }

    @Test
    @DisplayName("zoom stops at its floor and its ceiling and says so")
    void zoomIsBounded() {
        HudCamera c = new HudCamera();
        for (int i = 0; i < 40; i++) {
            c.zoomAbout(10, 10, 1.5, false);
        }
        assertThat(c.zoom()).isEqualTo(HudCamera.MAX_ZOOM);
        assertThat(c.zoomAbout(10, 10, 1.5, false)).as("already at the ceiling").isFalse();
        for (int i = 0; i < 40; i++) {
            c.zoomAbout(10, 10, 0.5, false);
        }
        assertThat(c.zoom()).isEqualTo(HudCamera.MIN_ZOOM);
        assertThat(c.zoomAbout(10, 10, 0.5, false)).as("already at the floor").isFalse();
    }

    @Test
    @DisplayName("a fit leaves content that already fits exactly as the layout placed it")
    void fitNeverEnlarges() {
        HudCamera c = new HudCamera();
        c.zoomAbout(200, 200, 2, false);
        c.fit(CONTENT, 900, 600, false);
        assertThat(c.identity()).isTrue();
        assertThat(c.zoom()).isEqualTo(1.0);
        assertThat(c.manual()).as("a fit hands the camera back to the layout").isFalse();
    }

    @Test
    @DisplayName("a fit shrinks content that overflows until all of it is inside, labels' room included")
    void fitShrinksOverflow() {
        HudCamera c = new HudCamera();
        Rectangle2D big = new Rectangle2D.Double(-200, -100, 1400, 900);
        c.fit(big, 800, 500, false);
        assertThat(c.zoom()).isLessThan(1.0).isGreaterThan(HudCamera.MIN_ZOOM);
        assertThat(c.sx(big.getMinX())).isGreaterThanOrEqualTo(HudCamera.PAD_X - 1e-6);
        assertThat(c.sx(big.getMaxX())).isLessThanOrEqualTo(800 - HudCamera.PAD_X + 1e-6);
        assertThat(c.sy(big.getMinY())).isGreaterThanOrEqualTo(HudCamera.PAD_TOP - 1e-6);
        assertThat(c.sy(big.getMaxY())).isLessThanOrEqualTo(500 - HudCamera.PAD_BOTTOM + 1e-6);
    }

    @Test
    @DisplayName("content too large even at the floor is centred at the floor, not shrunk past legibility")
    void fitStopsAtTheFloor() {
        HudCamera c = new HudCamera();
        Rectangle2D vast = new Rectangle2D.Double(0, 0, 9000, 6000);
        c.fit(vast, 800, 500, false);
        assertThat(c.zoom()).isEqualTo(HudCamera.MIN_ZOOM);
        assertThat(c.sx(vast.getCenterX())).as("its middle is the canvas's middle").isCloseTo(400, within(1.0));
    }

    @Test
    @DisplayName("a fit of nothing, or into no canvas, is the identity and divides by nothing")
    void fitOfNothing() {
        HudCamera c = new HudCamera();
        c.fit(null, 800, 500, false);
        assertThat(c.identity()).isTrue();
        c.fit(CONTENT, 0, 0, false);
        assertThat(c.identity()).isTrue();
        assertThat(c.zoom()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a pan cannot lose the content: part of it stays on screen on every side")
    void clampKeepsContentReachable() {
        HudCamera c = new HudCamera();
        c.panBy(-5000, 0, false);
        c.clamp(CONTENT, 900, 600);
        assertThat(c.sx(CONTENT.getMaxX())).as("dragged off the left").isCloseTo(HudCamera.KEEP, within(1e-6));
        c.panBy(10000, 0, false);
        c.clamp(CONTENT, 900, 600);
        assertThat(c.sx(CONTENT.getMinX())).as("dragged off the right").isCloseTo(900 - HudCamera.KEEP, within(1e-6));
        c.panBy(0, -9000, false);
        c.clamp(CONTENT, 900, 600);
        assertThat(c.sy(CONTENT.getMaxY())).as("dragged off the top").isCloseTo(HudCamera.KEEP, within(1e-6));
        c.panBy(0, 20000, false);
        c.clamp(CONTENT, 900, 600);
        assertThat(c.sy(CONTENT.getMinY())).as("dragged off the bottom").isCloseTo(600 - HudCamera.KEEP, within(1e-6));
        // and a camera that is already fine is not moved
        HudCamera still = new HudCamera();
        still.panBy(15, 20, false);
        still.clamp(CONTENT, 900, 600);
        assertThat(still.panX()).isEqualTo(15);
        assertThat(still.panY()).isEqualTo(20);
    }

    @Test
    @DisplayName("revealing a node moves the least it must, and nothing when the node is already in view")
    void reveal() {
        HudCamera c = new HudCamera();
        assertThat(c.reveal(400, 300, 30, 900, 600, false)).as("already visible").isFalse();
        assertThat(c.panX()).isZero();
        assertThat(c.reveal(1200, 300, 30, 900, 600, false)).isTrue();
        assertThat(c.sx(1200) + 30).as("its right edge is inside the margin").isLessThanOrEqualTo(900 - 36 + 1e-6);
        assertThat(c.panY()).as("the axis that was fine is untouched").isZero();
        assertThat(c.manual()).as("revealing is the HUD's move, not the user's").isFalse();
    }

    @Test
    @DisplayName("what a fit calls in view, a reveal calls in view: a framed node is never nudged")
    void revealAgreesWithFit() {
        // natural size: content the fit accepts as it lies (inside the fit's own padding) is the identity,
        // and the node at its edge must not be asked for a wider margin than the fit gave it
        HudCamera c = new HudCamera();
        java.awt.geom.Rectangle2D inside = new java.awt.geom.Rectangle2D.Double(
                HudCamera.PAD_X, HudCamera.PAD_TOP, 200, 120);
        c.fit(inside, 420, 300, false);
        assertThat(c.identity()).isTrue();
        assertThat(c.reveal(HudCamera.PAD_X + 20, HudCamera.PAD_TOP + 20, 20, 420, 300, false))
                .as("the node at the framed content's corner is in view").isFalse();
        assertThat(c.identity()).as("an untouched camera is still the identity after the keyboard walked it").isTrue();
        // zoomed to fit: the same, at the zoom the fit chose, for all four corners
        HudCamera z = new HudCamera();
        z.fit(CONTENT, 420, 300, false);
        double px = z.panX();
        double py = z.panY();
        double r = 30;
        double[][] corners = {
            {CONTENT.getMinX() + r, CONTENT.getMinY() + r}, {CONTENT.getMaxX() - r, CONTENT.getMinY() + r},
            {CONTENT.getMinX() + r, CONTENT.getMaxY() - r}, {CONTENT.getMaxX() - r, CONTENT.getMaxY() - r}};
        for (double[] corner : corners) {
            assertThat(z.reveal(corner[0], corner[1], r, 420, 300, false)).as("corner %s,%s", corner[0], corner[1]).isFalse();
        }
        assertThat(z.panX()).isEqualTo(px);
        assertThat(z.panY()).isEqualTo(py);
    }
}
