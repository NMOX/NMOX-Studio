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
            mimeType = "text/x-ng-template", popupPath = "", popupPosition = 95),
    // the css family (v1.336.0): all five mimes because css-prep resolves
    // real .scss/.less to its own text/scss / text/less while ours serve
    // .sass and friends — the v1.230.0 finding, twice bitten
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/css", popupPath = "", popupPosition = 95),
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/scss", popupPath = "", popupPosition = 95),
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/less", popupPath = "", popupPosition = 95),
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/x-scss", popupPath = "", popupPosition = 95),
    @EditorActionRegistration(name = "nmox-expand-abbreviation",
            mimeType = "text/x-less", popupPath = "", popupPosition = 95)
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
            int lineEnd = Utilities.getRowEnd(target, caret);
            String before = doc.getText(lineStart, caret - lineStart);
            String after = doc.getText(caret, lineEnd - caret);
            // stylesheet panes speak the CSS grammar (v1.336.0); the mime
            // rides the document property BaseDocument sets from its kit
            Object mime = doc.getProperty("mimeType");
            if (mime instanceof String m
                    && (m.contains("css") || m.contains("scss") || m.contains("less"))) {
                expandCss(target, doc, caret, before);
                return;
            }
            // auto-pair aware (v1.332.0): typing {text} leaves the caret
            // BEFORE the auto-closed brace — fold trailing closers in
            Emmet.AtCaret at = Emmet.abbreviationAt(before, after);
            if (at == null) {
                StatusDisplayer.getDefault().setStatusText(
                        "No abbreviation at the caret (try ul>li*3)");
                return;
            }
            String abbrev = at.abbrev();
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
            int abbrevStart = caret + at.trailingClosers() - abbrev.length();
            boolean[] landed = {false};
            doc.runAtomicAsUser(() -> {
                try {
                    doc.remove(abbrevStart, abbrev.length());
                    doc.insertString(abbrevStart, indented, null);
                    landed[0] = true;
                } catch (BadLocationException ex) {
                    // the document changed under the atomic edit; the
                    // runAtomic rollback restores it — nothing to do
                }
            });
            if (landed[0]) {
                // caret math is only valid for the document the edit
                // actually produced (v1.333.0 review: an unguarded set
                // after a rolled-back edit threw out-of-bounds)
                target.setCaretPosition(abbrevStart + caretInIndented);
            }
        } catch (BadLocationException ex) {
            // caret math raced an edit; refuse silently rather than guess
        }
    }

    /**
     * The CSS branch (v1.336.0): a single-line declaration replaces the
     * abbreviation token before the caret. No auto-pair folding — the
     * CSS grammar's tokens carry no closers — and no indentation math:
     * the declaration stays on the abbreviation's own line.
     */
    private static void expandCss(JTextComponent target, BaseDocument doc,
            int caret, String before) {
        String abbrev = CssEmmet.abbreviationIn(before);
        if (abbrev == null) {
            StatusDisplayer.getDefault().setStatusText(
                    "No CSS abbreviation at the caret (try m10, df, c#f00)");
            return;
        }
        CssEmmet.Expansion e = CssEmmet.expand(abbrev);
        // a + chain expands to multiple lines (v1.338.0); continuation
        // lines take the abbreviation line's own indent, the same idiom
        // as the markup path above
        String leading = before.substring(0,
                before.length() - before.stripLeading().length());
        String indented = e.css().replace("\n", "\n" + leading);
        int abbrevStart = caret - abbrev.length();
        boolean[] landed = {false};
        doc.runAtomicAsUser(() -> {
            try {
                doc.remove(abbrevStart, abbrev.length());
                doc.insertString(abbrevStart, indented, null);
                landed[0] = true;
            } catch (BadLocationException ex) {
                // rolled back by runAtomic; nothing to do
            }
        });
        if (landed[0]) {
            target.setCaretPosition(abbrevStart + indented.length());
        }
    }
}
