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
 * Toggle comments (Cmd+/ / Ctrl+/): if every selected line is
 * commented, uncomment them all; otherwise comment them all at a
 * uniform column. Languages with a line-comment prefix get the prefix
 * form; the component markup dialects (Svelte, Vue) have only HTML's
 * block pair, so each line is wrapped {@code <!-- like this -->}
 * instead — same per-line symmetry, same blank-line skipping.
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/javascript"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/typescript"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-toml"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-yaml"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-properties"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-svelte"),
    @EditorActionRegistration(name = "toggle-comment", mimeType = "text/x-vue")
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
        String mime = (String) doc.getProperty("mimeType");
        String prefix = org.nmox.studio.editor.polyglot.LanguageComments.lineCommentFor(mime);
        org.nmox.studio.editor.polyglot.LanguageComments.BlockComment block =
                prefix == null
                ? org.nmox.studio.editor.polyglot.LanguageComments.blockCommentFor(mime)
                : null;
        if (prefix == null && block == null) {
            return;
        }
        int selStart = Math.min(target.getSelectionStart(), target.getSelectionEnd());
        int selEnd = Math.max(target.getSelectionStart(), target.getSelectionEnd());
        doc.runAtomicAsUser(() -> {
            try {
                if (prefix != null) {
                    toggle(doc, selStart, selEnd, prefix);
                } else {
                    toggleBlock(doc, selStart, selEnd, block.open(), block.close());
                }
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

    /**
     * The block-pair form of {@link #toggle}: each non-blank line is
     * wrapped {@code open … close} at its own indent (the way editors
     * comment markup, so structure stays visible line by line), or
     * unwrapped when every non-blank line already carries the pair.
     */
    static void toggleBlock(Document doc, int selStart, int selEnd, String open, String close)
            throws BadLocationException {
        Element root = doc.getDefaultRootElement();
        int firstLine = root.getElementIndex(selStart);
        // a selection ending exactly at a line start doesn't include that line
        int lastLine = root.getElementIndex(selEnd > selStart ? selEnd - 1 : selEnd);

        boolean allCommented = true;
        for (int i = firstLine; i <= lastLine; i++) {
            String line = lineText(doc, root.getElement(i)).trim();
            if (!line.isEmpty() && !(line.startsWith(open) && line.endsWith(close))) {
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
            int trailing = text.length() - text.stripTrailing().length();
            int lineStart = line.getStartOffset();
            if (allCommented) {
                String body = text.trim();
                int openLen = body.startsWith(open + " ") ? open.length() + 1 : open.length();
                int closeLen = body.endsWith(" " + close) ? close.length() + 1 : close.length();
                if (openLen + closeLen > body.length()) {
                    // the degenerate empty comment ("<!-- -->"): the padding
                    // spaces overlap, so strip only the bare markers
                    openLen = open.length();
                    closeLen = close.length();
                }
                if (openLen + closeLen > body.length()) {
                    // Even the bare markers don't fit — the line only LOOKS
                    // commented because it both starts with open and ends
                    // with close while being shorter than the pair (e.g.
                    // "<!-->"). Removing that much would eat the newline
                    // and the head of the NEXT line: silent file
                    // corruption. A marker pair that cannot fit is not a
                    // comment to strip, so leave the line alone.
                    continue;
                }
                // remove the close first so the open's offset stays valid
                doc.remove(lineStart + text.length() - trailing - closeLen, closeLen);
                doc.remove(lineStart + indent, openLen);
            } else {
                doc.insertString(lineStart + text.length() - trailing, " " + close, null);
                doc.insertString(lineStart + indent, open + " ", null);
            }
        }
    }

    private static String lineText(Document doc, Element line) throws BadLocationException {
        return doc.getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset() - 1);
    }
}
