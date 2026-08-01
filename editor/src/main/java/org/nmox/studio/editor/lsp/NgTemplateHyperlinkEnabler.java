package org.nmox.studio.editor.lsp;

import java.util.EnumSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

/**
 * Template go-to-declaration (v1.219.0): claims an identifier span in
 * {@code .component.html} and delegates the actual navigation to the
 * platform LSP client's hyperlink provider — which asks ngserver for
 * {@code textDocument/definition} and opens the result.
 *
 * <p><b>Why delegation instead of the normal chain.</b> The LSP
 * client's own provider (registered for every mime at the Editors
 * root) declines on this document: wire capture showed
 * {@code textDocument/definition} never sent, and its span logic gates
 * on client-internal state this module cannot see. Its
 * {@code performClickAction}, however, takes the document + offset and
 * performs the request unconditionally (decompiled). So this provider
 * answers the cheap questions itself — is the caret in an identifier,
 * what are its bounds — and hands the click to the client's provider,
 * which owns the server connection and the navigation.
 */
@MimeRegistration(mimeType = "text/x-ng-template",
        service = HyperlinkProviderExt.class, position = 100)
public class NgTemplateHyperlinkEnabler implements HyperlinkProviderExt {

    private static final String LSP_CLIENT_PACKAGE = "org.netbeans.modules.lsp.client";

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        return getHyperlinkSpan(doc, offset, type) != null && lspProvider() != null;
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        return identifierSpan(doc, offset);
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        HyperlinkProviderExt lsp = lspProvider();
        if (lsp != null) {
            lsp.performClickAction(doc, offset, type);
        }
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        return null;
    }

    /** The platform LSP client's provider, found beside us in MimeLookup. */
    static HyperlinkProviderExt lspProvider() {
        for (HyperlinkProviderExt provider : MimeLookup
                .getLookup(MimePath.parse("text/x-ng-template"))
                .lookupAll(HyperlinkProviderExt.class)) {
            if (provider.getClass().getName().startsWith(LSP_CLIENT_PACKAGE)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * The identifier under {@code offset}, or null when the caret sits
     * outside a word. Pure text scan (letters, digits, {@code _},
     * {@code $}) so it needs nothing from the lexer or the client.
     */
    static int[] identifierSpan(Document doc, int offset) {
        try {
            int length = doc.getLength();
            if (offset < 0 || offset > length) {
                return null;
            }
            int lineStart = Math.max(0, offset - 200);
            int lineEnd = Math.min(length, offset + 200);
            String text = doc.getText(lineStart, lineEnd - lineStart);
            int at = offset - lineStart;
            int start = at;
            while (start > 0 && isIdentChar(text.charAt(start - 1))) {
                start--;
            }
            int end = at;
            while (end < text.length() && isIdentChar(text.charAt(end))) {
                end++;
            }
            if (start == end) {
                return null;
            }
            return new int[]{lineStart + start, lineStart + end};
        } catch (BadLocationException ex) {
            return null;
        }
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
