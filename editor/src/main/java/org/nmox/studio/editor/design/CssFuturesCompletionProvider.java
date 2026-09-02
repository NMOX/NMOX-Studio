package org.nmox.studio.editor.design;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 * Completes the everyday-2031 CSS vocabulary the platform's property
 * database predates (see {@link CssFutures}): at a property-name
 * position it offers anchor positioning, view transitions,
 * scroll-driven animation, field-sizing and interpolate-size names
 * with their meanings; after one of those properties' colons it offers
 * that property's keyword values; {@code @starting-style} and its
 * siblings complete from a leading {@code @}. Across the whole
 * stylesheet family AND the markup family's style regions — the
 * v2.25.0 family law, gated through the shared HtmlStyleRegions so
 * prose can never receive CSS.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/css", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/scss", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/less", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-scss", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-sass", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/html", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-vue", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-svelte", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-ng-template", service = CompletionProvider.class, position = 552),
    @MimeRegistration(mimeType = "text/x-less", service = CompletionProvider.class, position = 552)
})
public class CssFuturesCompletionProvider implements CompletionProvider {

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        // COMPLETION (1) or COMPLETION_ALL (9): a second Ctrl+Space while the
        // popup shows re-queries as ALL, and an equality gate dropped every
        // NMOX item on that press (the v2.58.1 walk find, all 11 providers)
        if ((queryType & COMPLETION_QUERY_TYPE) == 0) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0; // ⌃Space, the proven path (the v1.333.0 recorded limit)
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet result, Document doc, int caret) {
            try {
                int windowStart = Math.max(0, caret - 300);
                String before = doc.getText(windowStart, caret - windowStart);
                if (HtmlStyleRegions.isMarkup(doc.getProperty("mimeType"))) {
                    String text = doc.getText(0, doc.getLength());
                    if (!HtmlStyleRegions.inStyle(text, caret)) {
                        return; // outside a style region, CSS is prose
                    }
                }
                CssFutures.ValueContext vc = CssFutures.valueContextAt(before);
                if (vc != null) {
                    int anchor = caret - vc.partial().length();
                    for (String v : vc.property().values()) {
                        if (v.startsWith(vc.partial())) {
                            result.addItem(new CssFuturesCompletionItem(v,
                                    vc.property().name(), anchor, vc.partial().length()));
                        }
                    }
                    return;
                }
                String prefix = CssFutures.propertyPrefixAt(before);
                if (prefix == null) {
                    return;
                }
                int anchor = caret - prefix.length();
                if (prefix.startsWith("@")) {
                    for (String rule : CssFutures.AT_RULES) {
                        if (rule.startsWith(prefix)) {
                            result.addItem(new CssFuturesCompletionItem(rule,
                                    "at-rule", anchor, prefix.length()));
                        }
                    }
                    return;
                }
                for (CssFutures.Property p : CssFutures.properties().values()) {
                    if (p.name().startsWith(prefix)) {
                        result.addItem(new CssFuturesCompletionItem(p.name(),
                                p.doc(), anchor, prefix.length()));
                    }
                }
            } catch (BadLocationException ignore) {
                // the document changed under the query; offer nothing
            } finally {
                result.finish();
            }
        }
    }
}
