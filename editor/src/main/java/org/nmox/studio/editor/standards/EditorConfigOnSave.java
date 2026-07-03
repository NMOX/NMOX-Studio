package org.nmox.studio.editor.standards;

import java.io.File;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.document.OnSaveTask;
import org.nmox.studio.editor.format.FormatOnSave;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Honors the project's .editorconfig on every save - the standard the
 * IDE previously only syntax-highlighted. Applies the two save-safe
 * properties (trim_trailing_whitespace, insert_final_newline) to the
 * saving document with a minimal edit so the caret keeps its place.
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
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null || file.getName().equals(".editorconfig")) {
            return; // in-memory docs, and never rewrite the config itself
        }
        Map<String, String> props = EditorConfig.propertiesFor(file);
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
