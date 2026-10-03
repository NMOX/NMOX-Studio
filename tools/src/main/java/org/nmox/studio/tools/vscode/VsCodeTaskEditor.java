package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.nmox.studio.rack.service.EditorTabs;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The editor as a VS Code task's variables mean it: {@code ${file}} is
 * the file of the ACTIVE editor, {@code ${lineNumber}} its caret's line,
 * {@code ${selectedText}} its selection. Swing state, so read on the
 * event thread; the answer is a plain {@link EditorContext} the pure
 * resolver can use anywhere.
 *
 * <p><b>Which editor.</b> The tab that has the focus when it is an editor
 * tab; otherwise the editor area's selected tab — the file showing while
 * the focus sits in Quick Search, a studio or the Projects window. (The
 * Agent Port's {@code editor_state} names its active file by the same
 * rule, {@code rack.mcp.EditorState.activeEditor}.) A tab that holds no
 * file — the Welcome, the Task Rack, the Browser — is no file: a task
 * that needs one is refused, as VS Code refuses it with no editor open,
 * rather than handed a file from a tab the user is not looking at.
 *
 * <p>The file is the one the tab holds ({@link EditorTabs#fileOf}), and
 * only when it is on disk: a file inside an archive has no path a
 * command line could take.
 */
final class VsCodeTaskEditor {

    private static final Logger LOG = Logger.getLogger(VsCodeTaskEditor.class.getName());

    private VsCodeTaskEditor() {
    }

    /**
     * The editor now. From the event thread it is read in place; from any
     * other it is read there and waited for. An editor that cannot be
     * read is no editor — the tasks that need one say so.
     */
    static EditorContext snapshot() {
        if (EventQueue.isDispatchThread()) {
            return read();
        }
        EditorContext[] out = {EditorContext.NONE};
        try {
            EventQueue.invokeAndWait(() -> out[0] = read());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException failed) {
            LOG.log(Level.INFO, "the editor could not be read for a task's variables", failed.getCause());
        }
        return out[0];
    }

    private static EditorContext read() {
        try {
            TopComponent activated = TopComponent.getRegistry().getActivated();
            WindowManager windows = WindowManager.getDefault();
            Mode editors = windows.findMode("editor");
            TopComponent tab = activeEditor(activated,
                    activated != null && windows.isOpenedEditorTopComponent(activated),
                    editors == null ? null : editors.getSelectedTopComponent());
            if (tab == null) {
                return EditorContext.NONE;
            }
            FileObject edited = EditorTabs.fileOf(tab);
            File onDisk = edited == null ? null : FileUtil.toFile(edited);
            JTextComponent pane = null;
            // most recently used first, so a split editor answers with the half last typed in
            for (JTextComponent candidate : EditorRegistry.componentList()) {
                if (SwingUtilities.isDescendingFrom(candidate, tab)) {
                    pane = candidate;
                    break;
                }
            }
            return context(onDisk == null ? null : onDisk.toPath(), pane);
        } catch (RuntimeException unreadable) {
            LOG.log(Level.INFO, "the editor could not be read for a task's variables", unreadable);
            return EditorContext.NONE;
        }
    }

    /** The tab that counts as the editor: the activated one when it is an editor tab, else the editor area's selected tab. */
    static TopComponent activeEditor(TopComponent activated, boolean activatedIsEditor,
            TopComponent selectedInEditorArea) {
        return activated != null && activatedIsEditor ? activated : selectedInEditorArea;
    }

    /**
     * {@code file} with the caret and the selection of {@code pane} (null:
     * the tab has no text editor, so no caret and no selection). The line
     * and the column are the caret's, counted from 1 as VS Code counts
     * them. The selection is read only as far as the resolver needs to
     * know it is too long.
     */
    static EditorContext context(Path file, JTextComponent pane) {
        if (pane == null) {
            return new EditorContext(file, 0, 0, null);
        }
        Document doc = pane.getDocument();
        int caret = Math.max(0, Math.min(pane.getCaretPosition(), doc.getLength()));
        Element lines = doc.getDefaultRootElement();
        int index = lines.getElementIndex(caret);
        int column = caret - lines.getElement(index).getStartOffset() + 1;
        int start = pane.getSelectionStart();
        int length = Math.min(pane.getSelectionEnd() - start, VsCodeTasks.MAX_SELECTED_TEXT + 1);
        String selected = null;
        if (length > 0) {
            try {
                selected = doc.getText(start, length);
            } catch (BadLocationException moved) {
                selected = null; // the document changed under the read: no selection to give
            }
        }
        return new EditorContext(file, index + 1, column, selected);
    }
}
