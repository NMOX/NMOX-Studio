package org.nmox.studio.editor.design;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Design-token completion (v1.330.0): type {@code var(} in any
 * stylesheet and the project's custom properties appear — name, value,
 * and a swatch when the value is a color. Providers COMPOSE via
 * lookupAll (the v1.213.0 arrangement), so this rides beside the
 * existing property/value completion rather than replacing it.
 *
 * <p>Document-local tokens list first (they win the cascade a designer
 * is looking at); the rest of the project follows through {@link
 * CssTokens}' bounded, cached scan — which runs HERE, on the async
 * completion thread, never the EDT.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/css", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/scss", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/less", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-scss", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-sass", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-less", service = CompletionProvider.class)
})
public class CssVarCompletionProvider implements CompletionProvider {

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        // "(" after var — the moment the token list is wanted. startsWith,
        // not equals (v1.333.0 review): with pair-completion the insert
        // can reach this hook as "()" and an exact match never fires.
        // Recorded limit: the auto-popup remains unverified LIVE either
        // way — ⌃Space is the proven path in both walks.
        return typedText.startsWith("(") ? COMPLETION_QUERY_TYPE : 0;
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet result, Document doc, int caret) {
            try {
                int lineStart = Math.max(0, caret - 200);
                String before = doc.getText(lineStart, caret - lineStart);
                String prefix = CssTokens.varPrefix(before);
                if (prefix == null) {
                    return;
                }
                String text = doc.getText(0, doc.getLength());
                // document tokens first — the cascade the designer sees
                Map<String, String> tokens = new LinkedHashMap<>();
                CssTokens.declarations(text, "text/x-sass".equals(
                        doc.getProperty("mimeType"))).forEach(
                        (name, t) -> tokens.put(name, t.value()));
                for (CssTokens.ProjectToken t : CssTokens.scanProject(projectDir(doc))) {
                    tokens.putIfAbsent(t.name(), t.value());
                }
                int anchor = caret - prefix.length();
                for (Map.Entry<String, String> e : tokens.entrySet()) {
                    if (e.getKey().startsWith(prefix)) {
                        result.addItem(new CssVarCompletionItem(
                                e.getKey(), e.getValue(), anchor, prefix.length()));
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
