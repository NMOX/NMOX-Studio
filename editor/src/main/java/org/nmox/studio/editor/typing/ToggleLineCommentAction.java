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
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/typescript")
})
public class ToggleLineCommentAction extends BaseAction {

    private static final String PREFIX = "//";

    public ToggleLineCommentAction() {
        super("toggle-comment");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null || !(target.getDocument() instanceof BaseDocument)) {
            return;
        }
        BaseDocument doc = (BaseDocument) target.getDocument();
        int selStart = Math.min(target.getSelectionStart(), target.getSelectionEnd());
        int selEnd = Math.max(target.getSelectionStart(), target.getSelectionEnd());
        doc.runAtomicAsUser(() -> {
            try {
                toggle(doc, selStart, selEnd);
            } catch (BadLocationException ex) {
                Utilities.setStatusBoldText(target, "Toggle comment failed");
            }
        });
    }

    static void toggle(Document doc, int selStart, int selEnd) throws BadLocationException {
        Element root = doc.getDefaultRootElement();
        int firstLine = root.getElementIndex(selStart);
        // a selection ending exactly at a line start doesn't include that line
        int lastLine = root.getElementIndex(selEnd > selStart ? selEnd - 1 : selEnd);

        boolean allCommented = true;
        for (int i = firstLine; i <= lastLine; i++) {
            String line = lineText(doc, root.getElement(i));
            if (!line.isBlank() && !line.trim().startsWith(PREFIX)) {
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
                int len = text.stripLeading().startsWith(PREFIX + " ") ? 3 : 2;
                doc.remove(prefixAt, len);
            } else {
                doc.insertString(lineStart + indent, PREFIX + " ", null);
            }
        }
    }

    private static String lineText(Document doc, Element line) throws BadLocationException {
        return doc.getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset() - 1);
    }
}
