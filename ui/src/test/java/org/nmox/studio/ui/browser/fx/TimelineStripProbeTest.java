package org.nmox.studio.ui.browser.fx;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The strip driven for real (v2.12.0, the BlockCanvasKeyboardTest
 * idiom): synthesized mouse events through the actual listener — a
 * ruler press scrubs, a diamond press selects, a drag moves the stop
 * through the SAME clamped model path as the UI, a double-click on an
 * empty track adds a stop seeded from its neighbor, and Delete removes
 * the selection. Painting goes to a headless BufferedImage — the strip
 * is a painted widget, so the paint path is exercised, not excluded.
 */
class TimelineStripProbeTest {

    private static TimelineStrip strip(List<Integer> scrubs, AtomicInteger changes) {
        TimelineStrip s = new TimelineStrip(scrubs::add, changes::incrementAndGet,
                (prop, pct) -> { });
        s.setSize(520, 100);
        return s;
    }

    private static void press(TimelineStrip s, int x, int y, int clicks) {
        MouseEvent e = new MouseEvent(s, MouseEvent.MOUSE_PRESSED,
                0L, 0, x, y, clicks, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : s.getMouseListeners()) {
            l.mousePressed(e);
        }
    }

    private static void drag(TimelineStrip s, int x, int y) {
        MouseEvent e = new MouseEvent(s, MouseEvent.MOUSE_DRAGGED,
                0L, 0, x, y, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseMotionListener l : s.getMouseMotionListeners()) {
            l.mouseDragged(e);
        }
    }

    private static void release(TimelineStrip s, int x, int y) {
        MouseEvent e = new MouseEvent(s, MouseEvent.MOUSE_RELEASED,
                0L, 0, x, y, 1, false, MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : s.getMouseListeners()) {
            l.mouseReleased(e);
        }
    }

    /** The x pixel of a percent, mirroring the strip's own geometry. */
    private static int xOf(TimelineStrip s, int percent) {
        int w = s.getWidth() - 120 - 12;
        return 120 + 6 + Math.round(percent / 100f * w);
    }

    @Test
    @DisplayName("a ruler press scrubs; a diamond drag moves the stop through the clamped model")
    void scrubAndDrag() {
        List<Integer> scrubs = new ArrayList<>();
        AtomicInteger changes = new AtomicInteger();
        TimelineStrip s = strip(scrubs, changes);
        s.model().setStop("opacity", 0, "0");
        s.model().setStop("opacity", 50, "0.5");
        s.model().setStop("opacity", 100, "1");
        s.refresh();

        press(s, xOf(s, 30), 8, 1);
        assertThat(scrubs).isNotEmpty();

        int trackY = 18 + 24 / 2;
        press(s, xOf(s, 50), trackY, 1);
        assertThat(s.selectedProperty()).isEqualTo("opacity");
        assertThat(s.selectedPercent()).isEqualTo(50);
        drag(s, xOf(s, 200), trackY);
        release(s, xOf(s, 200), trackY);
        // clamped strictly below the 100% neighbor
        assertThat(s.model().stops("opacity")).containsKeys(0, 99, 100);
        assertThat(changes.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("double-click on an empty track adds a stop seeded from its neighbor; paint renders headless")
    void addStopAndPaint() {
        List<Integer> scrubs = new ArrayList<>();
        AtomicInteger changes = new AtomicInteger();
        TimelineStrip s = strip(scrubs, changes);
        s.model().addTrack("transform");
        s.model().setStop("transform", 0, "translateX(0)");
        s.refresh();

        int trackY = 18 + 24 / 2;
        press(s, xOf(s, 70), trackY, 2);
        assertThat(s.model().stops("transform")).containsKey(70);
        assertThat(s.model().stops("transform").get(70)).isEqualTo("translateX(0)");

        BufferedImage img = new BufferedImage(520, 100, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        s.paint(g);
        g.dispose();
        // the phosphor diamond really painted somewhere on the track row
        boolean phosphor = false;
        for (int x = 0; x < img.getWidth() && !phosphor; x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgb = img.getRGB(x, y);
                if (((rgb >> 8) & 0xFF) > 0xB0 && ((rgb >> 16) & 0xFF) < 0x80) {
                    phosphor = true;
                    break;
                }
            }
        }
        assertThat(phosphor).as("a keyframe diamond painted in phosphor green").isTrue();

        // an empty strip paints its hint without throwing
        TimelineStrip empty = strip(new ArrayList<>(), new AtomicInteger());
        empty.paint(img.createGraphics());
    }
}
