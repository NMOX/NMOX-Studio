package org.nmox.studio.editor.typing;

import java.awt.event.ActionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.EditorActionRegistrations;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;

/**
 * Toggle line comments (Cmd+/ / Ctrl+/) for JS and TS: if every
 * selected line is commented, uncomment them all; otherwise comment
 * them all at a uniform column.
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/javascript"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/typescript"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-java"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-c"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-cpp"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-rust"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-php5"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-go"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-python"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-ruby"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/sh"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-toml"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-yaml"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-properties")
})
public class ToggleLineCommentAction extends BaseAction {

    public ToggleLineCommentAction() {
        super("toggle-comment");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null || !(target.getDocument() instanceof BaseDocument)) {
            return;
        }
        BaseDocument doc = (BaseDocument) target.getDocument();
        String prefix = org.nmox.studio.editor.polyglot.LanguageComments
                .lineCommentFor((String) doc.getProperty("mimeType"));
        if (prefix == null) {
            return;
        }
        int selStart = Math.min(target.getSelectionStart(), target.getSelectionEnd());
        int selEnd = Math.max(target.getSelectionStart(), target.getSelectionEnd());
        doc.runAtomicAsUser(() -> {
            try {
                toggle(doc, selStart, selEnd, prefix);
            } catch (BadLocationException ex) {
                Utilities.setStatusBoldText(target, "Toggle comment failed");
            }
        });
    }

    static void toggle(Document doc, int selStart, int selEnd, String prefix) throws BadLocationException {
        Element root = doc.getDefaultRootElement();
        int firstLine = root.getElementIndex(selStart);
        // a selection ending exactly at a line start doesn't include that line
        int lastLine = root.getElementIndex(selEnd > selStart ? selEnd - 1 : selEnd);

        boolean allCommented = true;
        for (int i = firstLine; i <= lastLine; i++) {
            String line = lineText(doc, root.getElement(i));
            if (!line.isBlank() && !line.trim().startsWith(prefix)) {
                allCommented = false;
                break;
            }
        }
        // bottom-up so earlier offsets stay valid
        for (int i = lastLine; i >= firstLine; i--) {
            Element line = root.getElement(i);
            String text = lineText(doc, line);
            if (text.isBlank()) {
                continue;
            }
            int indent = text.length() - text.stripLeading().length();
            int lineStart = line.getStartOffset();
            if (allCommented) {
                int prefixAt = lineStart + indent;
                int len = text.stripLeading().startsWith(prefix + " ") ? prefix.length() + 1 : prefix.length();
                doc.remove(prefixAt, len);
            } else {
                doc.insertString(lineStart + indent, prefix + " ", null);
            }
        }
    }

    private static String lineText(Document doc, Element line) throws BadLocationException {
        return doc.getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset() - 1);
    }
}
