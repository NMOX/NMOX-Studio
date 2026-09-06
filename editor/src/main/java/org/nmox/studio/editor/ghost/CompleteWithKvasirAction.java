package org.nmox.studio.editor.ghost;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirClient.CodeQuestion;
import org.nmox.studio.rack.engine.KvasirComplete;
import org.nmox.studio.rack.engine.KvasirComplete.CompletionRequest;
import org.nmox.studio.rack.engine.KvasirCompleteEngine;
import org.nmox.studio.rack.engine.KvasirCompleteEngine.Proposal;
import org.nmox.studio.rack.service.KvasirKeys;
import org.nmox.studio.rack.service.AskKvasirModel;
import org.nmox.studio.rack.service.KvasirConsent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Complete with KVASIR (⌥⌘G or the editor popup): sends the code around
 * the caret — after the CODE consent, key from the keychain — and shows
 * the reply as ghost text; Tab inserts it, typing or moving the caret
 * dismisses it.
 *
 * <p>The gesture IS the gate: nothing leaves the machine until the chord
 * is pressed, each press is one bounded send (6,000 chars before the
 * caret, 1,500 after, clipped and said so), and the consent is the same
 * kind Ask and Edit earn — the same data classes travel, so no new
 * consent is invented. The send rides its own lane; only the ghost's
 * arming returns to the EDT.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.ghost.CompleteWithKvasirAction")
@ActionRegistration(displayName = "#CTL_CompleteWithKvasir", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1957),
    // ⌥⌘G is "DA-G": in NetBeans keystroke notation D is ⌘ on macOS and A is
    // ⌥, while O is ⌃ — the first draft's "DO-G" bound ⌃⌘G and the walk's
    // probe proved the chord never reached actionPerformed (only the popup
    // did). The chord also lives in every Keymaps profile (layer.xml).
    @ActionReference(path = "Shortcuts", name = "DA-G")
})
@Messages("CTL_CompleteWithKvasir=Complete with KVASIR")
public final class CompleteWithKvasirAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("KVASIR Complete", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent target = EditorRegistry.lastFocusedComponent();
        if (target == null || target.getDocument() == null) {
            StatusDisplayer.getDefault().setStatusText("Complete with KVASIR needs an editor with focus.");
            return;
        }
        GhostText ghost = GhostText.of(target);
        if (ghost == null) {
            StatusDisplayer.getDefault().setStatusText("This editor has no ghost-text layer yet — reopen the file.");
            return;
        }
        Document doc = target.getDocument();
        int caret = target.getCaretPosition();
        String before;
        String after;
        String lineHead;
        try {
            // bounded at the READ, not after it: the window is what travels,
            // so the window is all that is copied (the v2.61.1 review find —
            // the first cut read the whole document and clipped afterwards,
            // an unbounded read on a large file for a 7,500-character send)
            int from = Math.max(0, caret - KvasirComplete.MAX_BEFORE_CHARS);
            int to = Math.min(doc.getLength(), caret + KvasirComplete.MAX_AFTER_CHARS);
            before = doc.getText(from, caret - from);
            after = doc.getText(caret, to - caret);
            Element line = doc.getDefaultRootElement().getElement(
                    doc.getDefaultRootElement().getElementIndex(caret));
            lineHead = doc.getText(line.getStartOffset(), caret - line.getStartOffset());
        } catch (BadLocationException ex) {
            StatusDisplayer.getDefault().setStatusText("Complete with KVASIR could not read the editor.");
            return;
        }
        if (before.isBlank()) {
            StatusDisplayer.getDefault().setStatusText("Nothing to continue — write something first.");
            return;
        }
        Object mime = doc.getProperty("mimeType");
        String language = mime == null ? "" : mime.toString();
        Object title = doc.getProperty(Document.TitleProperty);
        String fileName = title == null ? "" : title.toString();
        CompletionRequest request = CompletionRequest.around(fileName, language, before, after);
        String model = AskKvasirModel.chosen();
        StatusDisplayer.getDefault().setStatusText("KVASIR is drafting a completion…");
        RP.post(() -> {
            KvasirCompleteEngine engine = new KvasirCompleteEngine(new KvasirClient(),
                    KvasirKeys::read,
                    r -> KvasirConsent.requestCodeConsent(new CodeQuestion(
                            r.fileName(), r.language(), r.before() + KvasirComplete.CURSOR + r.after(),
                            "complete the code at the caret")));
            Proposal p = engine.propose(request, lineHead, model);
            SwingUtilities.invokeLater(() -> deliver(p, target, caret));
        });
    }

    private static void deliver(Proposal p, JTextComponent target, int caret) {
        if (p.status() != KvasirCompleteEngine.Status.PROPOSED) {
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(firstLine(p.message())));
            return;
        }
        if (target.getCaretPosition() != caret) {
            StatusDisplayer.getDefault().setStatusText(
                    "The caret moved while KVASIR was drafting — completion dropped.");
            return;
        }
        GhostText ghost = GhostText.of(target);
        if (ghost != null) {
            ghost.arm(p.insertion());
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
