import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generates every piece of NMOX Studio branding art from code, so the
 * splash and icons are reproducible and tweakable without a designer
 * round trip. Run from the repository root:
 *
 *   java packaging/tools/BrandingArtGenerator.java
 *
 * Outputs:
 *   branding/.../core/core.jar/org/netbeans/core/startup/splash.gif
 *   branding/.../core/core.jar/org/netbeans/core/startup/frame.gif (+32/48)
 *   packaging/icons/nmox-studio-{16..1024}.png
 *   packaging/icons/nmox-studio.iconset/   (feed to iconutil on macOS)
 */
public final class BrandingArtGenerator {

    private static final Color BG_TOP = new Color(30, 30, 34);
    private static final Color BG_BOTTOM = new Color(20, 20, 23);
    private static final Color RAIL = new Color(54, 54, 58);
    private static final Color ACCENT = new Color(232, 116, 34);   // NMOX orange
    private static final Color ACCENT_2 = new Color(64, 156, 255); // cable blue
    private static final Color ACCENT_3 = new Color(99, 197, 70);  // LED green
    private static final Color TEXT = new Color(230, 231, 235);
    private static final Color TEXT_DIM = new Color(150, 152, 158);

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        File startup = new File(root,
                "branding/src/main/nbm-branding/core/core.jar/org/netbeans/core/startup");
        File icons = new File(root, "packaging/icons");
        File iconset = new File(icons, "nmox-studio.iconset");
        startup.mkdirs();
        icons.mkdirs();
        iconset.mkdirs();

        ImageIO.write(splash(500, 300), "gif", new File(startup, "splash.gif"));

        int[] frameSizes = {16, 32, 48};
        String[] frameNames = {"frame.gif", "frame32.gif", "frame48.gif"};
        for (int i = 0; i < frameSizes.length; i++) {
            ImageIO.write(icon(frameSizes[i]), "gif", new File(startup, frameNames[i]));
        }

