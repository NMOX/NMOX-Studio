package org.nmox.studio.rack.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shelf's cards mirror by hand (v2.151.0).
 *
 * <p>A cell renderer is never in the component tree, so the orientation sweep
 * cannot reach it, and a card computes its own x coordinates, so the flag
 * would change nothing even if it did. The card takes the list's orientation
 * and starts every line at the reader's line start — asserted here on the
 * pixels a Hebrew reader would see, not on a field.
 */
class PaletteCardDirectionTest {

    @Test
    @DisplayName("a card line starts inset from the reader's line start")
    void lineStartMirrors() {
        assertThat(PalettePanel.lineStart(true, 210, 20, 50)).isEqualTo(20);
        assertThat(PalettePanel.lineStart(false, 210, 20, 50))
                .as("right to left: the run ends 20px in from the right edge").isEqualTo(140);
    }

    @Test
    @DisplayName("in a right-to-left shelf the card's accent edge is painted on the right")
    void theAccentEdgeFollowsTheReader() {
        PalettePanel shelf = new PalettePanel(new Rack());
        JList<?> list = find(shelf, JList.class);
        assertThat(list).as("the shelf's list").isNotNull();

        int index = -1;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i) instanceof DeviceCatalog.Entry) {
                index = i;
                break;
            }
        }
        assertThat(index).as("a device card on the shelf").isNotNegative();
        Color accent = ((DeviceCatalog.Entry) list.getModel().getElementAt(index)).accent();

        BufferedImage ltr = paintCard(list, index, ComponentOrientation.LEFT_TO_RIGHT);
        BufferedImage rtl = paintCard(list, index, ComponentOrientation.RIGHT_TO_LEFT);

        assertThat(near(ltr.getRGB(8, 26), accent)).as("left to right: accent at the left edge").isTrue();
        assertThat(near(ltr.getRGB(201, 26), accent)).as("and not at the right").isFalse();
        assertThat(near(rtl.getRGB(201, 26), accent)).as("right to left: accent at the right edge").isTrue();
        assertThat(near(rtl.getRGB(8, 26), accent)).as("and not at the left").isFalse();
    }

    private static BufferedImage paintCard(JList<?> list, int index, ComponentOrientation o) {
        list.setComponentOrientation(o);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Component card = ((javax.swing.ListCellRenderer) list.getCellRenderer())
                .getListCellRendererComponent(list, list.getModel().getElementAt(index), index, false, false);
        card.setSize(210, 52);
        BufferedImage img = new BufferedImage(210, 52, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        card.paint(g);
        g.dispose();
        return img;
    }

    private static boolean near(int argb, Color c) {
        Color p = new Color(argb, true);
        return Math.abs(p.getRed() - c.getRed()) < 24
                && Math.abs(p.getGreen() - c.getGreen()) < 24
                && Math.abs(p.getBlue() - c.getBlue()) < 24;
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container k) {
                T hit = find(k, type);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
