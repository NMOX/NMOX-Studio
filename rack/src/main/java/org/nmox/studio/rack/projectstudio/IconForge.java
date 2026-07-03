package org.nmox.studio.rack.projectstudio;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Renders PWA icons with plain Java2D - no external tools, no network.
 * Two sources: a monogram (one or two letters on a colored plate) for
 * projects that don't have art yet, or an existing image scaled to
 * every required size. Standard icons get a rounded plate with
 * transparent corners; maskable icons are full-bleed with the glyph
 * held inside the safe zone, per the W3C maskable icon spec.
 */
public final class IconForge {

    private IconForge() {
    }

    /** Corner radius of the standard plate, as a fraction of the size. */
    private static final double CORNER = 0.22;
    /** Glyph scale on standard icons. */
    private static final double GLYPH = 0.52;
    /** Glyph scale on maskable icons - inside the 80% safe zone. */
    private static final double GLYPH_MASKABLE = 0.40;

    /** What to draw: a monogram, or a source image to scale. */
    public sealed interface Source {
    }

    public record Monogram(String text, Color background, Color foreground) implements Source {
    }

    public record Artwork(BufferedImage image, Color background) implements Source {
    }

    /**
     * Parses {@code #rgb} or {@code #rrggbb}; anything unparseable
     * falls back so a typo in the wizard can't sink the whole run.
     */
    public static Color color(String hex, Color fallback) {
        if (hex == null) {
            return fallback;
        }
        String h = hex.strip();
        if (h.matches("#[0-9a-fA-F]{3}")) {
            h = "#" + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2)
                    + h.charAt(3) + h.charAt(3);
        }
        try {
            return Color.decode(h);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Renders one icon. Maskable and apple-touch icons are full-bleed
     * (apple-touch because iOS composites its own corners onto opaque
     * art); standard icons keep transparent rounded corners.
     */
    public static BufferedImage render(Source source, int size, boolean fullBleed) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (source instanceof Monogram m) {
                paintPlate(g, size, m.background(), fullBleed);
                paintGlyph(g, size, m, fullBleed);
            } else if (source instanceof Artwork a) {
                paintPlate(g, size, a.background(), fullBleed);
                // maskable art stays inside the safe zone; standard art fills the plate
                int art = fullBleed ? (int) Math.round(size * 0.72) : size;
                Image scaled = smoothScale(a.image(), art);
                int off = (size - art) / 2;
                g.drawImage(scaled, off, off, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void paintPlate(Graphics2D g, int size, Color background, boolean fullBleed) {
        g.setColor(background);
        if (fullBleed) {
            g.fillRect(0, 0, size, size);
        } else {
            int r = (int) Math.round(size * CORNER);
            g.fillRoundRect(0, 0, size, size, r, r);
        }
    }

    private static void paintGlyph(Graphics2D g, int size, Monogram m, boolean fullBleed) {
        String text = m.text() == null || m.text().isBlank() ? "•"
                : m.text().strip().substring(0, Math.min(2, m.text().strip().length()))
                        .toUpperCase(Locale.ROOT);
        double scale = fullBleed ? GLYPH_MASKABLE : GLYPH;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD,
                (int) Math.round(size * scale / (text.length() > 1 ? 1.5 : 1.0)));
        g.setFont(font);
        g.setColor(m.foreground());
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(text)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
    }

    /** Progressive halving keeps downscaled artwork crisp. */
    private static Image smoothScale(BufferedImage src, int target) {
        BufferedImage current = src;
        while (current.getWidth() / 2 >= target && current.getHeight() / 2 >= target) {
            BufferedImage half = new BufferedImage(current.getWidth() / 2,
                    current.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            current = half;
        }
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(current, 0, 0, target, target, null);
        g.dispose();
        return out;
    }

    public static void writePng(BufferedImage image, File target) throws IOException {
        if (!ImageIO.write(image, "png", target)) {
            throw new IOException("No PNG writer available");
        }
    }

    /** Loads source artwork for the {@link Artwork} mode. */
    public static BufferedImage read(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Not a readable image: " + file.getName());
        }
        return img;
    }
}
