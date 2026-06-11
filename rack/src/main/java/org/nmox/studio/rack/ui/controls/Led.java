package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A round indicator LED with a label, supporting off, on and blinking.
 */
public class Led extends JComponent {

    private final String label;
    private Color color;
    private boolean on;
    private boolean blink;
    private boolean blinkPhase;
    private final Timer blinkTimer;

    public Led(String label, Color color) {
        this.label = label;
        this.color = color;
        setPreferredSize(new Dimension(Math.max(30, label.length() * 7), 26));
        setSize(getPreferredSize());
        blinkTimer = new Timer(380, e -> {
            blinkPhase = !blinkPhase;
            repaint();
        });
    }

    public void setOn(boolean v) {
        on = v;
        if (blink) {
            blink = false;
            blinkTimer.stop();
        }
        repaint();
    }

    public boolean isOn() {
        return on;
    }

    public void setBlinking(boolean v) {
        blink = v;
        if (v) {
            blinkTimer.start();
        } else {
            blinkTimer.stop();
        }
        repaint();
    }

    public void setColor(Color c) {
        this.color = c;
        repaint();
    }

    @Override
    public void removeNotify() {
        blinkTimer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        boolean lit = blink ? blinkPhase : on;
        int cx = getWidth() / 2, cy = 8, r = 5;

        if (lit) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
            g.fill(new Ellipse2D.Float(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2));
        }
        Color body = lit ? color : color.darker().darker().darker();
        g.setPaint(new RadialGradientPaint(cx - 1.5f, cy - 1.5f, r * 2f,
                new float[]{0f, 1f},
                new Color[]{lit ? body.brighter() : body, body.darker()}));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0, 0, 0, 150));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));

        g.setFont(RackStyle.TINY_FONT);
        g.setColor(RackStyle.SILKSCREEN_DIM);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, cx - fm.stringWidth(label) / 2, cy + r + 11);
        g.dispose();
    }
}
