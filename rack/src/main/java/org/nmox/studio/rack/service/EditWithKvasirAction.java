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
@Messages("CTL_EditWithKvasirAction=Edit with KVASIR…")
public final class EditWithKvasirAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-kvasir-edit", 2, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = focusedEditor();
        String selection = editor == null ? null : editor.getSelectedText();
        if (selection == null || selection.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Select some code first — Edit with KVASIR rewrites only the selection."));
            return;
        }
        if (selection.length() > KvasirEdit.MAX_CODE_CHARS) {
            // refuse, never truncate: a rewrite of a truncated selection
            // would delete the un-sent tail on Apply (the KvasirEdit law)
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    org.nmox.studio.core.util.PlainDialogs.plain("Selection too large for an KVASIR edit ("
                    + selection.length() + " chars, cap "
                    + KvasirEdit.MAX_CODE_CHARS + ") — nothing was sent.", "Message")));
            return;
        }
        Document doc = editor.getDocument();
        int start = editor.getSelectionStart();
        String fileName = AskKvasirAction.fileName(doc);
        String language = AskKvasirAction.language(doc);

        JTextField instruction = new JTextField();
        javax.swing.JComboBox<String> model =
                new javax.swing.JComboBox<>(AskKvasirModel.LABELS);
        model.setSelectedIndex(AskKvasirModel.chosenIndex());
        model.getAccessibleContext().setAccessibleName("Model depth");
        instruction.getAccessibleContext().setAccessibleName("Edit instruction");
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(new JLabel("<html><small>Sends only the selection, the file name, "
                + "the language, and your instruction. The reply replaces the "
                + "selection only after you approve the preview.</small></html>"),
                BorderLayout.CENTER);
        south.add(model, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("<html>What should KVASIR change in the selection ("
                + selection.length() + " chars of <b>" + PlainText.escape(fileName) + "</b>)?</html>"),
                BorderLayout.NORTH);
        panel.add(instruction, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Edit with KVASIR");
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        String asked = instruction.getText().trim();
        if (asked.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Say what to change — an empty instruction sends nothing."));
            return;
        }
        AskKvasirModel.remember(model.getSelectedIndex());
        EditRequest request = new EditRequest(fileName, language, selection, asked);
        String chosenModel = AskKvasirModel.chosen();

        StatusDisplayer.getDefault().setStatusText("KVASIR is drafting the edit…");
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
                    "KVASIR edit discarded — the file is untouched.");
            return;
        }
        apply(doc, start, original, proposal.replacement());
    }

    /** The BEFORE/AFTER preview. Returns true only on an explicit Apply. */
    private static boolean showPreview(String original, String replacement,
            String fileName) {
        JPanel diff = new JPanel(new GridLayout(1, 2, 8, 0));
        diff.add(titled("Current selection", original));
        diff.add(titled("KVASIR proposes", replacement));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("<html>Apply replaces the selection in <b>" + PlainText.escape(fileName)
                + "</b> as one undo unit (" + original.length() + " → "
                + replacement.length() + " chars, "
                + countLines(original) + " → " + countLines(replacement)
                + " lines). Nothing else in the file changes.</html>"),
                BorderLayout.NORTH);
        panel.add(diff, BorderLayout.CENTER);
        Object applyOption = "Apply";
        Object keep = "Keep Current Code";
        // Cancel is the default: Enter on a dialog that rewrites the
        // user's file must do nothing (the v1.98.0 safe-default idiom)
        NotifyDescriptor nd = new NotifyDescriptor(panel,
                "KVASIR edit — preview", NotifyDescriptor.DEFAULT_OPTION,
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
                    "KVASIR edit applied — ⌘Z undoes it.");
        } else {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "The file changed while KVASIR was thinking — nothing was "
                    + "applied. Re-select and try again."));
        }
    }

    /** The editor under the popup: focus stays on it while a menu shows. */
    private static JTextComponent focusedEditor() {
        java.awt.Component owner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return owner instanceof JTextComponent tc ? tc : null;
    }
}
