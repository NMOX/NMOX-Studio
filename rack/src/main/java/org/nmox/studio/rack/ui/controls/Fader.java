package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * A vertical slide fader with a brushed-aluminum cap, like a channel
 * fader on a mixing desk. Value range 0..1.
 */
public class Fader extends JComponent {

    private final String label;
    private double value;
    private final List<Runnable> listeners = new ArrayList<>();

    public Fader(String label, double initial) {
        this.label = label;
        this.value = Math.max(0, Math.min(1, initial));
        setPreferredSize(new Dimension(40, 110));
        setSize(getPreferredSize());
        setToolTipText(label);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                track(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                track(e);
            }

            private void track(MouseEvent e) {
                int top = 8, bottom = getHeight() - 26;
                double v = 1.0 - (double) (e.getY() - top) / (bottom - top);
                setValue(v);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        if (Math.abs(clamped - value) > 0.0001) {
            value = clamped;
            repaint();
            for (Runnable r : new ArrayList<>(listeners)) {
                r.run();
            }
        }
    }

    public void addChangeListener(Runnable r) {
        listeners.add(r);
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth(), h = getHeight();
        int cx = w / 2;
        int top = 8, bottom = h - 26;

        // slot
        g.setColor(new Color(8, 8, 9));
        g.fill(new RoundRectangle2D.Float(cx - 3, top, 6, bottom - top, 3, 3));
        // tick marks
        g.setColor(RackStyle.SILKSCREEN_DIM);
        for (int i = 0; i <= 10; i++) {
            int y = top + (bottom - top) * i / 10;
            g.drawLine(cx - 10, y, cx - 6, y);
        }

        // cap
        int capY = (int) (bottom - (bottom - top) * value) - 8;
        g.setPaint(RackStyle.brushedMetal(cx - 12f, capY, 24f));
        g.fill(new RoundRectangle2D.Float(cx - 12, capY, 24, 16, 4, 4));
        g.setColor(new Color(0, 0, 0, 170));
        g.draw(new RoundRectangle2D.Float(cx - 12, capY, 24, 16, 4, 4));
        g.setColor(new Color(20, 20, 22));
        g.drawLine(cx - 10, capY + 8, cx + 10, capY + 8);

        g.setFont(RackStyle.TINY_FONT);
        g.setColor(RackStyle.SILKSCREEN);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, cx - fm.stringWidth(label) / 2, h - 12);
        String pct = Math.round(value * 100) + "%";
        g.setColor(RackStyle.LCD_AMBER);
        g.drawString(pct, cx - fm.stringWidth(pct) / 2, h - 2);
        g.dispose();
    }
}
