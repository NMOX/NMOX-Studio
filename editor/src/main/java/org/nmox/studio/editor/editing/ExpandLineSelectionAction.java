package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.editor.BaseAction;

/**
 * Expand Line Selection (VS Code's ⌘L): selects the whole current line,
 * its terminator included, and pressed again takes the next line. The
 * rule is {@link LineSelection}; this class hands it the document's own
 * line table, so nothing is copied.
 *
 * <p>The platform's {@code select-line} selects one line and, pressed
 * again, selects the NEXT one instead of adding it, so it cannot stand in
 * for this gesture. ⌘L itself is Select Identifier in the default keymap
 * (and Go to Line in Eclipse's, Match Word in NetBeans 5.5's), so the
 * chord is bound only in the profiles that leave it free
 * ({@code vscode-line-keybindings.xml}) and the action is otherwise
 * reached from Quick Search by VS Code's title.
 */
@EditorActionRegistration(name = ExpandLineSelectionAction.NAME)
public class ExpandLineSelectionAction extends BaseAction {

    /** The kit action name the keybinding files and Quick Search name. */
    public static final String NAME = "nmox-expand-line-selection";

    public ExpandLineSelectionAction() {
        super(NAME, MAGIC_POSITION_RESET | UNDO_MERGE_RESET | WORD_MATCH_RESET);
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target != null) {
            expand(target);
        }
    }

    /** One press on {@code target}. */
    static void expand(JTextComponent target) {
        Document doc = target.getDocument();
        LineSelection.Range[] next = new LineSelection.Range[1];
        // the line table is read under the document's read lock
        doc.render(() -> next[0] = LineSelection.expand(linesOf(doc),
                target.getSelectionStart(), target.getSelectionEnd()));
        // anchored at the start, the caret at the end: where the next press reads from
        target.setCaretPosition(next[0].start());
        target.moveCaretPosition(next[0].end());
    }

    /** A document's own line table. */
    static LineSelection.Lines linesOf(Document doc) {
        Element root = doc.getDefaultRootElement();
        return new LineSelection.Lines() {
            @Override
            public int length() {
                return doc.getLength();
            }

            @Override
            public int lineStart(int offset) {
                return root.getElement(root.getElementIndex(offset)).getStartOffset();
            }

            @Override
            public int lineEnd(int offset) {
                return root.getElement(root.getElementIndex(offset)).getEndOffset();
            }
        };
    }
}
