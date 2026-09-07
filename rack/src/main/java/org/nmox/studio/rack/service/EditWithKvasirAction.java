package org.nmox.studio.rack.service;

import org.nmox.studio.core.util.PlainText;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirClient.CodeQuestion;
import org.nmox.studio.rack.engine.KvasirEdit;
import org.nmox.studio.rack.engine.KvasirEdit.EditRequest;
import org.nmox.studio.rack.engine.KvasirEditEngine;
import org.nmox.studio.rack.engine.KvasirEditEngine.Proposal;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Edit with KVASIR — the AI surface's second editor face, and the step
 * from explainer to pair programmer. Right-click a selection, say what
 * to change, and the proposed replacement arrives as a BEFORE/AFTER
 * preview; only Apply touches the file, as one undo unit, and only if
 * the buffer still holds exactly the text that was sent (the
 * stale-buffer guard). The whole KVASIR law set holds: zero boot cost,
 * no network without this explicit gesture, the key Keyring-or-env
 * only, and the CODE consent — an edit sends exactly the data classes
 * that consent names (the selection, the file's name, its language, one
 * line of user text), so the kind is the same and a new consent would
 * restate the same bullets.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.rack.service.EditWithKvasirAction")
@ActionRegistration(displayName = "#CTL_EditWithKvasirAction", lazy = true)
@ActionReference(path = "Editors/Popup", position = 1955)
@Messages({
    "CTL_EditWithKvasirAction=Edit with KVASIR…",
    "EditWithKvasirAction_selectFirst=Select some code first — Edit with KVASIR rewrites only the selection.",
    "EditWithKvasirAction_tooLarge=Selection too large for an KVASIR edit ({0} chars, cap {1}) — nothing was sent.",
    "EditWithKvasirAction_modelDepth=Model depth",
    "EditWithKvasirAction_instructionField=Edit instruction",
    "EditWithKvasirAction_sendsNote=<html><small>Sends only the selection, the file name, the language, and your instruction. The reply replaces the selection only after you approve the preview.</small></html>",
    "EditWithKvasirAction_prompt=<html>What should KVASIR change in the selection ({0} chars of <b>{1}</b>)?</html>",
    "EditWithKvasirAction_title=Edit with KVASIR",
    "EditWithKvasirAction_emptyInstruction=Say what to change — an empty instruction sends nothing.",
    "EditWithKvasirAction_drafting=KVASIR is drafting the edit…",
    "EditWithKvasirAction_discarded=KVASIR edit discarded — the file is untouched.",
    "EditWithKvasirAction_currentSelection=Current selection",
    "EditWithKvasirAction_proposes=KVASIR proposes",
    "EditWithKvasirAction_previewNote=<html>Apply replaces the selection in <b>{0}</b> as one undo unit ({1} → {2} chars, {3} → {4} lines). Nothing else in the file changes.</html>",
    "EditWithKvasirAction_apply=Apply",
    "EditWithKvasirAction_keepCurrent=Keep Current Code",
    "EditWithKvasirAction_previewTitle=KVASIR edit — preview",
    "EditWithKvasirAction_applied=KVASIR edit applied — ⌘Z undoes it.",
    "EditWithKvasirAction_fileChanged=The file changed while KVASIR was thinking — nothing was applied. Re-select and try again."
})
public final class EditWithKvasirAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-kvasir-edit", 2, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = focusedEditor();
        String selection = editor == null ? null : editor.getSelectedText();
        if (selection == null || selection.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.EditWithKvasirAction_selectFirst()));
            return;
        }
        if (selection.length() > KvasirEdit.MAX_CODE_CHARS) {
            // refuse, never truncate: a rewrite of a truncated selection
            // would delete the un-sent tail on Apply (the KvasirEdit law)
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    org.nmox.studio.core.util.PlainDialogs.plain(Bundle.EditWithKvasirAction_tooLarge(String.valueOf(selection.length()), String.valueOf(KvasirEdit.MAX_CODE_CHARS)), "Message")));
            return;
        }
        Document doc = editor.getDocument();
        int start = editor.getSelectionStart();
        String fileName = AskKvasirAction.fileName(doc);
        String language = AskKvasirAction.language(doc);

        JTextField instruction = new JTextField();
        javax.swing.JComboBox<String> model =
                new javax.swing.JComboBox<>(AskKvasirModel.labels());
        model.setSelectedIndex(AskKvasirModel.chosenIndex());
        model.getAccessibleContext().setAccessibleName(Bundle.EditWithKvasirAction_modelDepth());
        instruction.getAccessibleContext().setAccessibleName(Bundle.EditWithKvasirAction_instructionField());
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(new JLabel(Bundle.EditWithKvasirAction_sendsNote()),
                BorderLayout.CENTER);
        south.add(model, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(Bundle.EditWithKvasirAction_prompt(String.valueOf(selection.length()), PlainText.escape(fileName))),
                BorderLayout.NORTH);
        panel.add(instruction, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.EditWithKvasirAction_title());
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        String asked = instruction.getText().trim();
        if (asked.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.EditWithKvasirAction_emptyInstruction()));
            return;
        }
        AskKvasirModel.remember(model.getSelectedIndex());
        EditRequest request = new EditRequest(fileName, language, selection, asked);
        String chosenModel = AskKvasirModel.chosen();

        StatusDisplayer.getDefault().setStatusText(Bundle.EditWithKvasirAction_drafting());
        // the send rides the RP (the keychain read can block on an unlock
        // prompt — the v1.56 law); the preview and the apply hop to the EDT
        RP.post(() -> {
            KvasirEditEngine engine = new KvasirEditEngine(new KvasirClient(),
                    KvasirKeys::read,
                    r -> KvasirConsent.requestCodeConsent(new CodeQuestion(
                            r.fileName(), r.language(), r.code(), r.instruction())));
            Proposal proposal = engine.propose(request, chosenModel);
            javax.swing.SwingUtilities.invokeLater(
                    () -> deliver(proposal, doc, start, selection, fileName));
        });
    }

    /** EDT: turns the verdict into the preview or an honest message. */
    private static void deliver(Proposal proposal, Document doc, int start,
            String original, String fileName) {
        if (proposal.status() != KvasirEditEngine.Status.PROPOSED) {
            StatusDisplayer.getDefault().setStatusText("");
            DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(proposal.message(), "Message")));
            return;
        }
        StatusDisplayer.getDefault().setStatusText("");
        if (!showPreview(original, proposal.replacement(), fileName)) {
            StatusDisplayer.getDefault().setStatusText(
                    Bundle.EditWithKvasirAction_discarded());
            return;
        }
        apply(doc, start, original, proposal.replacement());
    }

    /** The BEFORE/AFTER preview. Returns true only on an explicit Apply. */
    private static boolean showPreview(String original, String replacement,
            String fileName) {
        JPanel diff = new JPanel(new GridLayout(1, 2, 8, 0));
        diff.add(titled(Bundle.EditWithKvasirAction_currentSelection(), original));
        diff.add(titled(Bundle.EditWithKvasirAction_proposes(), replacement));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(Bundle.EditWithKvasirAction_previewNote(PlainText.escape(fileName), String.valueOf(original.length()), String.valueOf(replacement.length()), String.valueOf(countLines(original)), String.valueOf(countLines(replacement)))),
                BorderLayout.NORTH);
        panel.add(diff, BorderLayout.CENTER);
        Object applyOption = Bundle.EditWithKvasirAction_apply();
        Object keep = Bundle.EditWithKvasirAction_keepCurrent();
        // Cancel is the default: Enter on a dialog that rewrites the
        // user's file must do nothing (the v1.98.0 safe-default idiom)
        NotifyDescriptor nd = new NotifyDescriptor(panel,
                Bundle.EditWithKvasirAction_previewTitle(), NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE,
                new Object[]{applyOption, keep}, keep);
        return DialogDisplayer.getDefault().notify(nd) == applyOption;
    }

    private static JScrollPane titled(String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, 12));
        area.getAccessibleContext().setAccessibleName(title);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(javax.swing.BorderFactory.createTitledBorder(title));
        scroll.setPreferredSize(new Dimension(380, 320));
        return scroll;
    }

    private static int countLines(String s) {
        return (int) s.chars().filter(c -> c == '\n').count() + 1;
    }

    /** EDT: the guarded, atomic apply — one undo unit or an honest refusal. */
    private static void apply(Document doc, int start, String original,
            String replacement) {
        boolean[] applied = {false};
        Runnable edit = () -> {
            try {
                applied[0] = KvasirEdit.replaceIfUnchanged(doc, start,
                        original, replacement);
            } catch (BadLocationException ex) {
                applied[0] = false;
            }
        };
        try {
            if (doc instanceof StyledDocument styled) {
                NbDocument.runAtomicAsUser(styled, edit);
            } else {
                edit.run();
            }
        } catch (BadLocationException ex) {
            applied[0] = false;
        }
        if (applied[0]) {
            StatusDisplayer.getDefault().setStatusText(
                    Bundle.EditWithKvasirAction_applied());
        } else {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.EditWithKvasirAction_fileChanged()));
        }
    }

    /** The editor under the popup: focus stays on it while a menu shows. */
    private static JTextComponent focusedEditor() {
        java.awt.Component owner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return owner instanceof JTextComponent tc ? tc : null;
    }
}
