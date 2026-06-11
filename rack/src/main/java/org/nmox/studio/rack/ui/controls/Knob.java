package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * A rotary knob in the style of studio hardware. Operates in two modes:
 * stepped (a fixed list of named positions, like a chicken-head selector)
 * or continuous (0.0 - 1.0). Drag vertically to turn, scroll to nudge,
 * double-click to reset.
 */
public class Knob extends JComponent {

    /** Sweep range: from 7 o'clock to 5 o'clock, like real gear. */
    private static final double START_ANGLE = Math.toRadians(220);
    private static final double SWEEP = Math.toRadians(-260);

    private final String label;
    private String[] options;           // null => continuous
    private int selectedIndex;
    private double value;               // continuous 0..1
    private final List<Runnable> changeListeners = new ArrayList<>();
    private Point dragOrigin;
    private double dragStartValue;
    private int dragStartIndex;

    /** Stepped selector knob. */
    public Knob(String label, String[] options, int initialIndex) {
        this.label = label;
        this.options = options.clone();
        this.selectedIndex = Math.max(0, Math.min(initialIndex, options.length - 1));
        setPreferredSize(new Dimension(64, 78));
        setSize(getPreferredSize());
        installMouse();
        setToolTipText(label);
    }

    /** Continuous knob 0..1. */
    public Knob(String label, double initialValue) {
        this.label = label;
        this.options = null;
        this.value = Math.max(0, Math.min(1, initialValue));
        setPreferredSize(new Dimension(64, 78));
        setSize(getPreferredSize());
        installMouse();
        setToolTipText(label);
    }

    private void installMouse() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOrigin = e.getPoint();
                dragStartValue = value;
                dragStartIndex = selectedIndex;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null) {
                    return;
                }
                int dy = dragOrigin.y - e.getY();
                if (options != null) {
                    int steps = dy / 14;
                    setSelectedIndex(dragStartIndex + steps);
                } else {
                    setValue(dragStartValue + dy / 150.0);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (options != null) {
                        setSelectedIndex(0);
                    } else {
                        setValue(0.5);
                    }
                }
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                if (options != null) {
                    setSelectedIndex(selectedIndex - e.getWheelRotation());
                } else {
                    setValue(value - e.getWheelRotation() * 0.05);
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    public void addChangeListener(Runnable r) {
        changeListeners.add(r);
    }

    private void fireChanged() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Replaces the option list of a stepped knob (e.g. project scripts changed). */
    public void setOptions(String[] newOptions) {
        if (options == null || newOptions == null || newOptions.length == 0) {
            return;
        }
        String current = options[selectedIndex];
        options = newOptions.clone();
        selectedIndex = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) {
                selectedIndex = i;
                break;
            }
        }
        repaint();
        fireChanged();
    }

    public boolean isStepped() {
        return options != null;
    }

    /**
     * Selects a stepped option by its text; falls back to parsing a
     * numeric index (the legacy patch format). Unknown values keep the
     * current selection.
     */
    public void selectOption(String value) {
        if (options == null || value == null) {
            return;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                setSelectedIndex(i);
                return;
            }
        }
        try {
            setSelectedIndex(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // neither a known option nor an index; keep current
        }
    }

    public String getSelectedOption() {
        return options == null ? null : options[selectedIndex];
    }

    public void setSelectedIndex(int idx) {
        if (options == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(idx, options.length - 1));
        if (clamped != selectedIndex) {
            selectedIndex = clamped;
            setToolTipText(label + ": " + options[selectedIndex]);
            repaint();
            fireChanged();
        }
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        if (Math.abs(clamped - value) > 0.0001) {
            value = clamped;
            repaint();
            fireChanged();
        }
    }

    private double position() {
        if (options == null) {
            return value;
        }
        return options.length <= 1 ? 0 : (double) selectedIndex / (options.length - 1);
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth();
        int cx = w / 2;
        int cy = 28;
        int r = 18;

        // tick marks
        g.setColor(RackStyle.SILKSCREEN_DIM);
        int ticks = options != null ? options.length : 11;
        for (int i = 0; i < ticks; i++) {
            double frac = ticks == 1 ? 0 : (double) i / (ticks - 1);
            double a = START_ANGLE + SWEEP * frac;
            double c = Math.cos(a), s = -Math.sin(a);
            g.draw(new Line2D.Double(cx + c * (r + 3), cy + s * (r + 3), cx + c * (r + 6), cy + s * (r + 6)));
        }

        // knob body
        g.setPaint(new RadialGradientPaint(cx - 5f, cy - 6f, r * 2f,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(95, 97, 102), new Color(48, 49, 53), new Color(20, 20, 22)}));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0, 0, 0, 180));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        // grip dimple ring
        g.setColor(new Color(255, 255, 255, 18));
        g.draw(new Ellipse2D.Float(cx - r + 3, cy - r + 3, (r - 3) * 2, (r - 3) * 2));

        // pointer
        double a = START_ANGLE + SWEEP * position();
        double c = Math.cos(a), s = -Math.sin(a);
        g.setColor(new Color(240, 240, 244));
        g.setStroke(new java.awt.BasicStroke(2.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx + c * 4, cy + s * 4, cx + c * (r - 4), cy + s * (r - 4)));

        // labels
        g.setFont(RackStyle.LABEL_FONT);
        g.setColor(RackStyle.SILKSCREEN);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, cx - fm.stringWidth(label) / 2, cy + r + 14);
        String valText = options != null ? options[selectedIndex] : String.valueOf(Math.round(value * 100));
        g.setFont(RackStyle.TINY_FONT);
        g.setColor(RackStyle.LCD_AMBER);
        fm = g.getFontMetrics();
        if (fm.stringWidth(valText) > w - 4) {
            while (valText.length() > 3 && fm.stringWidth(valText + "…") > w - 4) {
                valText = valText.substring(0, valText.length() - 1);
            }
            valText += "…";
        }
        g.drawString(valText, cx - fm.stringWidth(valText) / 2, cy + r + 26);
        g.dispose();
    }
}
