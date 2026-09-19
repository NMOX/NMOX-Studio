package org.nmox.studio.editor.importmap;

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
import org.nmox.studio.editor.ProjectRoot;
import org.nmox.studio.editor.importmap.ImportMaps.PageMap;

/**
 * Inside an open import quote in JS/TS, offer the page's mapped
 * specifiers (futures-2031 F3): {@code import … from '<⌃Space>'}
 * lists what the import map actually resolves, each with its target
 * as provenance. Outside an import quote this provider stays silent —
 * the map has no opinion elsewhere.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class, position = 576),
    @MimeRegistration(mimeType = "text/typescript", service = CompletionProvider.class, position = 576)
})
public class ImportMapCompletionProvider implements CompletionProvider {

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
                String prefix = ImportMaps.specifierPrefixAt(before);
                if (prefix == null) {
                    return;
                }
                PageMap map = ImportMaps.findProjectMap(ProjectRoot.of(doc));
                if (map == null) {
                    return;
                }
                int start = caret - prefix.length();
                for (var e : map.imports().entrySet()) {
                    if (e.getKey().startsWith(prefix)) {
                        result.addItem(new ImportMapCompletionItem(
                                e.getKey(), e.getValue(), start, prefix.length()));
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
