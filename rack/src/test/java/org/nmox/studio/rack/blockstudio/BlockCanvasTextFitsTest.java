package org.nmox.studio.rack.blockstudio;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A piece's two lines fit its row on every system.
 *
 * <p>A row is 34 pixels tall and the canvas draws a piece's kind and its
 * face at two fixed baselines twelve pixels apart. It drew both in whatever
 * font the component happened to have, which is the look and feel's default:
 * 13 points on a Mac, where the lines sit comfortably, and a taller font on
 * Linux, where the first Linux walk (3.5) photographed "Component" and
 * "&lt;my-widget&gt;" printed on top of each other. Fixed geometry needs
 * fixed type, which is the rule every faceplate in the rack already follows.
 */
class BlockCanvasTextFitsTest {

    private static BlockCanvas canvas() throws Exception {
        BlockDoc doc = new BlockDoc();
        Block div = doc.create(BlockKind.ELEMENT);
        div.setParam("tag", "div");
        doc.insert(doc.root(), div, 0);
        BlockCanvas[] canvas = new BlockCanvas[1];
        SwingUtilities.invokeAndWait(() -> {
            canvas[0] = new BlockCanvas(new BlockCanvas.Host() {
                @Override
                public void aboutToChange() {
                }

                @Override
                public void changed() {
                }

                @Override
                public void selected(Block block) {
                }

                @Override
                public void editParams(Block block) {
                }
            });
            canvas[0].setDoc(doc);
            canvas[0].setSize(420, 160);
        });
        return canvas[0];
    }

    private static int[] paintedWith(BlockCanvas canvas, float componentFontSize) throws Exception {
        BufferedImage img = new BufferedImage(420, 160, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            canvas.setFont(new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont(componentFontSize));
            Graphics2D g = img.createGraphics();
            try {
                canvas.paint(g);
            } finally {
                g.dispose();
            }
        });
        return img.getRGB(0, 0, 420, 160, null, 0, 420);
    }

    @Test
    @DisplayName("the canvas paints the same picture whatever size the system's default font is")
    void theDefaultFontSizeDoesNotReachTheRows() throws Exception {
        BlockCanvas canvas = canvas();
        int[] asOnAMac = paintedWith(canvas, 13f);
        int[] asOnLinux = paintedWith(canvas, 17f);
        assertThat(Arrays.equals(asOnAMac, asOnLinux))
                .as("a piece's text is drawn in the canvas's own fixed sizes").isTrue();
    }

    @Test
    @DisplayName("the two lines are sized for a 34-pixel row")
    void theTypeIsSizedForTheRow() {
        Font any = new Font(Font.DIALOG, Font.PLAIN, 19);
        assertThat(BlockCanvas.kindFont(any).getSize2D()).isEqualTo(12f);
        assertThat(BlockCanvas.kindFont(any).isBold()).as("the kind reads as the piece's title").isTrue();
        assertThat(BlockCanvas.faceFont(any).getSize2D()).isEqualTo(11f);
        assertThat(BlockCanvas.faceFont(any).getFamily()).as("the family stays the system's").isEqualTo(any.getFamily());
    }
}
