package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * A two-position vertical toggle switch with silk-screened ON/OFF
 * (or custom) position labels, like the rocker switches on rack gear.
 */
public class ToggleSwitch extends JComponent {

    private final String label;
    private final String onText;
    private final String offText;
    private boolean on;
    private final List<Runnable> listeners = new ArrayList<>();

    public ToggleSwitch(String label, boolean initial) {
        this(label, initial, "ON", "OFF");
    }

    public ToggleSwitch(String label, boolean initial, String onText, String offText) {
        this.label = label;
        this.on = initial;
        this.onText = onText;
        this.offText = offText;
        setPreferredSize(new Dimension(66, 74));
        setSize(getPreferredSize());
        setToolTipText(label);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setOn(!on);
            }
        });
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean v) {
        if (on != v) {
            on = v;
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
        int w = getWidth();
        int cx = w / 2;

        // switch well
        int wellW = 16, wellH = 34, wellX = cx - wellW / 2, wellY = 10;
        g.setColor(new Color(10, 10, 12));
        g.fill(new RoundRectangle2D.Float(wellX, wellY, wellW, wellH, 6, 6));
        g.setColor(new Color(255, 255, 255, 30));
        g.draw(new RoundRectangle2D.Float(wellX, wellY, wellW, wellH, 6, 6));

        // bat handle: top half when on, bottom half when off
        int batH = 16;
        int batY = on ? wellY + 2 : wellY + wellH - batH - 2;
        g.setPaint(new GradientPaint(wellX, batY, new Color(210, 212, 216), wellX, batY + batH, new Color(120, 122, 126)));
        g.fill(new RoundRectangle2D.Float(wellX + 2, batY, wellW - 4, batH, 4, 4));
        g.setColor(new Color(0, 0, 0, 140));
        g.draw(new RoundRectangle2D.Float(wellX + 2, batY, wellW - 4, batH, 4, 4));

        // position labels beside the well
        g.setFont(RackStyle.TINY_FONT);
        g.setColor(on ? RackStyle.LCD_AMBER : RackStyle.SILKSCREEN_DIM);
        g.drawString(onText, wellX + wellW + 4, wellY + 11);
        g.setColor(!on ? RackStyle.LCD_AMBER : RackStyle.SILKSCREEN_DIM);
        g.drawString(offText, wellX + wellW + 4, wellY + wellH - 3);

        // main label below
        g.setFont(RackStyle.LABEL_FONT);
        g.setColor(RackStyle.SILKSCREEN);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, cx - fm.stringWidth(label) / 2, wellY + wellH + 16);
        g.dispose();
    }
}
