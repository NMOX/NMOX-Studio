package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared look-and-feel constants and painting helpers for the NMOX rack.
 * Aims for the studio-hardware aesthetic of Reason's device rack: dark
 * faceplates, brushed metal, screws, LEDs and silk-screened labels.
 */
public final class RackStyle {

    private RackStyle() {
    }

    /** Total width of a rack device including the rack ears. */
    public static final int RACK_WIDTH = 920;
    /** Width of one rack ear (the mounting flanges left/right). */
    public static final int EAR_WIDTH = 26;
    /** One rack unit in pixels; devices are N units tall. */
    public static final int UNIT = 66;

    // Palette
    public static final Color RACK_BG = new Color(24, 24, 27);
    public static final Color RAIL = new Color(54, 54, 58);
    public static final Color RAIL_EDGE = new Color(14, 14, 16);
    public static final Color FACE_TOP = new Color(62, 63, 68);
    public static final Color FACE_BOTTOM = new Color(43, 44, 48);
    public static final Color BACK_TOP = new Color(38, 38, 41);
    public static final Color BACK_BOTTOM = new Color(28, 28, 31);
    public static final Color PANEL_EDGE = new Color(12, 12, 14);
    public static final Color SILKSCREEN = new Color(206, 208, 212);
    public static final Color SILKSCREEN_DIM = new Color(140, 142, 148);
    public static final Color LCD_BG = new Color(14, 22, 14);
    public static final Color LCD_TEXT = new Color(96, 235, 120);
    public static final Color LCD_AMBER = new Color(255, 184, 76);

    public static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    public static final Font TINY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    public static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    public static final Font BRAND_FONT = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 11);
    public static final Font LCD_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);

    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Paints a philips-head mounting screw centered at (cx, cy). */
    public static void paintScrew(Graphics2D g, int cx, int cy) {
        int r = 6;
        g.setPaint(new RadialGradientPaint(cx - 2f, cy - 2f, r * 2f,
                new float[]{0f, 1f},
                new Color[]{new Color(190, 192, 196), new Color(70, 72, 76)}));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(30, 30, 32));
        g.drawLine(cx - 4, cy, cx + 4, cy);
        g.drawLine(cx, cy - 4, cx, cy + 4);
        g.setColor(new Color(15, 15, 17, 160));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    /** Paints the device faceplate background (front view). */
    public static void paintFaceplate(Graphics2D g, int w, int h, Color accent) {
        g.setPaint(new GradientPaint(0, 0, FACE_TOP, 0, h, FACE_BOTTOM));
        g.fillRect(0, 0, w, h);
        // subtle brushed-metal horizontal striations
        g.setColor(new Color(255, 255, 255, 5));
        for (int y = 2; y < h; y += 3) {
            g.drawLine(0, y, w, y);
        }
        // accent stripe along the top edge
        if (accent != null) {
            g.setPaint(new GradientPaint(0, 0, accent, 0, 5, accent.darker()));
            g.fillRect(0, 0, w, 5);
        }
        // beveled edges
        g.setColor(new Color(255, 255, 255, 26));
        g.drawLine(0, 1, w, 1);
        g.setColor(PANEL_EDGE);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(new Color(0, 0, 0, 90));
        g.drawLine(0, h - 2, w, h - 2);
    }

    /** Paints the device back panel background (rear view). */
    public static void paintBackPanel(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, BACK_TOP, 0, h, BACK_BOTTOM));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 4));
        for (int x = 2; x < w; x += 4) {
            g.drawLine(x, 0, x, h);
        }
        g.setColor(PANEL_EDGE);
        g.drawRect(0, 0, w - 1, h - 1);
    }

    /** Paints a recessed group box with an etched label, front-panel style. */
    public static void paintGroup(Graphics2D g, int x, int y, int w, int h, String label) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
        g.setColor(new Color(255, 255, 255, 22));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
        if (label != null) {
            g.setFont(TINY_FONT);
            g.setColor(SILKSCREEN_DIM);
            g.drawString(label, x + 6, y + 11);
        }
    }

    /** Brushed-aluminum gradient for fader caps and similar hardware. */
    public static LinearGradientPaint brushedMetal(float x, float y, float w) {
        return new LinearGradientPaint(x, y, x + w, y,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(120, 122, 126), new Color(205, 207, 211), new Color(105, 107, 111)});
    }
}
