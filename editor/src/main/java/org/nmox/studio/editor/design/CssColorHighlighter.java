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
        // no removeDocumentListener anywhere, ON PURPOSE (the
        // JsOccurrencesHighlighter idiom, said out loud per the v1.234.0
        // review): the only strong holders of this object are the
        // document itself and a briefly-pending RP task, so it is
        // collected WITH the document when the last editor clone closes.
        // A remove path would need a disposal hook the HighlightsLayer
        // SPI doesn't offer.
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
        java.util.List<CssColors.ColorSpan> spans;
        if ("text/html".equals(doc.getProperty("mimeType"))) {
            // v2.22.0: in HTML, bare `tomato` is usually PROSE — only
            // <style> blocks and style="…" attribute values may swatch
            spans = new java.util.ArrayList<>(HtmlStyleRegions.scan(text[0]));
        } else {
            spans = new java.util.ArrayList<>(CssColors.scan(text[0]));
            // v1.330.0: var(--token) usages paint as the color their token
            // declares — the indirection resolved document-locally, so the
            // recompute lane still never touches disk
            spans.addAll(CssTokens.varUsageColorSpans(text[0]));
        }
        for (CssColors.ColorSpan span : spans) {
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
        @MimeRegistration(mimeType = "text/html", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/css", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/scss", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/less", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/x-scss", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/x-sass", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/x-less", service = HighlightsLayerFactory.class)
    })
    public static class Factory implements HighlightsLayerFactory {

        @Override
        public HighlightsLayer[] createLayers(Context context) {
            OffsetsBag bag = new OffsetsBag(context.getDocument());
            new CssColorHighlighter(context.getDocument(), bag);
            return new HighlightsLayer[]{
                // SHOW_OFF_RACK.forPosition(430) — placed by decompiled
                // evidence, not hope (v1.234.0 review). The three layers
                // this must sit AMONG: the CSL/TextMate syntax coloring
                // (SYNTAX rack — below everything here; it overrode the
                // swatch in v1.229.0 until we left that rack), the hints
                // error/warning background (SHOW_OFF 420, decompiled from
                // org-netbeans-spi-editor-hints HighlightsLayerFactoryImpl
                // — the legacy CSS grammar flags modern color syntax and
                // its background hid the swatches, the v1.231.0 find), and
                // TEXT SELECTION (SHOW_OFF 500, decompiled from editor-lib2
                // Factory). 430 beats the warnings and stays under the
                // selection — v1.231.0's TOP_RACK(100) beat the warnings
                // but ALSO painted over the selection band, so selecting a
                // rule punched an unselected-looking hole at every color
                // literal (and tied the platform's caret-overwrite layer at
                // TOP 100, an unspecified order). A z-order comment is a
                // claim about a total order — this one cites its anchors.
                HighlightsLayer.create("nmox-css-colors",
                        ZOrder.SHOW_OFF_RACK.forPosition(430), true, bag)
            };
        }
    }
}
