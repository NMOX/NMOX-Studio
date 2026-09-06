package org.nmox.studio.editor.share;

import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nmox.studio.core.util.Plural;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Right-click ▸ Copy as Markdown (v2.87.0): the selection — or, with
 * nothing selected, the whole file — lands on the clipboard as a fenced
 * block tagged with the file's language, ready to paste into a README, a
 * GitHub issue, a blog post or a chat. Born for the developer evangelist
 * who does exactly that a dozen times a day and hand-types the fence every
 * time. The status line says what was copied and which tag it carries.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyAsMarkdownAction")
@ActionRegistration(displayName = "#CTL_CopyAsMarkdown", lazy = true)
@ActionReference(path = "Editors/Popup", position = 1960)
@Messages("CTL_CopyAsMarkdown=Copy as Markdown")
public final class CopyAsMarkdownAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = focusedEditor();
        if (editor == null) {
            StatusDisplayer.getDefault().setStatusText("Copy as Markdown: no editor has focus");
            return;
        }
        Document doc = editor.getDocument();
        String selection = editor.getSelectedText();
        boolean whole = selection == null || selection.isEmpty();
        String code;
        try {
            code = whole ? doc.getText(0, doc.getLength()) : selection;
        } catch (BadLocationException ex) {
            StatusDisplayer.getDefault().setStatusText("Copy as Markdown: could not read the buffer");
            return;
        }
        String mime = mimeOf(doc);
        String block = CopyAsMarkdown.block(code, mime);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(block), null);
        StatusDisplayer.getDefault().setStatusText("Copied " + (whole ? "the whole of " + fileName(doc) : "the selection")
                + " as Markdown — " + Plural.of(CopyAsMarkdown.lineCount(code), "line")
                + " in a ```" + CopyAsMarkdown.fence(mime) + " block");
    }

    static JTextComponent focusedEditor() {
        java.awt.Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return owner instanceof JTextComponent tc ? tc : null;
    }

    static String mimeOf(Document doc) {
        Object mime = doc == null ? null : doc.getProperty("mimeType");
        return mime instanceof String s && !s.isBlank() ? s : "text/plain";
    }

    static String fileName(Document doc) {
        Object sd = doc == null ? null : doc.getProperty(Document.StreamDescriptionProperty);
        return sd instanceof DataObject dob ? dob.getPrimaryFile().getNameExt() : "the buffer";
    }
}
