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
@ActionReference(path = "Menu/File/AddToProject", position = 10)
@Messages({
    "CTL_StandardsKitAction=Standards Kit…",
    "StandardsKitAction_needsName=Give the site a name — it goes into the manifest and humans.txt.",
    "StandardsKitAction_urlNeedsSchemeHost=The site URL needs a scheme and host, like https://example.com — got \"{0}\".",
    "StandardsKitAction_urlUnparseable=That site URL doesn''t parse: \"{0}\".",
    "StandardsKitAction_contactInvalid=security.txt needs a real contact address (RFC 9116) — \"{0}\" doesn''t look like one.",
    "StandardsKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "StandardsKitAction_urlField=Site URL",
    "StandardsKitAction_nameField=Site name",
    "StandardsKitAction_contactField=Security contact",
    "StandardsKitAction_robotsBox=robots.txt — Robots Exclusion Protocol (RFC 9309)",
    "StandardsKitAction_sitemapBox=sitemap.xml — sitemaps.org protocol",
    "StandardsKitAction_manifestBox=site.webmanifest — W3C Web App Manifest",
    "StandardsKitAction_securityBox=.well-known/security.txt — RFC 9116 (Expires: +1 year)",
    "StandardsKitAction_humansBox=humans.txt — the people behind the site",
    "StandardsKitAction_urlLabel=Site URL:",
    "StandardsKitAction_nameLabel=Site name:",
    "StandardsKitAction_contactLabel=Security contact (for security.txt):",
    "StandardsKitAction_note=<html><small>Existing files are never overwritten.</small></html>",
    "StandardsKitAction_title=Standards Kit — {0}",
    "StandardsKitAction_messageName=Message",
    "StandardsKitAction_lineWritten=  ✓ {0}",
    "StandardsKitAction_lineKept=  – {0}  (already exists, untouched)",
    "StandardsKitAction_report=Standards Kit:\n\n{0}",
    "StandardsKitAction_couldNotWrite=Could not write: {0}"
})
public final class StandardsKitAction implements ActionListener {

    /** The generated files are only as valid as their inputs. */
    static String validate(String url, String name, String contact, boolean securityTxt) {
        if (name.isBlank()) {
            return Bundle.StandardsKitAction_needsName();
        }
        try {
            java.net.URI parsed = java.net.URI.create(url);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                return Bundle.StandardsKitAction_urlNeedsSchemeHost(url);
            }
        } catch (IllegalArgumentException bad) {
            return Bundle.StandardsKitAction_urlUnparseable(url);
        }
        if (securityTxt && !contact.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return Bundle.StandardsKitAction_contactInvalid(contact);
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.StandardsKitAction_aimFirst()));
            return;
        }

        JTextField url = new JTextField("https://example.com");
        url.getAccessibleContext().setAccessibleName(Bundle.StandardsKitAction_urlField());
        JTextField name = new JTextField(project.getName());
        name.getAccessibleContext().setAccessibleName(Bundle.StandardsKitAction_nameField());
        JTextField contact = new JTextField("security@example.com");
        contact.getAccessibleContext().setAccessibleName(Bundle.StandardsKitAction_contactField());
        JCheckBox robots = new JCheckBox(Bundle.StandardsKitAction_robotsBox(), true);
        JCheckBox sitemap = new JCheckBox(Bundle.StandardsKitAction_sitemapBox(), true);
        JCheckBox manifest = new JCheckBox(Bundle.StandardsKitAction_manifestBox(), true);
        JCheckBox security = new JCheckBox(Bundle.StandardsKitAction_securityBox(), true);
        JCheckBox humans = new JCheckBox(Bundle.StandardsKitAction_humansBox(), false);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(Bundle.StandardsKitAction_urlLabel()));
        panel.add(url);
        panel.add(new JLabel(Bundle.StandardsKitAction_nameLabel()));
        panel.add(name);
        panel.add(new JLabel(Bundle.StandardsKitAction_contactLabel()));
        panel.add(contact);
        panel.add(new JLabel(" "));
        panel.add(robots);
        panel.add(sitemap);
        panel.add(manifest);
        panel.add(security);
        panel.add(humans);
        panel.add(new JLabel(Bundle.StandardsKitAction_note()));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.StandardsKitAction_title(project.getName()));
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        String problem = validate(url.getText().trim(), name.getText().trim(),
                contact.getText().trim(), security.isSelected());
        if (problem != null) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(problem, Bundle.StandardsKitAction_messageName()), NotifyDescriptor.WARNING_MESSAGE)));
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
                    report.append(o.written() ? Bundle.StandardsKitAction_lineWritten(o.path())
                            : Bundle.StandardsKitAction_lineKept(o.path())).append('\n');
                    if (!o.note().isEmpty()) {
                        report.append("      ").append(o.note()).append('\n');
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.StandardsKitAction_report(report), Bundle.StandardsKitAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = Bundle.StandardsKitAction_couldNotWrite(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.StandardsKitAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
