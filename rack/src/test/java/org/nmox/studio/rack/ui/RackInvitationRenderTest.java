package org.nmox.studio.rack.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.RackStyle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack says where devices come from, on the rack a new user actually
 * sees (v2.118.0, the coherence pass).
 *
 * <p>The silkscreen naming the two doors — the shelf and the Presets menu —
 * has been painted since the rack shipped, but only when
 * {@code getDevices().isEmpty()}. Since v1.278.0 a project with no saved
 * patch resets to a STARTER rack holding one MONITOR, so that condition was
 * false from the first launch and the line had never been seen: the first
 * open of the product's signature window showed one console and eleven bare
 * rack units explaining nothing. It now paints in whatever bare rail is left
 * below the stack, and retires once the rack is full.
 *
 * <p>Painted, not asserted from the source: the probe renders the real panel
 * to an image and counts lit pixels in the bare-rail band between the rails.
 */
class RackInvitationRenderTest {

    /** The panel's own preferred width: narrower and the rails fall off the image. */
    private static final int WIDTH = RackStyle.RACK_WIDTH + 72;

    private static final class OneUnit extends RackDevice {
        OneUnit() {
            super("probe", "PROBE", "PROBE", new Color(40, 40, 40), 1);
            addOutPort("out", "OUT", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
        }
    }

    /** Pixels brighter than the rack's dark ground, in the band below the stack. */
    private static int litBelowStack(RackPanel panel, int width, int height) throws Exception {
        final int[] lit = {0};
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(width, height);
            panel.doLayout();
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g);
            g.dispose();
            int bottom = panel.stackBottom();
            // only between the rails: the rails and their cage nuts are lit too
            int railX1 = (width - RackStyle.RACK_WIDTH) / 2 - 10;
            int railX2 = railX1 + RackStyle.RACK_WIDTH + 20;
            int count = 0;
            for (int y = bottom + 2; y < height; y++) {
                for (int x = Math.max(0, railX1 + 14); x < Math.min(width, railX2 - 14); x++) {
                    Color c = new Color(img.getRGB(x, y));
                    if (c.getRed() + c.getGreen() + c.getBlue() > 150) {
                        count++;
                    }
                }
            }
            lit[0] = count;
        });
        return lit[0];
    }

    @Test
    @DisplayName("a rack with the starter device still shows where devices come from")
    void starterRackInvites() throws Exception {
        Rack rack = new Rack();
        rack.addDevice(new OneUnit());
        RackPanel panel = new RackPanel(rack);
        int tall = RackStyle.UNIT * 10;
        assertThat(litBelowStack(panel, WIDTH, tall))
                .as("the silkscreen line painted on the bare rail below one mounted device")
                .isGreaterThan(50);
    }

    @Test
    @DisplayName("a rack with no bare rail left says nothing — the line retires as it fills")
    void fullRackIsQuiet() throws Exception {
        Rack rack = new Rack();
        rack.addDevice(new OneUnit());
        RackPanel panel = new RackPanel(rack);
        final int[] bottom = {0};
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(WIDTH, RackStyle.UNIT * 10);
            panel.doLayout();
            bottom[0] = panel.stackBottom();
        });
        // exactly one rack unit of bare rail: less than the two the line needs
        int snug = bottom[0] + RackStyle.UNIT;
        assertThat(litBelowStack(panel, WIDTH, snug))
                .as("no room for the line, so no line")
                .isZero();
    }
}
