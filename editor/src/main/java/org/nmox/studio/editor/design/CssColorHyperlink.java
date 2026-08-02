package org.nmox.studio.editor.design;

import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.openide.text.NbDocument;
import org.openide.windows.WindowManager;

/**
 * The color picker (v1.229.0, the Senior Web Designer pass): ⌘-click
 * a color literal in a stylesheet and a chooser opens seeded with
 * that color; picking replaces the literal IN ITS AUTHORED FORM —
 * hex stays hex, {@code rgb()} stays {@code rgb()}, {@code hsl()}
 * stays {@code hsl()} — as one undoable edit. Rides the hyperlink
 * gesture the editor already owns (the {@code NgTemplateHyperlinkEnabler}
 * idiom, v1.219.0), so the affordance is discoverable the same way
 * go-to-declaration is: hover with ⌘ held and the literal underlines.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/css", service = HyperlinkProviderExt.class, position = 150),
    // text/scss + text/less are the css-prep mimes real .scss/.less
    // files resolve to; the x- pair only reaches .sass (v1.230.0 find)
    @MimeRegistration(mimeType = "text/scss", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/less", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/x-scss", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/x-less", service = HyperlinkProviderExt.class, position = 150)
})
public final class CssColorHyperlink implements HyperlinkProviderExt {

    /** Stylesheets are small; refuse to scan absurd documents. */
    private static final int MAX_SCAN_CHARS = 500_000;

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        return spanAt(doc, offset) != null;
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        CssColors.ColorSpan span = spanAt(doc, offset);
        return span == null ? null : new int[]{span.start(), span.end()};
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        return "Pick a color (replaces this literal)";
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        CssColors.ColorSpan span = spanAt(doc, offset);
        if (span == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> pickAndReplace(doc, span));
    }

    /** EDT. Chooser → atomic in-form replacement; cancel changes nothing. */
    private static void pickAndReplace(Document doc, CssColors.ColorSpan span) {
        Color picked = javax.swing.JColorChooser.showDialog(
                WindowManager.getDefault().getMainWindow(),
                "Pick a color", opaque(span.color()));
        if (picked == null) {
            return; // canceled
        }
        try {
            String original = doc.getText(span.start(), span.end() - span.start());
            // the document may have changed while the chooser was open —
            // replace only if the literal is still exactly where it was
            List<CssColors.ColorSpan> now = CssColors.scan(
                    doc.getText(0, doc.getLength()));
            boolean stillThere = now.stream().anyMatch(s
                    -> s.start() == span.start() && s.end() == span.end());
            if (!stillThere) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        "Color not replaced — the stylesheet changed while the picker was open");
                return;
            }
            String replacement = CssColors.format(picked, original);
            Runnable edit = () -> {
                try {
                    doc.remove(span.start(), span.end() - span.start());
                    doc.insertString(span.start(), replacement, null);
                } catch (BadLocationException ex) {
                    // the guarded re-scan above makes this unreachable in
                    // practice; losing the edit beats corrupting the file
                }
            };
            if (doc instanceof StyledDocument styled) {
                NbDocument.runAtomicAsUser(styled, edit); // one undo unit
            } else {
                edit.run();
            }
        } catch (BadLocationException ex) {
            // stale offsets: drop the edit, never corrupt
        }
    }

    private static Color opaque(Color c) {
        return c.getAlpha() == 255 ? c
                : new Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    /** The color literal containing {@code offset}, else null. */
    static CssColors.ColorSpan spanAt(Document doc, int offset) {
        if (doc.getLength() > MAX_SCAN_CHARS) {
            return null;
        }
        final String[] text = new String[1];
        doc.render(() -> {
            try {
                text[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        // NARROWEST containing span: clicking `tomato` inside a
        // color-mix(...) should pick the inner literal, not the recipe
        CssColors.ColorSpan best = null;
        for (CssColors.ColorSpan span : CssColors.scan(text[0])) {
            if (offset >= span.start() && offset < span.end()
                    && (best == null || span.end() - span.start() < best.end() - best.start())) {
                best = span;
            }
        }
        return best;
    }

}
