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
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleValue;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * A rotary knob in the style of studio hardware. Operates in two modes:
 * stepped (a fixed list of named positions, like a chicken-head selector)
 * or continuous (0.0 - 1.0). Drag vertically to turn, scroll to nudge,
 * double-click to reset. Keyboard: arrows step, Home/End jump to the
 * ends. To assistive technology it is a SLIDER; a continuous knob
 * reports percent (matching the painted readout), a stepped knob
 * reports its position index with the position's text in the
 * description.
 */
public class Knob extends JComponent implements javax.accessibility.Accessible {

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
        installKeyboard();
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
        installKeyboard();
        setToolTipText(label);
    }

    private void installKeyboard() {
        setFocusable(true);
        // the focus ring is painted state, so focus changes must repaint
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
        var im = getInputMap(WHEN_FOCUSED);
        im.put(KeyStroke.getKeyStroke("UP"), "increment");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "increment");
        im.put(KeyStroke.getKeyStroke("DOWN"), "decrement");
        im.put(KeyStroke.getKeyStroke("LEFT"), "decrement");
        im.put(KeyStroke.getKeyStroke("HOME"), "minimum");
        im.put(KeyStroke.getKeyStroke("END"), "maximum");
        getActionMap().put("increment", action(() -> nudge(1)));
        getActionMap().put("decrement", action(() -> nudge(-1)));
        getActionMap().put("minimum", action(() -> {
            if (options != null) {
                setSelectedIndex(0);
            } else {
                setValue(0);
            }
        }));
        getActionMap().put("maximum", action(() -> {
            if (options != null) {
                setSelectedIndex(options.length - 1);
            } else {
                setValue(1);
            }
        }));
    }

    private static javax.swing.Action action(Runnable body) {
        return new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                body.run();
            }
        };
    }

    /** One keyboard step: an option on stepped knobs, 5% on continuous. */
    private void nudge(int direction) {
        if (options != null) {
            setSelectedIndex(selectedIndex + direction);
        } else {
            setValue(value + direction * 0.05);
        }
    }

    private void installMouse() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // grabbing a knob aims the keyboard at it, like real gear
                requestFocusInWindow();
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
        int old = selectedIndex;
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
        fireAccessibleValue(old, selectedIndex);
    }

    public boolean isStepped() {
        return options != null;
    }

    /**
     * The current option list of a stepped knob (copy), or null for a
     * continuous knob — lets reload paths equality-guard before calling
     * {@link #setOptions}, which always fires a change event.
     */
    public String[] getOptions() {
        return options == null ? null : options.clone();
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
            int old = selectedIndex;
            selectedIndex = clamped;
            setToolTipText(label + ": " + options[selectedIndex]);
            repaint();
            fireChanged();
            fireAccessibleValue(old, clamped);
        }
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        if (Math.abs(clamped - value) > 0.0001) {
            int old = percent();
            value = clamped;
            repaint();
            fireChanged();
            fireAccessibleValue(old, percent());
        }
    }

    /** The painted readout rounds to percent; the accessible value matches. */
    private int percent() {
        return (int) Math.round(value * 100);
    }

    private void fireAccessibleValue(int old, int now) {
        // guarded on the field: no assistive tech asked, nothing to tell
        if (accessibleContext != null && old != now) {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_VALUE_PROPERTY,
                    Integer.valueOf(old), Integer.valueOf(now));
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

        // keyboard focus ring, outside the tick marks
        if (isFocusOwner()) {
            g.setColor(RackStyle.FOCUS_RING);
            g.setStroke(RackStyle.focusStroke());
            g.draw(new Ellipse2D.Float(cx - r - 9, cy - r - 9, (r + 9) * 2, (r + 9) * 2));
        }

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

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleKnob();
        }
        return accessibleContext;
    }

    private final class AccessibleKnob extends AccessibleJComponent implements AccessibleValue {

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.SLIDER;
        }

        @Override
        public String getAccessibleName() {
            String name = super.getAccessibleName();
            return name != null ? name : label;
        }

        @Override
        public String getAccessibleDescription() {
            // the field, not super: super falls back to the tooltip,
            // which is the label - the description must add the value
            if (accessibleDescription != null) {
                return accessibleDescription;
            }
            if (options != null) {
                return "Position " + (selectedIndex + 1) + " of " + options.length
                        + ": " + options[selectedIndex];
            }
            return percent() + " percent";
        }

        @Override
        public AccessibleValue getAccessibleValue() {
            return this;
        }

        @Override
        public Number getCurrentAccessibleValue() {
            return options != null ? selectedIndex : percent();
        }

        @Override
        public boolean setCurrentAccessibleValue(Number n) {
            if (n == null) {
                return false;
            }
            if (options != null) {
                setSelectedIndex(n.intValue());
            } else {
                setValue(n.intValue() / 100.0);
            }
            return true;
        }

        @Override
        public Number getMinimumAccessibleValue() {
            return 0;
        }

        @Override
        public Number getMaximumAccessibleValue() {
            return options != null ? options.length - 1 : 100;
        }
    }
}