        for (int size : new int[]{16, 32, 48, 64, 128, 256, 512, 1024}) {
            ImageIO.write(icon(size), "png",
                    new File(icons, "nmox-studio-" + size + ".png"));
        }
        // Apple iconset naming for iconutil
        int[][] appleSizes = {{16, 1}, {16, 2}, {32, 1}, {32, 2}, {128, 1}, {128, 2}, {256, 1}, {256, 2}, {512, 1}, {512, 2}};
        for (int[] s : appleSizes) {
            int px = s[0] * s[1];
            String name = "icon_" + s[0] + "x" + s[0] + (s[1] == 2 ? "@2x" : "") + ".png";
            ImageIO.write(icon(px), "png", new File(iconset, name));
        }
        System.out.println("branding art generated");
    }

    // ---- the app icon: a rack device faceplate with a patched cable ----

    private static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float u = size / 64f; // design units on a 64px grid

        // rounded faceplate
        RoundRectangle2D plate = new RoundRectangle2D.Float(2 * u, 2 * u, 60 * u, 60 * u, 14 * u, 14 * u);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, size, BG_BOTTOM));
        g.fill(plate);

        // accent stripe across the top
        g.setClip(plate);
        g.setPaint(new GradientPaint(0, 2 * u, ACCENT, 0, 10 * u, ACCENT.darker()));
        g.fillRect(Math.round(2 * u), Math.round(2 * u), Math.round(60 * u), Math.round(8 * u));

        // two knobs
        paintKnob(g, 17 * u, 24 * u, 7.5f * u, 220);
        paintKnob(g, 47 * u, 24 * u, 7.5f * u, -40);

        // LED
        g.setColor(ACCENT_3);
        g.fill(new Ellipse2D.Float(29.5f * u, 21 * u, 5 * u, 5 * u));
        g.setColor(new Color(ACCENT_3.getRed(), ACCENT_3.getGreen(), ACCENT_3.getBlue(), 70));
        g.fill(new Ellipse2D.Float(27.5f * u, 19 * u, 9 * u, 9 * u));

        // two jacks at the bottom, one cable sagging between them
        paintJack(g, 16 * u, 47 * u, 5 * u);
        paintJack(g, 48 * u, 47 * u, 5 * u);
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(4.4f * u / 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        CubicCurve2D shadow = new CubicCurve2D.Float(16 * u, 48.5f * u, 24 * u, 60 * u, 40 * u, 60 * u, 48 * u, 48.5f * u);
        g.draw(shadow);
        g.setColor(ACCENT_2);
        g.setStroke(new BasicStroke(4f * u / 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        CubicCurve2D cable = new CubicCurve2D.Float(16 * u, 47 * u, 24 * u, 58.5f * u, 40 * u, 58.5f * u, 48 * u, 47 * u);
        g.draw(cable);
        g.setClip(null);

        // plate edge
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke(Math.max(1f, u)));
        g.draw(plate);
        g.dispose();
        return img;
    }

    private static void paintKnob(Graphics2D g, float cx, float cy, float r, double pointerDeg) {
        g.setPaint(new RadialGradientPaint(cx - r / 3, cy - r / 3, r * 2,
                new float[]{0f, 1f},
                new Color[]{new Color(96, 98, 104), new Color(34, 35, 38)}));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0, 0, 0, 150));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        double a = Math.toRadians(pointerDeg);
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(r / 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * r * 0.72, cy - Math.sin(a) * r * 0.72));
    }

    private static void paintJack(Graphics2D g, float cx, float cy, float r) {
        g.setColor(new Color(12, 12, 14));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(120, 122, 128));
        g.setStroke(new BasicStroke(r / 4f));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    // ---- the splash: rack rails, wordmark, a hint of patched devices ----

    private static BufferedImage splash(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
        g.fillRect(0, 0, w, h);

        // rack rails with screws
        g.setColor(RAIL);
        g.fillRect(14, 0, 9, h);
        g.fillRect(w - 23, 0, 9, h);
        g.setColor(new Color(14, 14, 16));
        g.drawRect(14, -1, 9, h + 1);
        g.drawRect(w - 23, -1, 9, h + 1);
        for (int y = 18; y < h; y += 44) {
            paintScrew(g, 18, y);
            paintScrew(g, w - 19, y);
        }

        // three suggestion-of-device strips behind the wordmark
        int[] ys = {36, 86, 136};
        Color[] accents = {ACCENT, ACCENT_2, ACCENT_3};
        for (int i = 0; i < 3; i++) {
            int y = ys[i];
            g.setPaint(new GradientPaint(0, y, new Color(52, 53, 58), 0, y + 38, new Color(40, 41, 45)));
            g.fillRect(34, y, w - 68, 38);
            g.setColor(accents[i]);
            g.fillRect(34, y, w - 68, 3);
            g.setColor(new Color(12, 12, 14));
            g.drawRect(34, y, w - 68, 38);
            // a few knobs and LEDs per strip
            for (int k = 0; k < 3; k++) {
                paintKnob(g, 58 + k * 34, y + 21, 9, 200 - k * 60 - i * 25);
            }
            g.setColor(accents[(i + 1) % 3]);
            g.fill(new Ellipse2D.Float(168, y + 16, 7, 7));
            g.setColor(new Color(10, 16, 10));
            g.fillRoundRect(188, y + 11, 110, 17, 4, 4);
            g.setColor(new Color(96, 235, 120));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            g.drawString(i == 0 ? "BUILD OK 1.2s" : i == 1 ? "READY :5173" : "P:42 F:0", 193, y + 23);
        }
        // one cable sagging across the device strips
        g.setColor(new Color(0, 0, 0, 100));
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new CubicCurve2D.Float(320, 55, 360, 130, 300, 120, 340, 155));
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new CubicCurve2D.Float(320, 53, 360, 128, 300, 118, 340, 153));
        paintJack(g, 320, 53, 6);
        paintJack(g, 340, 153, 6);

        // wordmark
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        g.setColor(TEXT);
        g.drawString("NMOX", 36, 232);
        g.setColor(ACCENT);
        g.drawString("STUDIO", 178, 232);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(TEXT_DIM);
        g.drawString("The web development task rack", 38, 254);

        // progress track (the platform paints the bar inside these bounds;
        // keep in sync with SplashProgressBarBounds in Bundle.properties)
        g.setColor(new Color(12, 12, 14));
        g.fillRoundRect(36, 272, w - 72, 8, 4, 4);
        g.setColor(new Color(70, 71, 76));
        g.drawRoundRect(36, 272, w - 72, 8, 4, 4);

        g.dispose();
        return img;
    }

    private static void paintScrew(Graphics2D g, int cx, int cy) {
        g.setPaint(new RadialGradientPaint(cx - 1f, cy - 1f, 8f,
                new float[]{0f, 1f},
                new Color[]{new Color(180, 182, 186), new Color(70, 72, 76)}));
        g.fill(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
        g.setColor(new Color(28, 28, 30));
        g.drawLine(cx - 2, cy, cx + 2, cy);
    }
}
