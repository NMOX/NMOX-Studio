package org.nmox.studio.editor.occurrences;

import java.awt.Color;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;
import org.nmox.studio.editor.typescript.TypeScriptLanguage;
import org.openide.util.RequestProcessor;

/**
 * Mark occurrences: rest the caret on an identifier and every other
 * occurrence of that identifier lights up. Token-based, so text inside
 * strings and comments never matches.
 */
public class JsOccurrencesHighlighter implements CaretListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-js-occurrences", 1);
    private static final AttributeSet HIGHLIGHT = AttributesUtilities.createImmutable(
            StyleConstants.Background, new Color(0x3A, 0x43, 0x4F));

    private final JTextComponent component;
    private final OffsetsBag bag;
    private RequestProcessor.Task pending;

    private JsOccurrencesHighlighter(JTextComponent component, OffsetsBag bag) {
        this.component = component;
        this.bag = bag;
        component.addCaretListener(this);
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        if (pending != null) {
            pending.cancel();
        }
        final int dot = e.getDot();
        pending = RP.post(() -> recompute(dot), 200);
    }

    private void recompute(int caretOffset) {
        Document doc = component.getDocument();
        final String[] text = new String[1];
        doc.render(() -> {
            try {
                text[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        String source = text[0];
        String identifier = identifierAt(source, caretOffset);
        OffsetsBag fresh = new OffsetsBag(doc);
        if (identifier != null) {
            Language<JavaScriptTokenId> language = languageFor(doc);
            TokenSequence<JavaScriptTokenId> ts =
                    TokenHierarchy.create(source, language).tokenSequence(language);
            int count = 0;
            while (ts != null && ts.moveNext()) {
                if (ts.token().id() == JavaScriptTokenId.IDENTIFIER
                        && identifier.contentEquals(ts.token().text())) {
                    fresh.addHighlight(ts.offset(), ts.offset() + identifier.length(), HIGHLIGHT);
                    count++;
                }
            }
            if (count < 2) {
                fresh.clear(); // a lone occurrence is just noise
            }
        }
        bag.setHighlights(fresh);
    }

    private static Language<JavaScriptTokenId> languageFor(Document doc) {
        return "text/typescript".equals(doc.getProperty("mimeType"))
                ? TypeScriptLanguage.language() : JavaScriptTokenId.language();
    }

    /** The identifier the caret rests on or touches, else null. */
    static String identifierAt(String text, int offset) {
        if (text.isEmpty()) {
            return null;
        }
        int at = Math.min(offset, text.length() - 1);
        if (!isIdentChar(text.charAt(at)) && at > 0 && isIdentChar(text.charAt(at - 1))) {
            at--; // caret just after the word
        }
        if (!isIdentChar(text.charAt(at))) {
            return null;
        }
        int start = at;
        while (start > 0 && isIdentChar(text.charAt(start - 1))) {
            start--;
        }
        int end = at;
        while (end < text.length() - 1 && isIdentChar(text.charAt(end + 1))) {
            end++;
        }
        String word = text.substring(start, end + 1);
        return Character.isDigit(word.charAt(0)) ? null : word;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = HighlightsLayerFactory.class),
        @MimeRegistration(mimeType = "text/typescript", service = HighlightsLayerFactory.class)
    })
    public static class Factory implements HighlightsLayerFactory {
        @Override
        public HighlightsLayer[] createLayers(Context context) {
            OffsetsBag bag = new OffsetsBag(context.getDocument());
            new JsOccurrencesHighlighter(context.getComponent(), bag);
            return new HighlightsLayer[]{
                HighlightsLayer.create("nmox-js-occurrences",
                        ZOrder.SHOW_OFF_RACK.forPosition(20), true, bag)
            };
        }
    }
}
