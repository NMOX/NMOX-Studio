package org.nmox.studio.editor.ghost;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.engine.OracleComplete.CompletionRequest;
import org.nmox.studio.rack.engine.OracleCompleteEngine;
import org.nmox.studio.rack.engine.OracleCompleteEngine.Proposal;
import org.nmox.studio.rack.service.OracleKeys;
import org.nmox.studio.rack.service.AskOracleModel;
import org.nmox.studio.rack.service.OracleConsent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Complete with ORACLE (⌥⌘G or the editor popup): sends the code around
 * the caret — after the CODE consent, key from the keychain — and shows
 * the reply as ghost text; Tab inserts it, Escape dismisses it.
 *
 * <p>The gesture IS the gate: nothing leaves the machine until the chord
 * is pressed, each press is one bounded send (6,000 chars before the
 * caret, 1,500 after, clipped and said so), and the consent is the same
 * kind Ask and Edit earn — the same data classes travel, so no new
 * consent is invented. The send rides its own lane; only the ghost's
 * arming returns to the EDT.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.ghost.CompleteWithOracleAction")
@ActionRegistration(displayName = "#CTL_CompleteWithOracle", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1957),
    // ⌥⌘G — a LETTER on purpose: ⌥⌘/ (the first draft) never fired on macOS,
    // measured twice on the walk (the harness and System Events both), because
    // Option changes the key char of punctuation before the keymap sees it
    @ActionReference(path = "Shortcuts", name = "DO-G")
})
@Messages("CTL_CompleteWithOracle=Complete with ORACLE")
public final class CompleteWithOracleAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("ORACLE Complete", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent target = EditorRegistry.lastFocusedComponent();
        if (target == null || target.getDocument() == null) {
            StatusDisplayer.getDefault().setStatusText("Complete with ORACLE needs an editor with focus.");
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
            before = doc.getText(0, caret);
            after = doc.getText(caret, doc.getLength() - caret);
            Element line = doc.getDefaultRootElement().getElement(
                    doc.getDefaultRootElement().getElementIndex(caret));
            lineHead = doc.getText(line.getStartOffset(), caret - line.getStartOffset());
        } catch (BadLocationException ex) {
            StatusDisplayer.getDefault().setStatusText("Complete with ORACLE could not read the editor.");
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
        String model = AskOracleModel.chosen();
        StatusDisplayer.getDefault().setStatusText("ORACLE is drafting a completion…");
        RP.post(() -> {
            OracleCompleteEngine engine = new OracleCompleteEngine(new OracleClient(),
                    OracleKeys::read,
                    r -> OracleConsent.requestCodeConsent(new CodeQuestion(
                            r.fileName(), r.language(), r.before() + "<CURSOR>" + r.after(),
                            "complete the code at the caret")));
            Proposal p = engine.propose(request, lineHead, model);
            SwingUtilities.invokeLater(() -> deliver(p, target, caret));
        });
    }

    private static void deliver(Proposal p, JTextComponent target, int caret) {
        if (p.status() != OracleCompleteEngine.Status.PROPOSED) {
            StatusDisplayer.getDefault().setStatusText(firstLine(p.message()));
            return;
        }
        if (target.getCaretPosition() != caret) {
            StatusDisplayer.getDefault().setStatusText(
                    "The caret moved while ORACLE was drafting — completion dropped.");
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
