package org.nmox.studio.editor.ghost;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.awt.StatusDisplayer;

/**
 * The ghost: an KVASIR completion shown as virtual gray text at the caret
 * until Tab inserts it or any edit, caret move or click dismisses it.
 *
 * <p>Rendering rides the platform's own virtual-text mechanism, decompiled
 * from editor-lib2: a highlight carrying the {@code virtual-text-prepend}
 * attribute makes the view factory wrap the view at that offset in a
 * PrependedTextView that paints the string before the real character. So
 * the ghost is a one-character highlight at the caret whose prepended text
 * is the completion's first line (the rest is counted, "+N lines", and
 * inserted whole on Tab). The document is never touched until Tab — a
 * ghost that was never accepted leaves no undo step and no edit.
 *
 * <p>Dismissal is total and symmetric: Tab accepts; any caret move or
 * document change dismisses; the key, caret and document listeners are
 * installed only while a ghost is armed and removed with it (the v1.44.0
 * symmetry law).
 */
public final class GhostText {

    /** The platform's prepend attribute (editor-lib2 HighlightsViewFactory). */
    static final String VIRTUAL_TEXT_PREPEND = "virtual-text-prepend";
    private static final Object KEY = new Object();

    private final JTextComponent component;
    private final OffsetsBag bag;
    private String insertion;
    private int offset = -1;
    private final KeyAdapter keys = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (insertion == null) {
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_TAB && e.getModifiersEx() == 0) {
                e.consume();
                accept();
            }
        }
    };
    // Escape is deliberately NOT a dismiss key: inside a docked TopComponent
    // the window system's own KeyEventDispatcher consumes it before any
    // component listener OR a later-registered dispatcher sees it (the
    // v1.205.0 law, re-measured on this unit's walk with a probe — zero
    // Escape events reached a dispatcher added at arm time). Dismissal is
    // therefore what the editor cannot swallow: any edit, a caret move,
    // a click — and Tab accepts.
    private final CaretListener caret = new CaretListener() {
        @Override
        public void caretUpdate(CaretEvent e) {
            if (insertion != null && e.getDot() != offset) {
                dismiss("");
            }
        }
    };
    private final DocumentListener edits = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            dismiss("");
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            dismiss("");
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
        }
    };

    private GhostText(JTextComponent component, OffsetsBag bag) {
        this.component = component;
        this.bag = bag;
    }

    /** The ghost attached to a pane, if the layer factory has been asked for it. */
    public static GhostText of(JTextComponent component) {
        Object g = component.getClientProperty(KEY);
        return g instanceof GhostText ? (GhostText) g : null;
    }

    /**
     * Arms a ghost at the caret. EDT. At the very end of the document there
     * is no character to prepend to, so the insertion is applied directly
     * and selected instead — one undo step, said out loud.
     */
    public void arm(String text) {
        dismiss("");
        Document doc = component.getDocument();
        int at = component.getCaretPosition();
        if (doc == null || text == null || text.isEmpty()) {
            return;
        }
        if (at >= doc.getLength()) {
            insertNow(at, text);
            component.select(at, at + text.length());
            StatusDisplayer.getDefault().setStatusText(
                    "KVASIR completion inserted at the end of the file — ⌘Z removes it.");
            return;
        }
        this.insertion = text;
        this.offset = at;
        String first = firstLine(text);
        int more = moreLines(text);
        String shown = more > 0 ? first + "  … +" + more + " line" + (more == 1 ? "" : "s") : first;
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        attrs.addAttribute(VIRTUAL_TEXT_PREPEND, shown);
        Color fg = component.getForeground() == null ? Color.GRAY : component.getForeground();
        StyleConstants.setForeground(attrs, new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120));
        bag.clear();
        bag.addHighlight(at, at + 1, attrs);
        component.addKeyListener(keys);
        component.addCaretListener(caret);
        doc.addDocumentListener(edits);
        StatusDisplayer.getDefault().setStatusText(
                "KVASIR completion — Tab inserts" + (more > 0 ? " all " + (more + 1) + " lines" : "")
                + "; typing or moving the caret dismisses.");
    }

    /** Whether a ghost is armed on this pane. */
    public boolean armed() {
        return insertion != null;
    }

    private void accept() {
        String text = insertion;
        int at = offset;
        detach();
        if (text != null && at >= 0) {
            insertNow(at, text);
            StatusDisplayer.getDefault().setStatusText("KVASIR completion inserted — ⌘Z removes it.");
        }
    }

    private void insertNow(int at, String text) {
        Document doc = component.getDocument();
        Runnable r = () -> {
            try {
                doc.insertString(at, text, null);
                component.setCaretPosition(Math.min(at + text.length(), doc.getLength()));
            } catch (BadLocationException ignore) {
                // the document changed under the accept; nothing inserted
            }
        };
        if (doc instanceof org.netbeans.editor.BaseDocument bd) {
            bd.runAtomicAsUser(r);
        } else {
            r.run();
        }
    }

    /** Drops the ghost; {@code note} on the status line when non-empty. */
    public void dismiss(String note) {
        if (insertion == null) {
            return;
        }
        detach();
        if (!note.isEmpty()) {
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(note));
        }
    }

    private void detach() {
        insertion = null;
        offset = -1;
        bag.clear();
        component.removeKeyListener(keys);
        component.removeCaretListener(caret);
        Document doc = component.getDocument();
        if (doc != null) {
            doc.removeDocumentListener(edits);
        }
    }

    static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    static int moreLines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /**
     * One ghost layer per editor pane, every mime (root registration). No
     * position on purpose: the root folder's platform rows are unpositioned,
     * and a lone positioned row makes the platform warn on every boot for
     * every mime (the v2.28.0 quieter-boot law, measured on the first walk).
     */
    @MimeRegistration(mimeType = "", service = HighlightsLayerFactory.class)
    public static final class Factory implements HighlightsLayerFactory {
        @Override
        public HighlightsLayer[] createLayers(Context context) {
            OffsetsBag bag = new OffsetsBag(context.getDocument());
            JTextComponent component = context.getComponent();
            GhostText ghost = new GhostText(component, bag);
            component.putClientProperty(KEY, ghost);
            // fixedSize=FALSE is load-bearing: a fixed-size layer is merged
            // as a colors-only pass and its attributes never reach the view
            // factory that honors virtual-text-prepend (the probe walk armed
            // a ghost at the right offset and nothing painted until this)
            return new HighlightsLayer[] {
                HighlightsLayer.create("nmox-ghost-text", ZOrder.SHOW_OFF_RACK.forPosition(560), false, bag)
            };
        }
    }
}
