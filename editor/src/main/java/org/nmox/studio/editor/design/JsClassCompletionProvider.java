package org.nmox.studio.editor.design;

import java.io.File;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * The class arc's JavaScript reach (v2.30.0): inside
 * {@code querySelector('.…')} — and the selector/classList families —
 * completion offers the classes the project's stylesheets actually
 * declare, each item naming its declaring file. The third language of
 * the trio: markup and stylesheets already navigate classes both
 * directions; this is the same {@link CssClasses} truth offered where
 * scripts type them blind. Arbitrary strings never trigger — only the
 * recognized call shapes ({@code fetch('.card')} stays a URL).
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class, position = 570),
    @MimeRegistration(mimeType = "text/typescript", service = CompletionProvider.class, position = 570)
})
public class JsClassCompletionProvider implements CompletionProvider {

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
                String prefix = CssClasses.jsClassPrefix(before);
                if (prefix == null) {
                    return;
                }
                for (CssClassCompletionItem item
                        : CssClassCompletionProvider.itemsFor(
                                prefix, "", false, projectDir(doc), caret)) {
                    result.addItem(item);
                }
            } catch (BadLocationException ignore) {
                // the document changed under the query; offer nothing
            } finally {
                result.finish();
            }
        }

        private static File projectDir(Document doc) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            File f = fo == null ? null : FileUtil.toFile(fo);
            if (f == null) {
                return null;
            }
            File dir = f.getParentFile();
            File cursor = dir;
            for (int up = 0; cursor != null && up < 6; up++, cursor = cursor.getParentFile()) {
                if (new File(cursor, "package.json").isFile()
                        || new File(cursor, "angular.json").isFile()
                        || new File(cursor, ".git").exists()) {
                    return cursor;
                }
            }
            return dir;
        }
    }
}
