package org.nmox.studio.editor.lsp;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Opens the language-server install interface: which languages NMOX can
 * light up, which servers are present, and one-click installs for the
 * ones that aren't. Non-modal, so installs run while you keep working.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.editor.lsp.LanguageServerStatusAction")
@ActionRegistration(displayName = "#CTL_LanguageServers")
@ActionReference(path = "Menu/Tools", position = 1450)
@Messages("CTL_LanguageServers=Language Servers…")
public final class LanguageServerStatusAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        LanguageServersPanel panel = new LanguageServersPanel();
        JButton close = new JButton("Close");
        DialogDescriptor d = new DialogDescriptor(panel, "Language Servers", false,
                new Object[]{close}, close, DialogDescriptor.DEFAULT_ALIGN, null, null);
        Dialog dialog = DialogDisplayer.getDefault().createDialog(d);
        close.addActionListener(ev -> dialog.dispose());
        dialog.setVisible(true);
    }
}
