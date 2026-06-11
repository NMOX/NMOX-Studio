package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A segmented activity meter (green -> yellow -> red) with peak-hold and
 * decay, like the VU ladders on a mixing console. Devices pulse it as
 * output lines stream in, so a busy task "plays" the meter.
 */
public class VuMeter extends JComponent {

    private static final int SEGMENTS = 12;

    private final String label;
    private final boolean vertical;
    private double level;       // 0..1, decays over time
    private double peak;
    private long peakTime;
    private final Timer decayTimer;

    public VuMeter(String label, boolean vertical) {
        this.label = label;
        this.vertical = vertical;
        setPreferredSize(vertical ? new Dimension(26, 96) : new Dimension(110, 30));
        setSize(getPreferredSize());
        decayTimer = new Timer(70, e -> {
            boolean active = level > 0.001 || peak > 0.001;
            level = Math.max(0, level - 0.06);
            if (System.currentTimeMillis() - peakTime > 1200) {
                peak = Math.max(0, peak - 0.04);
            }
            if (active) {
                repaint();
            } else {
                ((Timer) e.getSource()).stop();
            }
        });
    }

    /** Kick the meter up to at least this level (0..1); it decays on its own. */
    public void pulse(double v) {
        Runnable r = () -> {
            level = Math.max(level, Math.min(1, v));
            if (level > peak) {
                peak = level;
                peakTime = System.currentTimeMillis();
            }
            if (!decayTimer.isRunning()) {
                decayTimer.start();
            }
            repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Pin the meter to a fixed level (for gauges such as severity counts). */
    public void setLevel(double v) {
        Runnable r = () -> {
            level = Math.max(0, Math.min(1, v));
            peak = level;
            peakTime = System.currentTimeMillis();
            repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    @Override
    public void removeNotify() {
        decayTimer.stop();
        super.removeNotify();
    }

    private static Color segmentColor(int i) {
        if (i >= SEGMENTS - 2) {
            return new Color(255, 64, 56);
        }
        if (i >= SEGMENTS - 5) {
            return new Color(255, 200, 40);
        }
        return new Color(70, 230, 90);
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth(), h = getHeight();
        int litCount = (int) Math.round(level * SEGMENTS);
        int peakSeg = (int) Math.round(peak * SEGMENTS) - 1;

        if (vertical) {
            int mh = h - 14;
            int segH = (mh - 4) / SEGMENTS;
            int x = w / 2 - 7;
            g.setColor(new Color(8, 8, 9));
            g.fill(new RoundRectangle2D.Float(x - 2, 0, 18, mh, 4, 4));
            for (int i = 0; i < SEGMENTS; i++) {
                int y = mh - 4 - (i + 1) * segH;
                Color c = segmentColor(i);
                boolean lit = i < litCount || i == peakSeg;
                g.setColor(lit ? c : new Color(c.getRed() / 5, c.getGreen() / 5, c.getBlue() / 5));
                g.fillRect(x, y, 14, segH - 2);
            }
            g.setFont(RackStyle.TINY_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, w / 2 - fm.stringWidth(label) / 2, h - 2);
        } else {
            int mw = w - 4;
            int segW = mw / SEGMENTS;
            int y = 4;
            g.setColor(new Color(8, 8, 9));
            g.fill(new RoundRectangle2D.Float(0, y - 2, mw + 4, 14, 4, 4));
            for (int i = 0; i < SEGMENTS; i++) {
                Color c = segmentColor(i);
                boolean lit = i < litCount || i == peakSeg;
                g.setColor(lit ? c : new Color(c.getRed() / 5, c.getGreen() / 5, c.getBlue() / 5));
                g.fillRect(2 + i * segW, y, segW - 2, 10);
            }
            g.setFont(RackStyle.TINY_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            g.drawString(label, 2, y + 22);
        }
        g.dispose();
    }
}
