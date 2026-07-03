package org.nmox.studio.rack.projectstudio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The icon renderer, held to the maskable spec: standard icons keep
 * transparent rounded corners, maskable icons bleed to the edge, and
 * every output is a real PNG at its declared size.
 */
class IconForgeTest {

    private static final IconForge.Monogram MONO
            = new IconForge.Monogram("N", new Color(0x1a1a1e), Color.WHITE);

    @Test
    @DisplayName("Rendered icons round-trip through PNG at their declared size")
    void pngRoundTrip(@TempDir Path tmp) throws Exception {
        for (int size : new int[]{192, 512, 180}) {
            File out = tmp.resolve("icon-" + size + ".png").toFile();
            IconForge.writePng(IconForge.render(MONO, size, false), out);
            BufferedImage back = ImageIO.read(out);
            assertThat(back.getWidth()).isEqualTo(size);
            assertThat(back.getHeight()).isEqualTo(size);
        }
    }

    @Test
    @DisplayName("Standard icons have transparent corners; maskable icons bleed to the edge")
    void cornersFollowTheSpec() {
        BufferedImage standard = IconForge.render(MONO, 192, false);
        BufferedImage maskable = IconForge.render(MONO, 192, true);
        int cornerAlphaStandard = (standard.getRGB(1, 1) >>> 24);
        int cornerAlphaMaskable = (maskable.getRGB(1, 1) >>> 24);
        assertThat(cornerAlphaStandard).as("rounded corner is transparent").isZero();
        assertThat(cornerAlphaMaskable).as("maskable is full-bleed").isEqualTo(0xFF);
    }

    @Test
    @DisplayName("The monogram actually draws: center pixels differ from the plate")
    void monogramDraws() {
        BufferedImage img = IconForge.render(MONO, 192, false);
        int center = img.getRGB(96, 96) & 0xFFFFFF;
        assertThat(center).as("white glyph over the dark plate")
                .isNotEqualTo(0x1a1a1e);
    }

    @Test
    @DisplayName("Artwork mode scales source images onto the plate")
    void artworkScales(@TempDir Path tmp) throws Exception {
        BufferedImage source = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1024, 1024);
        g.dispose();
        File file = tmp.resolve("logo.png").toFile();
        ImageIO.write(source, "png", file);

        IconForge.Artwork art = new IconForge.Artwork(IconForge.read(file), Color.BLACK);
        BufferedImage standard = IconForge.render(art, 192, false);
        assertThat(standard.getRGB(96, 96) & 0xFFFFFF)
                .as("standard: art fills the plate").isEqualTo(0xFF0000);
        BufferedImage maskable = IconForge.render(art, 192, true);
        assertThat(maskable.getRGB(96, 96) & 0xFFFFFF)
                .as("maskable: art still covers the center").isEqualTo(0xFF0000);
        assertThat(maskable.getRGB(4, 4) & 0xFFFFFF)
                .as("maskable: safe-zone margin shows the background").isEqualTo(0x000000);
    }

    @Test
    @DisplayName("Hex colors parse in #rgb and #rrggbb; garbage falls back")
    void hexParsing() {
        assertThat(IconForge.color("#1a1a1e", Color.RED)).isEqualTo(new Color(0x1a1a1e));
        assertThat(IconForge.color("#f0a", Color.RED)).isEqualTo(new Color(0xff00aa));
        assertThat(IconForge.color("teal-ish", Color.RED)).isEqualTo(Color.RED);
        assertThat(IconForge.color(null, Color.RED)).isEqualTo(Color.RED);
        assertThat(IconForge.color("  #ffffff  ", Color.RED)).isEqualTo(Color.WHITE);
    }
}
