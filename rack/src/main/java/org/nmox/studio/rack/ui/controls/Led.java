package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A round indicator LED with a label, supporting off, on and blinking.
 * A pure indicator: to assistive technology it is a read-only LABEL
 * (never focusable) whose description tracks lit state and color.
 */
public class Led extends JComponent implements javax.accessibility.Accessible {

    private final String label;
    private Color color;
    private boolean on;
    private boolean blink;
    private boolean blinkPhase;
    private final Timer blinkTimer;

    public Led(String label, Color color) {
        this.label = label;
        this.color = color;
        // an indicator is not operable; keep it out of the Tab order
        setFocusable(false);
        setPreferredSize(new Dimension(Math.max(30, label.length() * 7), 26));
        setSize(getPreferredSize());
        blinkTimer = new Timer(380, e -> {
            blinkPhase = !blinkPhase;
            repaint();
        });
    }

    public void setOn(boolean v) {
        String old = stateText();
        on = v;
        if (blink) {
            blink = false;
            blinkTimer.stop();
        }
        repaint();
        fireStateChanged(old);
    }

    public boolean isOn() {
        return on;
    }

    public void setBlinking(boolean v) {
        String old = stateText();
        blink = v;
        if (v) {
            blinkTimer.start();
        } else {
            blinkTimer.stop();
        }
        repaint();
        fireStateChanged(old);
    }

    public void setColor(Color c) {
        String old = stateText();
        this.color = c;
        repaint();
        fireStateChanged(old);
    }

    /** What a screen reader hears for the current lamp state. */
    private String stateText() {
        String base = blink ? "blinking" : on ? "on" : "off";
        String hue = colorName(color);
        return (blink || on) && !hue.isEmpty() ? base + " (" + hue + ")" : base;
    }

    /**
     * Devices light LEDs in the RackStyle palette; naming those few
     * hues beats reading RGB triplets to a screen reader. Unknown
     * colors stay nameless rather than guessing.
     */
    private static String colorName(Color c) {
        if (c == null) {
            return "";
        }
        if (c.equals(RackStyle.GO) || c.equals(RackStyle.LCD_TEXT)) {
            return "green";
        }
        if (c.equals(RackStyle.STOP)) {
            return "red";
        }
        if (c.equals(RackStyle.MUTATE) || c.equals(RackStyle.LCD_AMBER)) {
            return "amber";
        }
        if (c.equals(RackStyle.QUERY)) {
            return "blue";
        }
        return "";
    }

    private void fireStateChanged(String oldState) {
        // guarded on the field: no assistive tech asked, nothing to tell
        String now = stateText();
        if (accessibleContext != null && !now.equals(oldState)) {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY, oldState, now);
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_STATE_PROPERTY, oldState, now);
        }
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

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleLed();
        }
        return accessibleContext;
    }

    private final class AccessibleLed extends AccessibleJComponent {

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.LABEL;
        }

        @Override
        public String getAccessibleName() {
            String name = super.getAccessibleName();
            return name != null ? name : label;
        }

        @Override
        public String getAccessibleDescription() {
            // the field, not super: super falls back to the tooltip,
            // and the description must track the lamp, not a hint
            return accessibleDescription != null ? accessibleDescription : stateText();
        }
    }
}
