package org.nmox.studio.editor.i18n;

import java.io.File;
import java.util.Map;

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
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.editor.design.CssClassCompletionItem;
import org.nmox.studio.editor.ProjectRoot;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;

/**
 * Translation-key completion inside a lookup ({@code t('}, {@code $t('},
 * {@code data-i18n="}, {@code i18nKey="}, {@code <FormattedMessage id="},
 * {@code m.}…) from the project's SOURCE catalog (v2.177.0): every key
 * with its catalog file on the right and the source value beside it,
 * truncated the way an env value is — the popup is a reminder of what
 * the key says, not a place to read the catalog. Rides the design
 * package's completion item (name left, provenance right). The catalog
 * read happens on the completion thread, never the EDT, and is cached
 * per file by mtime and size, so a repeat costs stats, not parses.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class, position = 580),
    @MimeRegistration(mimeType = "text/typescript", service = CompletionProvider.class, position = 580),
    @MimeRegistration(mimeType = "text/html", service = CompletionProvider.class, position = 580),
    @MimeRegistration(mimeType = "text/x-vue", service = CompletionProvider.class, position = 580),
    @MimeRegistration(mimeType = "text/x-svelte", service = CompletionProvider.class, position = 580),
    @MimeRegistration(mimeType = "text/x-ng-template", service = CompletionProvider.class, position = 580)
})
public class I18nKeyCompletionProvider implements CompletionProvider {

    /** The value shown beside a key: this many chars, then an ellipsis. */
    static final int VALUE_CHARS = 24;

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        // COMPLETION (1) or COMPLETION_ALL (9): a second Ctrl+Space while the
        // popup shows re-queries as ALL, and an equality gate drops every
        // NMOX item on that press (the v2.58.1 walk find)
        if ((queryType & COMPLETION_QUERY_TYPE) == 0) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0; // ⌃Space, the proven path (the v1.333.0 recorded limit)
    }

    /** The provenance text for one key: {@code en/common.json · "Hello!"}, markup-safe. */
    static String provenance(Catalog catalog, Entry entry, boolean namespaced) {
        String file = catalog.file().getFileName().toString();
        if (namespaced && catalog.file().getParent() != null) {
            file = catalog.file().getParent().getFileName() + "/" + file;
        }
        String value = entry.value().replace('\n', ' ');
        if (value.length() > VALUE_CHARS) {
            value = value.substring(0, value.offsetByCodePoints(0,
                    Math.min(value.codePointCount(0, value.length()), VALUE_CHARS - 3))) + "…";
        }
        // the item renders its right column as HTML: a catalog value is the
        // user's text and must never become markup (the v2.86.0 class)
        return PlainText.escape(file + " · \"" + value + "\"");
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet result, Document doc, int caret) {
            try {
                int windowStart = Math.max(0, caret - 300);
                String before = doc.getText(windowStart, caret - windowStart);
                String prefix = I18nKeys.keyPrefix(before);
                if (prefix == null) {
                    return;
                }
                File root = ProjectRoot.of(doc);
                if (root == null) {
                    return;
                }
                Catalogs catalogs;
                try {
                    catalogs = I18nCatalogs.detect(root.toPath());
                } catch (I18nCatalogs.ParseFailure unreadable) {
                    return;           // the Tools door names the file; a popup cannot
                }
                if (catalogs == null) {
                    return;
                }
                int anchor = caret - prefix.length();
                String[] wanted = I18nKeys.split(prefix);
                boolean namespaced = catalogs.format() == I18nCatalogs.Format.I18NEXT_NESTED;
                for (Catalog catalog : catalogs.source()) {
                    if (wanted[0] != null && !catalog.namespace().equals(wanted[0])) {
                        continue;
                    }
                    for (Map.Entry<String, Entry> e : catalog.entries().entrySet()) {
                        if (!e.getKey().startsWith(wanted[1])) {
                            continue;
                        }
                        String name = wanted[0] == null ? e.getKey() : wanted[0] + ":" + e.getKey();
                        result.addItem(new CssClassCompletionItem(name,
                                provenance(catalog, e.getValue(), namespaced), anchor, prefix.length()));
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
