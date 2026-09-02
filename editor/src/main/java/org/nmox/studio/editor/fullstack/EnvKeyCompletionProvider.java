package org.nmox.studio.editor.fullstack;

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
import org.nmox.studio.editor.design.CssClassCompletionItem;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * {@code process.env.} / {@code import.meta.env.} completion from the
 * project's real env family (v2.31.0, the full-stack wishlist): the
 * keys with their declaring file — and a TRUNCATED value hint, because
 * an env file holds secrets and the popup is a reminder, not a
 * disclosure. Rides the design package's completion item (name left,
 * provenance right — the exact popup shape this needs).
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class, position = 575),
    @MimeRegistration(mimeType = "text/typescript", service = CompletionProvider.class, position = 575)
})
public class EnvKeyCompletionProvider implements CompletionProvider {

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
                int windowStart = Math.max(0, caret - 200);
                String before = doc.getText(windowStart, caret - windowStart);
                String prefix = EnvKeys.keyPrefix(before);
                if (prefix == null) {
                    return;
                }
                int anchor = caret - prefix.length();
                for (EnvKeys.EnvKey k : EnvKeys.scan(projectDir(doc))) {
                    if (k.name().startsWith(prefix)) {
                        result.addItem(new CssClassCompletionItem(
                                k.name(),
                                k.file().getName() + " · " + k.value(),
                                anchor, prefix.length()));
                    }
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
