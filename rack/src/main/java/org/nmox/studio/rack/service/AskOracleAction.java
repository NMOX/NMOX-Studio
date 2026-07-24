package org.nmox.studio.rack.service;

import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nmox.studio.rack.engine.AskOracleEngine;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Ask ORACLE about the selected code — the AI surface's editor face.
 * Right-click a selection in ANY editor, ask a question (or none — the
 * default is "explain this"), and the answer arrives in a dialog. The
 * whole ORACLE law set holds: zero boot cost (a menu item), no network
 * without this explicit invocation, the key Keyring-or-env only, and the
 * code flow's OWN one-time consent — the failure-flow consent promises
 * source never leaves the machine, so it cannot cover this.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.rack.service.AskOracleAction")
@ActionRegistration(displayName = "#CTL_AskOracleAction", lazy = true)
@ActionReference(path = "Editors/Popup", position = 1950, separatorBefore = 1940)
@Messages("CTL_AskOracleAction=Ask ORACLE About Selection…")
public final class AskOracleAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-ask-oracle", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = focusedEditor();
        String selection = editor == null ? null : editor.getSelectedText();
        if (selection == null || selection.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Select some code first — Ask ORACLE sends only the selection."));
            return;
        }
        Document doc = editor.getDocument();
        CodeQuestion preview = new CodeQuestion(
                fileName(doc), language(doc), selection, "");

        JTextField question = new JTextField();
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("<html>Question about the selection ("
                + preview.code().length() + " chars of <b>"
                + preview.fileName() + "</b>) — empty asks for an explanation:</html>"),
                BorderLayout.NORTH);
        panel.add(question, BorderLayout.CENTER);
        panel.add(new JLabel("<html><small>Sends only the selection, the file name, "
                + "the language, and your question — never the rest of the file.</small></html>"),
                BorderLayout.SOUTH);

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Ask ORACLE");
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        CodeQuestion q = new CodeQuestion(preview.fileName(), preview.language(),
                selection, question.getText().trim());

        // key peek, consent prompt, and the network call all belong off the
        // EDT (the keychain can block on an unlock prompt — the v1.56 law)
        RP.post(() -> {
            AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                    OracleKeys::read, OracleConsent::requestCodeConsent);
            AskOracleEngine.Result result = engine.answer(q, OracleClient.MODEL_HAIKU);
            SwingUtilities.invokeLater(() -> show(result, q.fileName()));
        });
    }

    private static void show(AskOracleEngine.Result result, String fileName) {
        if (result.status() != AskOracleEngine.Status.ANSWERED) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    result.text(),
                    result.status() == AskOracleEngine.Status.FAILED
                            ? NotifyDescriptor.ERROR_MESSAGE
                            : NotifyDescriptor.INFORMATION_MESSAGE));
            return;
        }
        JTextArea area = new JTextArea(result.text(), 20, 72);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        DialogDescriptor dd = new DialogDescriptor(new JScrollPane(area),
                "ORACLE — " + fileName, false, new Object[]{DialogDescriptor.CLOSED_OPTION},
                DialogDescriptor.CLOSED_OPTION, DialogDescriptor.DEFAULT_ALIGN, null, null);
        DialogDisplayer.getDefault().createDialog(dd).setVisible(true);
    }

    /** The editor under the popup: focus stays on it while a menu shows. */
    private static JTextComponent focusedEditor() {
        java.awt.Component owner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return owner instanceof JTextComponent tc ? tc : null;
    }

    /** The file's display name off the document, or an honest unknown. */
    public static String fileName(Document doc) {
        Object sd = doc == null ? null : doc.getProperty(Document.StreamDescriptionProperty);
        if (sd instanceof DataObject dob) {
            return dob.getPrimaryFile().getNameExt();
        }
        return "(unsaved buffer)";
    }

    /** The document's mime — every NetBeans editor document carries it. */
    public static String language(Document doc) {
        Object mime = doc == null ? null : doc.getProperty("mimeType");
        return mime instanceof String s && !s.isBlank() ? s : "text/plain";
    }
}
