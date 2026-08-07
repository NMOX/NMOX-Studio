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

    /**
     * Fit text to {@code maxPx}, keeping the HEAD and marking the cut.
     *
     * <p>The single-line branch used to drop characters off the FRONT,
     * silently: VERITAS's "UNTRUSTED WORKSPACE — EXECUTION REFUSED"
     * reached the user as "ED WORKSPACE — EXECUTION REFUSED", which
     * loses the one word the message exists to say and reads like
     * content rather than a cut. The multi-line branch cut from the
     * end, also silently, so the two halves of one widget disagreed.
     * Both now keep the head and end in an ellipsis, and whatever the
     * glass cannot hold is one hover away — see {@link #getToolTipText}.
     *
     * @param maxPx   available width inside the bezel
     * @param widthOf measures a candidate string (the font's metrics)
     * @return text that fits, ending in … when it had to be cut
     */
    static String fit(String t, int maxPx, java.util.function.ToIntFunction<String> widthOf) {
        if (t == null || t.isEmpty() || widthOf.applyAsInt(t) <= maxPx) {
            return t == null ? "" : t;
        }
        String head = t;
        while (!head.isEmpty() && widthOf.applyAsInt(head + "…") > maxPx) {
            // whole code points, never bare chars: chopping a UTF-16
            // unit can split a surrogate pair and leave a lone high
            // surrogate on the glass (the v1.149.0 cap law — this
            // helper reintroduced the class one day before this review)
            head = head.substring(0, head.offsetByCodePoints(head.length(), -1));
        }
        // even the ellipsis alone may not fit; showing it beats showing
        // a lone letter that reads as content
        return head + "…";
    }

    /**
     * Register for tooltips here, not in the constructor: devices are
     * built off the EDT, and {@link javax.swing.ToolTipManager} is a
     * Swing singleton that must be touched on the paint thread.
     * v1.282.0 registered in the constructor and the tooltip never
     * fired in the shipped app — the method returned the right string,
     * nothing ever asked it. {@code addNotify} runs on the EDT when the
     * faceplate is really added, which is also when a tooltip could
     * first be shown.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public void removeNotify() {
        javax.swing.ToolTipManager.sharedInstance().unregisterComponent(this);
        super.removeNotify();
    }

    /**
     * The full text whenever the glass had to cut it, so nothing the
     * device said is unreachable. Assistive technology already reads
     * the untruncated text through the accessible description.
     */
    @Override
    public String getToolTipText() {
        if (getWidth() <= 14) {
            return super.getToolTipText();   // not laid out yet
        }
        String full = shownText();
        java.awt.FontMetrics fm = getFontMetrics(RackStyle.LCD_FONT);
        boolean cut = false;
        for (String line : full.split("\n", -1)) {
            if (fm.stringWidth(line) > getWidth() - 14) {
                cut = true;
                break;
            }
        }
        if (cut) {
            return editable ? full + "  (double-click to edit)" : full;
        }
        return super.getToolTipText();
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
            String t = fit(text, w - 14, fm::stringWidth);
            g.drawString(t, 7, h / 2 + fm.getAscent() / 2 - 2);
        } else {
            List<Entry> snapshot;
            synchronized (buffer) {
                snapshot = new ArrayList<>(buffer);
            }
            int y = 4 + fm.getAscent();
            for (Entry entry : snapshot) {
                String t = fit(entry.text(), w - 14, fm::stringWidth);
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
