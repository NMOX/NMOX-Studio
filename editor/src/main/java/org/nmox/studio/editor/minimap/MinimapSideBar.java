package org.nmox.studio.editor.minimap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
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

/**
 * The minimap: an East side bar painting the document's silhouette
 * (one bar per line, indent to length) with the editor's visible band
 * over it; click or drag scrolls the editor to the line under the mouse.
 *
 * <p>Laws carried from day one: every listener attaches in addNotify and
 * detaches in removeNotify (the v1.44.0 symmetry law); document edits
 * coalesce through one 120 ms Swing timer so a typing burst reshapes once;
 * the shape is read under {@code Document.render} and bounded by
 * {@link MinimapModel#MAX_LINES}; painting never touches the document —
 * it paints the last shape, so a paint during an edit storm is cheap and
 * consistent. The strip hides itself when the View ▸ Minimap preference
 * is off (a hidden BoxLayout child costs no width), so the toggle needs
 * no editor reopen.
 */
public final class MinimapSideBar extends JComponent {

    /** Strip width in pixels: enough for a 120-column silhouette to read. */
    static final int WIDTH = 84;

    private final JTextComponent target;
    private final Timer reshape = new Timer(120, e -> reshapeNow());
    private MinimapModel.Shapes shapes = MinimapModel.shape("");
    private Document listened;

    private final DocumentListener docListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            reshape.restart();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            reshape.restart();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            // attribute-only; the silhouette is text-only
        }
    };
    private final PropertyChangeListener docSwap = e -> {
        if ("document".equals(e.getPropertyName())) {
            relisten();
            reshape.restart();
        }
    };
    private final ChangeListener viewportMoved = e -> repaint();
    private final PreferenceChangeListener prefChanged = e -> {
        if (MinimapPrefs.KEY.equals(e.getKey())) {
            applyEnabled();
        }
    };
    private JViewport viewport;

    MinimapSideBar(JTextComponent target) {
        this.target = target;
        reshape.setRepeats(false);
        setOpaque(true);
        setToolTipText("Minimap — click to scroll");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                scrollTo(e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                scrollTo(e.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        applyEnabled();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, target.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(WIDTH, 0);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(WIDTH, Integer.MAX_VALUE);
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
        MinimapPrefs.prefs().addPreferenceChangeListener(prefChanged);
        reshape.restart();
    }

    @Override
    public void removeNotify() {
        reshape.stop();
        MinimapPrefs.prefs().removePreferenceChangeListener(prefChanged);
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

    private void applyEnabled() {
        boolean on = MinimapPrefs.enabled();
        if (on != isVisible()) {
            setVisible(on);
            revalidate();
        }
    }

    private void reshapeNow() {
        Document doc = target.getDocument();
        if (doc == null) {
            shapes = MinimapModel.shape("");
            repaint();
            return;
        }
        final String[] text = new String[1];
        doc.render(() -> {
            try {
                Element root = doc.getDefaultRootElement();
                int lines = root.getElementCount();
                int end = lines > MinimapModel.MAX_LINES
                        ? root.getElement(MinimapModel.MAX_LINES).getStartOffset() + 1
                        : doc.getLength();
                text[0] = doc.getText(0, Math.min(end, doc.getLength()));
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        shapes = MinimapModel.shape(text[0]);
        repaint();
    }

    /** The editor's visible line range, {@code {first, last}}. */
    private int[] visibleLines() {
        Document doc = target.getDocument();
        if (doc == null || viewport == null) {
            return new int[] {0, 0};
        }
        Rectangle r = viewport.getViewRect();
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(target.viewToModel2D(new java.awt.Point(0, r.y)));
        int last = root.getElementIndex(target.viewToModel2D(new java.awt.Point(0, r.y + r.height - 1)));
        return new int[] {first, Math.max(first, last)};
    }

    private void scrollTo(int y) {
        Document doc = target.getDocument();
        if (doc == null) {
            return;
        }
        double row = MinimapModel.rowHeight(shapes.lines(), getHeight());
        int line = MinimapModel.lineAt(y, row, shapes.lines());
        Element root = doc.getDefaultRootElement();
        if (line >= root.getElementCount()) {
            return;
        }
        try {
            Rectangle2D at = target.modelToView2D(root.getElement(line).getStartOffset());
            if (at == null) {
                return;
            }
            int viewH = viewport != null ? viewport.getExtentSize().height : getHeight();
            Rectangle centered = new Rectangle(0, (int) at.getY() - viewH / 2, 1, viewH);
            target.scrollRectToVisible(centered);
        } catch (BadLocationException ignore) {
            // the document changed under the click; nothing to scroll to
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            Color bg = target.getBackground() != null ? target.getBackground() : getBackground();
            Color fg = target.getForeground() != null ? target.getForeground() : Color.GRAY;
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int lines = shapes.lines();
            double row = MinimapModel.rowHeight(lines, getHeight());
            int barH = Math.max(1, (int) Math.floor(row));
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 96));
            int[] ind = shapes.indents();
            int[] len = shapes.lengths();
            for (int i = 0; i < lines; i++) {
                int[] bar = MinimapModel.bar(ind[i], len[i], getWidth() - 4);
                if (bar[1] > 0) {
                    g2.fillRect(2 + bar[0], MinimapModel.yOf(i, row), bar[1], barH);
                }
            }
            int[] vis = visibleLines();
            int top = MinimapModel.yOf(vis[0], row);
            int bottom = MinimapModel.yOf(vis[1] + 1, row);
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 28));
            g2.fillRect(0, top, getWidth(), Math.max(2, bottom - top));
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 70));
            g2.drawRect(0, top, getWidth() - 1, Math.max(2, bottom - top) - 1);
            if (shapes.truncated()) {
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 140));
                g2.drawString("…", 2, getHeight() - 3);
            }
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
                    return "Minimap";
                }

                @Override
                public String getAccessibleDescription() {
                    return "Document overview; click or drag to scroll the editor";
                }
            };
        }
        return accessibleContext;
    }
}
