package org.nmox.studio.rack.ui.controls;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * A hardware push button with an integrated LED. Momentary by default;
 * the LED can be driven independently of presses (e.g. lit while a task
 * runs). Sized like the square soft-touch buttons on studio gear.
 */
public class RackButton extends JComponent {

    private final String label;
    private final Color ledColor;
    private boolean pressed;
    private boolean lit;
    private boolean enabledLook = true;
    private final List<ActionListener> listeners = new ArrayList<>();

    public RackButton(String label, Color ledColor) {
        this.label = label;
        this.ledColor = ledColor;
        setPreferredSize(new Dimension(58, 40));
        setSize(getPreferredSize());
        setToolTipText(label);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!enabledLook) {
                    return;
                }
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!enabledLook) {
                    return;
                }
                boolean fire = pressed && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire) {
                    java.awt.event.ActionEvent ev =
                            new java.awt.event.ActionEvent(RackButton.this, java.awt.event.ActionEvent.ACTION_PERFORMED, label);
                    for (ActionListener l : new ArrayList<>(listeners)) {
                        l.actionPerformed(ev);
                    }
                }
            }
        };
        addMouseListener(ma);
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    public void setLit(boolean lit) {
        if (this.lit != lit) {
            this.lit = lit;
            repaint();
        }
    }

    public boolean isLit() {
        return lit;
    }

    /** Dims the button to indicate it is inoperative (e.g. not armed). */
    public void setEnabledLook(boolean on) {
        this.enabledLook = on;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth(), h = getHeight();
        int bh = h - 14;

        // button cap
        Color top = pressed ? new Color(38, 39, 42) : new Color(72, 74, 79);
        Color bot = pressed ? new Color(30, 31, 34) : new Color(46, 47, 51);
        if (!enabledLook) {
            top = new Color(40, 41, 44);
            bot = new Color(34, 35, 38);
        }
        g.setPaint(new GradientPaint(0, 0, top, 0, bh, bot));
        RoundRectangle2D rect = new RoundRectangle2D.Float(1, pressed ? 2 : 1, w - 2, bh - 2, 7, 7);
        g.fill(rect);
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(rect);
        if (!pressed) {
            g.setColor(new Color(255, 255, 255, 28));
            g.drawLine(4, 3, w - 5, 3);
        }

        // LED bar across the top of the cap
        Color led = lit ? ledColor : ledColor.darker().darker().darker();
        g.setColor(led);
        g.fill(new RoundRectangle2D.Float(7, pressed ? 7 : 6, w - 14, 4, 3, 3));
        if (lit) {
            g.setColor(new Color(ledColor.getRed(), ledColor.getGreen(), ledColor.getBlue(), 60));
            g.fill(new RoundRectangle2D.Float(4, pressed ? 4 : 3, w - 8, 10, 6, 6));
        }

        // label on the cap
        g.setFont(RackStyle.LABEL_FONT);
        g.setColor(enabledLook ? RackStyle.SILKSCREEN : RackStyle.SILKSCREEN_DIM);
        FontMetrics fm = g.getFontMetrics();
        String text = label;
        g.drawString(text, (w - fm.stringWidth(text)) / 2, (pressed ? 1 : 0) + bh - 8);
        g.dispose();
    }
}
