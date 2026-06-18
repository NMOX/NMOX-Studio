package org.nmox.studio.editor.lsp;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JLabel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * "Which language servers do I have?" — a one-look answer for a polyglot
 * workspace, listing every language NMOX can light up and whether its
 * server is installed, with the install command for the ones that aren't.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.editor.lsp.LanguageServerStatusAction")
@ActionRegistration(displayName = "#CTL_LanguageServers")
@ActionReference(path = "Menu/Tools", position = 1450)
@Messages("CTL_LanguageServers=Language Servers…")
public final class LanguageServerStatusAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        // a JLabel renders the HTML report; a bare String would show raw markup
        NotifyDescriptor d = new NotifyDescriptor.Message(
                new JLabel(LanguageServerHealth.statusReportHtml()),
                NotifyDescriptor.PLAIN_MESSAGE);
        d.setTitle("Language Servers");
        DialogDisplayer.getDefault().notify(d);
    }
}
