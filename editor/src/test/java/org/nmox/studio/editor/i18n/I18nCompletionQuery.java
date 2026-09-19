package org.nmox.studio.editor.i18n;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.filesystems.FileUtil;

/**
 * Drives a real {@link org.netbeans.spi.editor.completion.CompletionProvider}
 * the way the platform drives it, with no popup anywhere.
 *
 * <p>A completion popup cannot be opened by this automation — it exists
 * only while a keystroke holds it up. The v2.145.0 answer to the same
 * wall one surface over was to stop reaching for the gesture and ask
 * the platform's own machinery for the ANSWER instead; this is that,
 * for completion. The platform's own
 * {@code CompletionResultSetImpl(CompletionImpl, resultId, task,
 * queryType)} is built here by reflection (its constructor is
 * package-private, its {@code CompletionImpl} a real headless
 * singleton), the task is queried ON THE EDT because
 * {@code AsyncCompletionTask.query} asserts that, and the items are
 * read back off the same result set the popup would have rendered —
 * read from the shipped bytecode, not from folklore.
 *
 * <p><b>Ceiling, stated:</b> this proves the provider ANSWERS, with
 * which items and which provenance. It does not prove the popup PAINTS
 * them — no instrument in this repo can, and inventing one would be the
 * v1.324.0 fabrication. The rendering half is
 * {@code CompletionUtilities.renderHtml(null, name, provenance, …)},
 * one line in {@code CssClassCompletionItem.render}, whose two
 * arguments are exactly the two fields asserted here.
 */
final class I18nCompletionQuery {

    private I18nCompletionQuery() {
    }

    /** Write {@code text} at {@code rel} under {@code root}, parents included. */
    static Path write(Path root, String rel, String text) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
        return f;
    }

    /** A document backed by a REAL file, so {@code ProjectRoot.of} resolves. */
    static Document documentOn(Path file, String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        d.putProperty(Document.StreamDescriptionProperty,
                FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile())));
        return d;
    }

    /**
     * The items a real query of {@code queryType} yields with the caret
     * at {@code caret} — null when the provider refuses the query type
     * outright (no task), an empty list when it offers nothing.
     */
    static List<? extends CompletionItem> items(int queryType, Document doc, int caret)
            throws Exception {
        JEditorPane pane = new JEditorPane();
        pane.setDocument(doc);
        pane.setCaretPosition(caret);
        CompletionTask task = new I18nKeyCompletionProvider().createTask(queryType, pane);
        if (task == null) {
            return null;
        }
        Class<?> implType = Class.forName("org.netbeans.modules.editor.completion.CompletionResultSetImpl");
        Class<?> completionType = Class.forName("org.netbeans.modules.editor.completion.CompletionImpl");
        Object completion = completionType.getMethod("get").invoke(null);
        Constructor<?> ctor = implType.getDeclaredConstructor(
                completionType, Object.class, CompletionTask.class, int.class);
        ctor.setAccessible(true);
        Object impl = ctor.newInstance(completion, "nmox-test", task, queryType);
        CompletionResultSet set = (CompletionResultSet) implType.getMethod("getResultSet").invoke(impl);
        java.awt.EventQueue.invokeAndWait(() -> task.query(set));
        for (int i = 0; i < 500 && !(Boolean) implType.getMethod("isFinished").invoke(impl); i++) {
            Thread.sleep(10);
        }
        if (!(Boolean) implType.getMethod("isFinished").invoke(impl)) {
            throw new IllegalStateException("the query never finished");
        }
        @SuppressWarnings("unchecked")
        List<? extends CompletionItem> items =
                (List<? extends CompletionItem>) implType.getMethod("getItems").invoke(impl);
        return items == null ? List.of() : items;
    }

    /** One item as the popup would show it: {@code name · provenance}. */
    static String shown(CompletionItem item) throws Exception {
        return field(item, "name") + " · " + field(item, "provenance");
    }

    /** The replacement span an accept would rewrite: {@code start+length}. */
    static String span(CompletionItem item) throws Exception {
        return field(item, "startOffset") + "+" + field(item, "prefixLength");
    }

    private static Object field(CompletionItem item, String name) throws Exception {
        Field f = item.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(item);
    }
}
