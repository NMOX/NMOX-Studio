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
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * A green-on-black LCD panel, one or more lines. Multi-line displays
 * scroll like a tiny console; single-line displays show a status string.
 * Optionally editable via double-click (used for URL entry and the like).
 * To assistive technology it is a read-only LABEL (never focusable):
 * the name says what the panel shows (explicit name, else the edit
 * prompt), the description is the text currently on the glass.
 */
public class LcdDisplay extends JComponent implements javax.accessibility.Accessible {

    /** One scrolled line and the color it glows in (null = panel default). */
    private record Entry(String text, Color color) {
    }

    private final int lines;
    private final LinkedList<Entry> buffer = new LinkedList<>();
    private String text = "";
    private Color textColor = RackStyle.LCD_TEXT;
    private boolean editable;
    private String editPrompt = "Value";
    private final List<Runnable> editListeners = new ArrayList<>();

    public LcdDisplay(int widthPx, int lines) {
        this.lines = Math.max(1, lines);
        // a display is not operable; keep it out of the Tab order
        setFocusable(false);
        setPreferredSize(new Dimension(widthPx, 12 + this.lines * 15));
        setSize(getPreferredSize());
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (editable && e.getClickCount() == 2) {
                    NotifyDescriptor.InputLine line = new NotifyDescriptor.InputLine(editPrompt, editPrompt);
                    line.setInputText(text);
                    if (DialogDisplayer.getDefault().notify(line) == NotifyDescriptor.OK_OPTION) {
                        setText(line.getInputText().trim());
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
        String old = shownText();
        this.text = t == null ? "" : t;
        repaintLater();
        fireTextChanged(old);
    }

    public String getText() {
        return text;
    }

    /** Multi-line mode: append a line, scrolling old ones off. */
    public void appendLine(String line) {
        appendLine(line, null);
    }

    /** Multi-line mode with a per-line glow color (null = panel default). */
    public void appendLine(String line, Color color) {
        String old = shownText();
        synchronized (buffer) {
            buffer.add(new Entry(line == null ? "" : line, color));
            while (buffer.size() > lines) {
                buffer.removeFirst();
            }
        }
        repaintLater();
        fireTextChanged(old);
    }

    public void clear() {
        String old = shownText();
        synchronized (buffer) {
            buffer.clear();
        }
        text = "";
        repaintLater();
        fireTextChanged(old);
    }

    /** Everything currently on the glass, newest line last. */
    private String shownText() {
        if (lines == 1) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (buffer) {
            for (Entry e : buffer) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(e.text());
            }
        }
        return sb.toString();
    }

    private void fireTextChanged(String oldShown) {
        // guarded on the field: no assistive tech asked, nothing to tell
        if (accessibleContext == null) {
            return;
        }
        String now = shownText();
        if (now.equals(oldShown)) {
            return;
        }
        // marshal like repaintLater: appendLine streams in from process
        // reader threads, and accessibility listeners expect the EDT
        Runnable fire = () -> {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY, Boolean.FALSE, Boolean.TRUE);
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY, oldShown, now);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            fire.run();
        } else {
            SwingUtilities.invokeLater(fire);
        }
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
            List<Entry> snapshot;
            synchronized (buffer) {
                snapshot = new ArrayList<>(buffer);
            }
            int y = 4 + fm.getAscent();
            for (Entry entry : snapshot) {
                String t = entry.text();
                while (t.length() > 1 && fm.stringWidth(t) > w - 14) {
                    t = t.substring(0, t.length() - 1);
                }
                g.setColor(entry.color() != null ? entry.color() : textColor);
                g.drawString(t, 7, y);
                y += 15;
            }
        }
        g.setClip(oldClip);

        g.setColor(new Color(255, 255, 255, 24));
        g.draw(bezel);
        g.dispose();
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleLcdDisplay();
        }
        return accessibleContext;
    }

    private final class AccessibleLcdDisplay extends AccessibleJComponent {

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.LABEL;
        }

        @Override
        public String getAccessibleName() {
            // explicit name from the device first; an editable panel's
            // prompt already says what it shows ("URL to watch", ...)
            String name = super.getAccessibleName();
            if (name != null) {
                return name;
            }
            return editable ? editPrompt : null;
        }

        @Override
        public String getAccessibleDescription() {
            // the description IS the glass: read what the panel shows,
            // not the "Double-click to edit" tooltip super falls back to
            return accessibleDescription != null ? accessibleDescription : shownText();
        }
    }
}
