package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A green-on-black LCD panel, one or more lines. Multi-line displays
 * scroll like a tiny console; single-line displays show a status string.
 * Optionally editable via double-click (used for URL entry and the like).
 */
public class LcdDisplay extends JComponent {

    private final int lines;
    private final LinkedList<String> buffer = new LinkedList<>();
    private String text = "";
    private Color textColor = RackStyle.LCD_TEXT;
    private boolean editable;
    private String editPrompt = "Value";
    private final List<Runnable> editListeners = new ArrayList<>();

    public LcdDisplay(int widthPx, int lines) {
        this.lines = Math.max(1, lines);
        setPreferredSize(new Dimension(widthPx, 12 + this.lines * 15));
        setSize(getPreferredSize());
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (editable && e.getClickCount() == 2) {
                    String input = javax.swing.JOptionPane.showInputDialog(LcdDisplay.this, editPrompt, text);
                    if (input != null) {
                        setText(input.trim());
                        for (Runnable r : new ArrayList<>(editListeners)) {
                            r.run();
                        }
                    }
                }
            }
        });
    }

    public void setEditable(String prompt) {
        this.editable = true;
        this.editPrompt = prompt;
        setToolTipText("Double-click to edit");
    }

    public void addEditListener(Runnable r) {
        editListeners.add(r);
    }

    public void setTextColor(Color c) {
        textColor = c;
        repaintLater();
    }

    /** Single-line mode: replace the text outright. */
    public void setText(String t) {
        this.text = t == null ? "" : t;
        repaintLater();
    }

    public String getText() {
        return text;
    }

    /** Multi-line mode: append a line, scrolling old ones off. */
    public void appendLine(String line) {
        synchronized (buffer) {
            buffer.add(line == null ? "" : line);
            while (buffer.size() > lines) {
                buffer.removeFirst();
            }
        }
        repaintLater();
    }

    public void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
        text = "";
        repaintLater();
    }

    private void repaintLater() {
        if (SwingUtilities.isEventDispatchThread()) {
            repaint();
        } else {
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth(), h = getHeight();

        RoundRectangle2D bezel = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 6, 6);
        g.setColor(new Color(8, 8, 9));
        g.fill(bezel);
        g.setColor(RackStyle.LCD_BG);
        g.fill(new RoundRectangle2D.Float(2, 2, w - 5, h - 5, 4, 4));

        Shape oldClip = g.getClip();
        g.clip(new RoundRectangle2D.Float(2, 2, w - 5, h - 5, 4, 4));
        g.setFont(RackStyle.LCD_FONT);
        FontMetrics fm = g.getFontMetrics();

        // faint scanlines for that LCD feel
        g.setColor(new Color(255, 255, 255, 6));
        for (int y = 3; y < h - 3; y += 3) {
            g.drawLine(3, y, w - 4, y);
        }

        g.setColor(textColor);
        if (lines == 1) {
            String t = text;
            while (t.length() > 1 && fm.stringWidth(t) > w - 14) {
                t = t.substring(1);
            }
            g.drawString(t, 7, h / 2 + fm.getAscent() / 2 - 2);
        } else {
            List<String> snapshot;
            synchronized (buffer) {
                snapshot = new ArrayList<>(buffer);
            }
            int y = 4 + fm.getAscent();
            for (String line : snapshot) {
                String t = line;
                while (t.length() > 1 && fm.stringWidth(t) > w - 14) {
                    t = t.substring(0, t.length() - 1);
                }
                g.drawString(t, 7, y);
                y += 15;
            }
        }
        g.setClip(oldClip);

        g.setColor(new Color(255, 255, 255, 24));
        g.draw(bezel);
        g.dispose();
    }
}
