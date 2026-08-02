package org.nmox.studio.editor.design;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.util.RequestProcessor;

/**
 * Inline color swatches for stylesheets (v1.227.0, the Senior Web
 * Designer pass): every color literal is painted AS its color — the
 * token's background becomes the color it names, with black or white
 * text chosen by luminance so it stays legible. `#336699` stops being
 * hex soup and becomes the blue it is, where it's written.
 *
 * <p>Same shape as {@link
 * org.nmox.studio.editor.occurrences.JsOccurrencesHighlighter}: a
 * mime-registered {@link HighlightsLayerFactory} owning an
 * {@link OffsetsBag}, rescans coalesced on a named RP (200 ms after
 * the last keystroke), text snapshot taken under {@code doc.render}.
 */
public final class CssColorHighlighter implements DocumentListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-css-colors", 1);

    private final Document doc;
    private final OffsetsBag bag;
    private RequestProcessor.Task pending;

    CssColorHighlighter(Document doc, OffsetsBag bag) {
        this.doc = doc;
        this.bag = bag;
        doc.addDocumentListener(this);
        schedule(0); // first paint without waiting for an edit
    }

    private void schedule(int delayMillis) {
        if (pending != null) {
            pending.cancel();
        }
        pending = RP.post(this::recompute, delayMillis);
    }

    void recompute() {
        final String[] text = new String[1];
        doc.render(() -> {
            try {
                text[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        OffsetsBag fresh = new OffsetsBag(doc);
        for (CssColors.ColorSpan span : CssColors.scan(text[0])) {
            AttributeSet attrs = AttributesUtilities.createImmutable(
                    StyleConstants.Background, opaque(span.color()),
                    StyleConstants.Foreground, CssColors.readableTextOn(opaque(span.color())));
            fresh.addHighlight(span.start(), span.end(), attrs);
        }
        bag.setHighlights(fresh);
    }

    /**
     * Test barrier: drains the recompute lane so a test never reads the
     * bag while a scheduled recompute is writing it (the awaitIdle
     * idiom; a torn concurrent read cost this test suite one flake).
     */
    static void awaitQuiet() {
        RP.post(() -> {
        }).waitFinished();
    }

    /** The preview is the opaque color; alpha blending in a text run is noise. */
    private static java.awt.Color opaque(java.awt.Color c) {
        return c.getAlpha() == 255 ? c
                : new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        schedule(200);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        schedule(200);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // attribute-only change: no text moved, spans still valid
    }

    // BOTH mime families: the ide cluster's css-prep resolver claims
    // .scss/.less as text/scss and text/less BEFORE our x- resolver
    // (found live in the v1.230.0 gauntlet — the x- registrations only
    // ever reach .sass files), so the swatches must ride the platform
    // mimes too or real stylesheets never see them.
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/css", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/scss", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/less", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/x-scss", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/x-less", service = HighlightsLayerFactory.class)
    })
    public static class Factory implements HighlightsLayerFactory {

        @Override
        public HighlightsLayer[] createLayers(Context context) {
            OffsetsBag bag = new OffsetsBag(context.getDocument());
            new CssColorHighlighter(context.getDocument(), bag);
            return new HighlightsLayer[]{
                // SHOW_OFF_RACK, not SYNTAX_RACK: the CSL/TextMate coloring
                // paints grammar-recognized literals (named colors, hex) at
                // the top of the syntax racks and was overriding the swatch —
                // only grammar-UNKNOWN literals showed one (found live in the
                // v1.229.0 gauntlet). Still below caret/selection racks, so
                // selecting a literal still looks selected.
                HighlightsLayer.create("nmox-css-colors",
                        ZOrder.SHOW_OFF_RACK.forPosition(100), true, bag)
            };
        }
    }
}
