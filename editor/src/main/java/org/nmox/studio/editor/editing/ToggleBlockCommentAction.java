package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.editor.editing.BlockComments.Edit;
import org.nmox.studio.editor.editing.BlockComments.NoBlockComment;
import org.nmox.studio.editor.editing.BlockComments.Op;
import org.nmox.studio.editor.editing.BlockComments.Outcome;
import org.nmox.studio.editor.editing.BlockComments.Refusal;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * Toggle Block Comment (⇧⌥A, VS Code's chord): wraps the selection in the
 * language's block delimiters, or unwraps a selection that is one block
 * comment; with no selection it toggles the current line. The rules are
 * {@link BlockComments}; this class reads the lines the range touches,
 * applies the answer as one undo step and says a refusal on the status
 * line.
 *
 * <p>Registered at the ROOT of {@code Editors/Actions}, the way the
 * platform registers its own editing actions, so every kit carries it
 * (a kit collects its mime's actions with the root's inherited:
 * {@code NbEditorKit.getDeclaredActions}); the language is decided when
 * the key is pressed, from the document, and a language with no block
 * comment is refused by name instead of being left without the action.
 */
@EditorActionRegistration(name = ToggleBlockCommentAction.NAME)
public class ToggleBlockCommentAction extends BaseAction {

    /** The kit action name the keybinding files and Quick Search name. */
    public static final String NAME = "nmox-toggle-block-comment";

    /**
     * The most text one press reads: the selection, to look for delimiters
     * in it. Past this the toggle is refused, since checking would mean
     * copying a document's worth of text on the event thread.
     */
    static final int MAX_RANGE = 2_000_000;

    /** How much text before the range a markup document is asked for, to find the block the range is in. */
    static final int MAX_BEFORE = 1_000_000;

    /** Where a refusal is said; a seam for tests. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text));

    public ToggleBlockCommentAction() {
        super(NAME, MAGIC_POSITION_RESET | UNDO_MERGE_RESET | WORD_MATCH_RESET);
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        if (evt != null && TypedEcho.follows(evt.getModifiers())) {
            TypedEcho.swallowNext(target);
        }
        toggle(target);
    }

    /** One press on {@code target}. */
    static void toggle(JTextComponent target) {
        if (!target.isEditable() || !target.isEnabled()) {
            status.accept(message("BlockComment_readOnly"));
            return;
        }
        Document doc = target.getDocument();
        int s = Math.min(target.getSelectionStart(), target.getSelectionEnd());
        int e = Math.max(target.getSelectionStart(), target.getSelectionEnd());
        int base = s;
        String window;
        Outcome outcome;
        try {
            int to = e;
            if (s == e) {
                Element root = doc.getDefaultRootElement();
                Element line = root.getElement(root.getElementIndex(s));
                base = line.getStartOffset();
                to = Math.min(line.getEndOffset(), doc.getLength());
            }
            if (to - base > MAX_RANGE) {
                status.accept(message("BlockComment_tooLarge"));
                return;
            }
            String mime = mimeOf(doc);
            String before = "";
            if (BlockComments.EMBEDDING.contains(mime)) {
                int head = Math.max(0, base - MAX_BEFORE);
                before = doc.getText(head, base - head);
            }
            window = doc.getText(base, to - base);
            outcome = BlockComments.toggle(mime, before, window, s - base, e - base);
        } catch (BadLocationException moved) {
            status.accept(message("BlockComment_failed"));
            return;
        }
        if (outcome instanceof NoBlockComment) {
            status.accept(message("BlockComment_none"));
        } else if (outcome instanceof Refusal refusal) {
            status.accept(NbBundle.getMessage(ToggleBlockCommentAction.class, "BlockComment_contains",
                    refusal.delimiter()));
        } else if (outcome instanceof Edit edit) {
            apply(target, doc, base, window, edit);
        }
    }

    /** The edit as one undo step, then the selection it leaves. */
    private static void apply(JTextComponent target, Document doc, int base, String window, Edit edit) {
        boolean[] landed = {false};
        Runnable write = () -> {
            try {
                // all or nothing: the offsets are only true of the text they
                // were computed from, so a document that changed since the
                // read (another thread's edit) is left alone
                if (base + window.length() > doc.getLength()
                        || !doc.getText(base, window.length()).equals(window)) {
                    return;
                }
                for (Op op : edit.ops()) {
                    if (op.remove() > 0) {
                        doc.remove(base + op.offset(), op.remove());
                    }
                    if (!op.insert().isEmpty()) {
                        doc.insertString(base + op.offset(), op.insert(), null);
                    }
                }
                landed[0] = true;
            } catch (BadLocationException moved) {
                // the document changed under the edit; an atomic edit rolls back
            }
        };
        if (doc instanceof BaseDocument atomic) {
            atomic.runAtomicAsUser(write);
        } else {
            write.run();
        }
        if (!landed[0]) {
            status.accept(message("BlockComment_failed"));
            return;
        }
        // the selection is only meaningful for the text the edit produced
        target.setCaretPosition(base + edit.selStart());
        if (edit.selEnd() != edit.selStart()) {
            target.moveCaretPosition(base + edit.selEnd());
        }
    }

    /** The language of a document: the mime its kit gave it. */
    static String mimeOf(Document doc) {
        Object mime = doc.getProperty("mimeType");
        return mime instanceof String m ? m : null;
    }

    private static String message(String key) {
        return NbBundle.getMessage(ToggleBlockCommentAction.class, key);
    }
}
