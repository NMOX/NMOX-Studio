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
 * {@code class="…"} completion from the project's real stylesheets
 * (v2.27.0): the markup family's class attributes offer the classes
 * the CSS/SCSS/Less/Sass — and the family's own {@code <style>} blocks
 * — actually declare, each item carrying its declaring file. Providers
 * COMPOSE via lookupAll (the v1.213.0 arrangement), so this rides
 * beside the platform's html completion rather than replacing it.
 *
 * <p>Document-local {@code <style>} selectors list first (a component's
 * own classes are the ones its template most likely wants); the rest of
 * the project follows through {@link CssClasses}' bounded, cached scan
 * — which runs HERE, on the async completion thread, never the EDT.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/html", service = CompletionProvider.class, position = 540),
    @MimeRegistration(mimeType = "text/x-vue", service = CompletionProvider.class, position = 540),
    @MimeRegistration(mimeType = "text/x-svelte", service = CompletionProvider.class, position = 540),
    @MimeRegistration(mimeType = "text/x-ng-template", service = CompletionProvider.class, position = 540)
})
public class CssClassCompletionProvider implements CompletionProvider {

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
        // no auto-popup: the attribute context check needs the document,
        // which this hook does not get cheaply — ⌃Space is the proven
        // path (the v1.333.0 recorded limit, same stance as var()
        return 0;
    }


    /**
     * The item assembly, pure given its inputs (v2.30.0 floor fix —
     * extracted so the logic is testable headless; the query wrapper
     * keeps only platform plumbing): document-local {@code <style>}
     * classes first when {@code localRegions}, then the project scan,
     * filtered by {@code prefix}, provenance per item.
     */
    static java.util.List<CssClassCompletionItem> itemsFor(String prefix,
            String fullText, boolean localRegions, File root, int caret) {
        Map<String, String> classes = new LinkedHashMap<>();
        if (localRegions) {
            for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(fullText)) {
                CssClasses.selectors(fullText.substring(r.start(), r.end()))
                        .forEach((name, s) -> classes.putIfAbsent(name, "this file"));
            }
        }
        for (CssClasses.ProjectSelector s : CssClasses.scanProject(root)) {
            classes.putIfAbsent(s.name(), s.file().getName());
        }
        int anchor = caret - prefix.length();
        java.util.List<CssClassCompletionItem> out = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : classes.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.add(new CssClassCompletionItem(
                        e.getKey(), e.getValue(), anchor, prefix.length()));
            }
        }
        return out;
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet result, Document doc, int caret) {
            try {
                int windowStart = Math.max(0, caret - 300);
                String before = doc.getText(windowStart, caret - windowStart);
                String prefix = CssClasses.attrPrefix(before);
                if (prefix == null) {
                    return;
                }
                String text = doc.getText(0, doc.getLength());
                for (CssClassCompletionItem item : itemsFor(
                        prefix, text, true, projectDir(doc), caret)) {
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
