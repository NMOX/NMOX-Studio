package org.nmox.studio.editor.emmet;

import java.awt.event.ActionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.EditorActionRegistrations;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.openide.awt.StatusDisplayer;

/**
 * Expand Abbreviation (⌥⌘E, v1.329.0): type {@code ul>li.item$*3} in an
 * HTML or Angular template and the chord replaces it with the real,
 * indented markup, caret parked at the first useful empty spot. The
 * grammar lives in {@link Emmet}, pure and pinned; this class is only
 * the editor plumbing.
 *
 * <p><b>Why a chord, not Tab</b> — recorded so nobody "fixes" it: Tab
 * in these panes is claimed by indentation and the code-template
 * engine, and layering a third meaning onto it is exactly the silent
 * keystroke fight the v1.38.1 sweep spent a release untangling. An
 * explicit chord also frees the grammar to refuse honestly: on text it
 * cannot parse, NOTHING happens except a status line saying so —
 * pressing it mid-sentence never mangles prose.
 *
 * <p>Mime-registered like the v1.219.0 goto-declaration: on CSL panes
 * the kit's action map is what menus and keybindings actually resolve,
 * so each markup mime carries the action + a mime-scoped {@code DA-E}
 * binding (see emmet-keybindings.xml).
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/html", popupPath = "", popupPosition = 95),
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/x-ng-template", popupPath = "", popupPosition = 95)
})
public class ExpandAbbreviationAction extends BaseAction {

    public ExpandAbbreviationAction() {
        super("nmox-expand-abbreviation");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null || !target.isEditable()
                || !(target.getDocument() instanceof BaseDocument doc)) {
            return;
        }
        try {
            int caret = target.getCaretPosition();
            int lineStart = Utilities.getRowStart(target, caret);
            String before = doc.getText(lineStart, caret - lineStart);
            String abbrev = Emmet.abbreviationIn(before);
            if (abbrev == null) {
                StatusDisplayer.getDefault().setStatusText(
                        "No abbreviation at the caret (try ul>li*3)");
                return;
            }
            // indentation: the line's leading whitespace prefixes every
            // generated line after the first, so the fragment sits at
            // the caret's own depth
            String leading = before.substring(0,
                    before.length() - before.stripLeading().length());
            Emmet.Expansion e = Emmet.expand(abbrev, "  ");
            String indented = e.html().replace("\n", "\n" + leading);
            // the caret offset survives indentation by counting the
            // newlines BEFORE it in the raw fragment
            long newlinesBeforeCaret = e.html().substring(0, e.caretOffset())
                    .chars().filter(c -> c == '\n').count();
            int caretInIndented = e.caretOffset()
                    + (int) newlinesBeforeCaret * leading.length();
            int abbrevStart = caret - abbrev.length();
            doc.runAtomicAsUser(() -> {
                try {
                    doc.remove(abbrevStart, abbrev.length());
                    doc.insertString(abbrevStart, indented, null);
                } catch (BadLocationException ex) {
                    // the document changed under the atomic edit; the
                    // runAtomic rollback restores it — nothing to do
                }
            });
            target.setCaretPosition(abbrevStart + caretInIndented);
        } catch (BadLocationException ex) {
            // caret math raced an edit; refuse silently rather than guess
        }
    }
}
