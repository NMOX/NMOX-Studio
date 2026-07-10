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
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * A hardware push button with an integrated LED. Momentary by default;
 * the LED can be driven independently of presses (e.g. lit while a task
 * runs). Sized like the square soft-touch buttons on studio gear.
 * Space or Enter presses a focused button; to assistive technology it
 * is a PUSH_BUTTON named by its cap label.
 */
public class RackButton extends JComponent implements javax.accessibility.Accessible {

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
                // pressing a button aims the keyboard at it, like real gear
                requestFocusInWindow();
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
                    fireAction();
                }
            }
        };
        addMouseListener(ma);

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
        im.put(KeyStroke.getKeyStroke("SPACE"), "press");
        im.put(KeyStroke.getKeyStroke("ENTER"), "press");
        getActionMap().put("press", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                fireAction();
            }
        });
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    /**
     * The one firing path: mouse release, Space/Enter and the accessible
     * action all land here, so no input channel can diverge from another.
     */
    private void fireAction() {
        if (!enabledLook) {
            return; // a dimmed button ignores the keyboard like it ignores the mouse
        }
        java.awt.event.ActionEvent ev = new java.awt.event.ActionEvent(
                this, java.awt.event.ActionEvent.ACTION_PERFORMED, label);
        for (ActionListener l : new ArrayList<>(listeners)) {
            l.actionPerformed(ev);
        }
    }

    private java.util.function.Supplier<String> commandPreview;

    /**
     * Transparency: hovering the button shows exactly what it will run
     * and where, computed live so knob changes are reflected.
     */
    public void setCommandPreview(java.util.function.Supplier<String> preview) {
        this.commandPreview = preview;
        setToolTipText(label); // ensure tooltip machinery is registered
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        if (commandPreview != null) {
            try {
                String preview = commandPreview.get();
                if (preview != null && !preview.isBlank()) {
                    return preview;
                }
            } catch (RuntimeException ignored) {
                // preview must never break hovering
            }
        }
        return super.getToolTipText(e);
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

        // keyboard focus ring, just outside the cap
        if (isFocusOwner()) {
            g.setColor(RackStyle.FOCUS_RING);
            g.setStroke(RackStyle.focusStroke());
            g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, w - 5, bh - 5, 8, 8));
        }
        g.dispose();
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleRackButton();
        }
        return accessibleContext;
    }

    private final class AccessibleRackButton extends AccessibleJComponent implements AccessibleAction {

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PUSH_BUTTON;
        }

        @Override
        public String getAccessibleName() {
            String name = super.getAccessibleName();
            return name != null ? name : label;
        }

        @Override
        public AccessibleAction getAccessibleAction() {
            return this;
        }

        @Override
        public int getAccessibleActionCount() {
            return 1;
        }

        @Override
        public String getAccessibleActionDescription(int i) {
            return i == 0 ? AccessibleAction.CLICK : null;
        }

        @Override
        public boolean doAccessibleAction(int i) {
            if (i != 0) {
                return false;
            }
            fireAction();
            return true;
        }
    }
}
