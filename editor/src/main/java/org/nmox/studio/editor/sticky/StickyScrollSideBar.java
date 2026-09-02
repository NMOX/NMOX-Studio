package org.nmox.studio.editor.sticky;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.PreferenceChangeListener;
import javax.accessibility.AccessibleContext;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.nmox.studio.editor.outline.OutlineModel;
import org.nmox.studio.editor.outline.StickyScope;

/**
 * Sticky scroll: a North side bar that pins the declarations enclosing the
 * first visible line — the class and method you scrolled out of sight —
 * as up to three rows of the source's own text in the editor's font,
 * aligned past the gutter; click a row to jump to that line.
 *
 * <p>Laws: listeners attach in addNotify and detach in removeNotify;
 * the outline is re-extracted through one 200 ms timer on edits (read
 * under Document.render, bounded by the outline's own caps); scrolling
 * only re-selects the chain from the cached items; the bar collapses to
 * zero height when nothing encloses the top line or the View ▸ Sticky
 * Scroll preference is off, so it costs nothing when it says nothing.
 */
public final class StickyScrollSideBar extends JComponent {

    static final int MAX_ROWS = 3;
    /** Lines past which the document is not read — the outline's own cap. */
    static final int MAX_LINES = 50_000;

    private final JTextComponent target;
    private final Timer refresh = new Timer(200, e -> reindexNow());
    private List<OutlineModel.Item> items = List.of();
    private int[] ends = new int[0];
    private List<String> lines = List.of();
    private List<OutlineModel.Item> chain = List.of();
    private Document listened;
    private JViewport viewport;

    private final DocumentListener docListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            refresh.restart();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            refresh.restart();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }
    };
    private final PropertyChangeListener docSwap = e -> {
        if ("document".equals(e.getPropertyName())) {
            relisten();
            refresh.restart();
        }
    };
    private final ChangeListener viewportMoved = e -> rechain();
    private final PreferenceChangeListener prefChanged = e -> {
        if (StickyPrefs.KEY.equals(e.getKey())) {
            rechain();
        }
    };

    StickyScrollSideBar(JTextComponent target) {
        this.target = target;
        refresh.setRepeats(false);
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = e.getY() / Math.max(1, rowHeight());
                if (row >= 0 && row < chain.size()) {
                    jumpTo(chain.get(row).line());
                }
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        target.addPropertyChangeListener(docSwap);
        relisten();
        viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, target);
        if (viewport != null) {
            viewport.addChangeListener(viewportMoved);
        }
        StickyPrefs.prefs().addPreferenceChangeListener(prefChanged);
        refresh.restart();
    }

    @Override
    public void removeNotify() {
        refresh.stop();
        StickyPrefs.prefs().removePreferenceChangeListener(prefChanged);
        if (viewport != null) {
            viewport.removeChangeListener(viewportMoved);
            viewport = null;
        }
        if (listened != null) {
            listened.removeDocumentListener(docListener);
            listened = null;
        }
        target.removePropertyChangeListener(docSwap);
        super.removeNotify();
    }

    private void relisten() {
        Document now = target.getDocument();
        if (now == listened) {
            return;
        }
        if (listened != null) {
            listened.removeDocumentListener(docListener);
        }
        listened = now;
        if (listened != null) {
            listened.addDocumentListener(docListener);
        }
    }

    private String mime() {
        Document doc = target.getDocument();
        Object m = doc == null ? null : doc.getProperty("mimeType");
        return m == null ? "" : m.toString();
    }

    private void reindexNow() {
        Document doc = target.getDocument();
        if (doc == null) {
            items = List.of();
            ends = new int[0];
            lines = List.of();
            rechain();
            return;
        }
        final String[] text = new String[1];
        doc.render(() -> {
            try {
                // bounded like the minimap's read: the outline itself shapes
                // at most MAX_LINES lines, so copying past them is pure cost
                Element root = doc.getDefaultRootElement();
                int end = root.getElementCount() > MAX_LINES
                        ? root.getElement(MAX_LINES).getStartOffset()
                        : doc.getLength();
                text[0] = doc.getText(0, Math.min(end, doc.getLength()));
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        String mime = mime();
        items = OutlineModel.extract(mime, text[0]);
        lines = Arrays.asList(text[0].split("\n", -1));
        ends = StickyScope.endLines(OutlineModel.familyOf(mime), lines, items);
        rechain();
    }

    private void rechain() {
        List<OutlineModel.Item> next = List.of();
        if (StickyPrefs.enabled() && viewport != null && !items.isEmpty()) {
            Document doc = target.getDocument();
            if (doc != null) {
                Rectangle r = viewport.getViewRect();
                int first = doc.getDefaultRootElement()
                        .getElementIndex(target.viewToModel2D(new Point(0, r.y)));
                // the rows themselves cover the top of the viewport: chain
                // against the first line that will still be visible BELOW them
                List<OutlineModel.Item> probe = StickyScope.enclosing(items, ends, first, MAX_ROWS);
                int covered = first + probe.size();
                next = StickyScope.enclosing(items, ends, covered, MAX_ROWS);
                if (!next.isEmpty() && next.get(next.size() - 1).line() >= first) {
                    // the innermost header IS still on screen: pinning it would
                    // show the same line twice
                    next = new ArrayList<>(next);
                    while (!next.isEmpty() && next.get(next.size() - 1).line() >= first) {
                        next.remove(next.size() - 1);
                    }
                }
            }
        }
        if (!next.equals(chain)) {
            chain = next;
            revalidate();
            repaint();
        }
    }

    private void jumpTo(int line) {
        Document doc = target.getDocument();
        if (doc == null) {
            return;
        }
        Element root = doc.getDefaultRootElement();
        if (line >= root.getElementCount()) {
            return;
        }
        try {
            int offset = root.getElement(line).getStartOffset();
            Rectangle2D at = target.modelToView2D(offset);
            if (at != null) {
                target.setCaretPosition(offset);
                target.scrollRectToVisible(new Rectangle(0, (int) at.getY(), 1,
                        viewport != null ? viewport.getExtentSize().height : getHeight()));
            }
        } catch (BadLocationException ignore) {
            // the document changed under the click
        }
    }

    private int rowHeight() {
        FontMetrics fm = target.getFontMetrics(target.getFont());
        return fm.getHeight();
    }

    /** Horizontal offset of the text past the gutter, in this bar's space. */
    private int textInset() {
        if (viewport == null) {
            return 0;
        }
        Point p = SwingUtilities.convertPoint(viewport, 0, 0, this);
        return Math.max(0, p.x) + target.getInsets().left;
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = chain.size();
        return new Dimension(0, rows == 0 ? 0 : rows * rowHeight() + 1);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (chain.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color bg = target.getBackground() != null ? target.getBackground() : getBackground();
            Color fg = target.getForeground() != null ? target.getForeground() : Color.GRAY;
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Font font = target.getFont();
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int rowH = fm.getHeight();
            int x = textInset();
            for (int i = 0; i < chain.size(); i++) {
                int line = chain.get(i).line();
                String text = line < lines.size() ? lines.get(line) : chain.get(i).name();
                g2.setColor(fg);
                g2.drawString(text.stripTrailing(), x, i * rowH + fm.getAscent());
            }
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60));
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public String getAccessibleName() {
                    return "Sticky scroll";
                }

                @Override
                public String getAccessibleDescription() {
                    return "The declarations enclosing the top of the view; click a row to jump to it";
                }
            };
        }
        return accessibleContext;
    }
}
