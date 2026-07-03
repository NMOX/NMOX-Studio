package org.nmox.studio.ui.actions;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.StandardsKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * The Standards Kit wizard: one dialog, and the aimed project gains
 * the web's well-known standard files - robots.txt, sitemap.xml,
 * site.webmanifest, RFC 9116 security.txt, humans.txt - each correct
 * to its spec, none overwriting what already exists.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.StandardsKitAction")
@ActionRegistration(displayName = "#CTL_StandardsKitAction")
@ActionReference(path = "Menu/File", position = 117)
@Messages("CTL_StandardsKitAction=Standards Kit...")
public final class StandardsKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        JTextField url = new JTextField("https://example.com");
        JTextField name = new JTextField(project.getName());
        JTextField contact = new JTextField("security@example.com");
        JCheckBox robots = new JCheckBox("robots.txt — Robots Exclusion Protocol (RFC 9309)", true);
        JCheckBox sitemap = new JCheckBox("sitemap.xml — sitemaps.org protocol", true);
        JCheckBox manifest = new JCheckBox("site.webmanifest — W3C Web App Manifest", true);
        JCheckBox security = new JCheckBox(".well-known/security.txt — RFC 9116 (Expires: +1 year)", true);
        JCheckBox humans = new JCheckBox("humans.txt — the people behind the site", false);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Site URL:"));
        panel.add(url);
        panel.add(new JLabel("Site name:"));
        panel.add(name);
        panel.add(new JLabel("Security contact (for security.txt):"));
        panel.add(contact);
        panel.add(new JLabel(" "));
        panel.add(robots);
        panel.add(sitemap);
        panel.add(manifest);
        panel.add(security);
        panel.add(humans);
        panel.add(new JLabel("<html><small>Existing files are never overwritten.</small></html>"));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "Standards Kit — " + project.getName());
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        StandardsKit.Options opts = new StandardsKit.Options(
                url.getText().trim(), name.getText().trim(), contact.getText().trim(),
                robots.isSelected(), sitemap.isSelected(), manifest.isSelected(),
                security.isSelected(), humans.isSelected());
        // disk I/O has no place in an event dispatch; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<StandardsKit.Outcome> outcomes = StandardsKit.write(project, opts);
                StringBuilder report = new StringBuilder();
                for (StandardsKit.Outcome o : outcomes) {
                    report.append(o.written() ? "  ✓ " : "  – ").append(o.path())
                            .append(o.written() ? "" : "  (already exists, untouched)").append('\n');
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("Standards Kit:\n\n" + report,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not write: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
