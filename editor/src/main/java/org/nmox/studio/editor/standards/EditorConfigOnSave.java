package org.nmox.studio.editor.standards;

import java.io.File;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.document.OnSaveTask;
import org.nmox.studio.editor.format.FormatOnSave;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Honors the project's .editorconfig on every save - the standard the
 * IDE previously only syntax-highlighted. Applies the two save-safe
 * properties (trim_trailing_whitespace, insert_final_newline) to the
 * saving document with a minimal edit so the caret keeps its place, and
 * end_of_line by telling the document which line ending to be written
 * with.
 * No .editorconfig, or none matching the file: the save is untouched.
 */
public final class EditorConfigOnSave implements OnSaveTask {

    private final Document doc;
    private volatile boolean cancelled;

    private EditorConfigOnSave(Document doc) {
        this.doc = doc;
    }

    @Override
    public void performTask() {
        if (cancelled) {
            return;
        }
        FileObject fo = org.nmox.studio.core.util.EditedFile.of(doc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null || file.getName().equals(".editorconfig")) {
            return; // in-memory docs, and never rewrite the config itself
        }
        Map<String, String> props = ProjectFormatting.propertiesFor(file);
        if (props.isEmpty()) {
            return;
        }
        try {
            String before = doc.getText(0, doc.getLength());
            String after = EditorConfig.applyOnSave(before, props);
            if (!after.equals(before) && !cancelled) {
                FormatOnSave.applyMinimalEdit(doc, before, after);
            }
        } catch (BadLocationException ex) {
            // the save proceeds with the text as typed
        }
        if (!cancelled) {
            writeWith(doc, EditorConfig.lineSeparator(props));
        }
    }

    /**
     * The property the platform's kits read when they write a document
     * out: {@code DefaultEditorKit.EndOfLineStringProperty}, which
     * {@code BaseDocument} names {@code READ_LINE_SEPARATOR_PROP} (the
     * status line's own CRLF switch sets this one).
     */
    static final String END_OF_LINE = javax.swing.text.DefaultEditorKit.EndOfLineStringProperty;

    /** {@code BaseDocument.WRITE_LINE_SEPARATOR_PROP}: wins over the one above when set. */
    static final String WRITE_END_OF_LINE = "write-line-separator";

    /**
     * Has {@code doc} written with {@code separator} from this save on
     * (3.5.13): a project that states {@code end_of_line} (or
     * {@code files.eol}) gets that ending on every file it saves, a file
     * that arrived with the other one included. Null changes nothing.
     * The text in the document is not touched: it always holds
     * {@code \n}, and the ending is applied as the bytes are written.
     */
    static void writeWith(Document doc, String separator) {
        if (separator == null) {
            return;
        }
        if (!separator.equals(doc.getProperty(END_OF_LINE))) {
            doc.putProperty(END_OF_LINE, separator);
        }
        if (doc.getProperty(WRITE_END_OF_LINE) != null && !separator.equals(doc.getProperty(WRITE_END_OF_LINE))) {
            doc.putProperty(WRITE_END_OF_LINE, separator);
        }
    }

    @Override
    public void runLocked(Runnable run) {
        run.run();
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        return true;
    }

    /** All mimes: the standard applies to every text file the IDE saves. */
    @MimeRegistration(mimeType = "", service = OnSaveTask.Factory.class, position = 380)
    public static final class Factory implements OnSaveTask.Factory {

        @Override
        public OnSaveTask createTask(Context context) {
            return new EditorConfigOnSave(context.getDocument());
        }
    }
}
