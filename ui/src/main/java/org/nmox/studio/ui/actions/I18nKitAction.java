package org.nmox.studio.ui.actions;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.I18nKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * File ▸ I18n Kit (Web)… — the kit family's internationalization
 * member (v2.37.5, David's ask: "all that for i18n, too"), the A11y
 * Kit's sibling: a locales/ catalog pair (English + Spanish, same
 * keys), a dependency-free i18n.js applying data-i18n markup with
 * <html lang> kept truthful, an I18N-NOTES.md checklist for the rules
 * tooling can't enforce, and idempotent index.html wiring for the
 * script tag. Kit laws throughout: never-clobber,
 * run-twice-nothing-doubles, honest refusals.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.I18nKitAction")
@ActionRegistration(displayName = "#CTL_I18nKitAction")
@ActionReference(path = "Menu/File", position = 123)
@Messages("CTL_I18nKitAction=I18n Kit (Web)…")
public final class I18nKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        JCheckBox locales = new JCheckBox(
                "locales/en.json + es.json — one catalog per language, same keys", true);
        JCheckBox helper = new JCheckBox(
                "i18n.js — data-i18n applier, lang kept truthful, no dependencies", true);
        JCheckBox notes = new JCheckBox(
                "I18N-NOTES.md — the rules tooling can't enforce", true);
        JCheckBox wire = new JCheckBox(
                "Wire index.html — the script tag (idempotent)", true);
        locales.getAccessibleContext().setAccessibleName("Write locale catalogs");
        helper.getAccessibleContext().setAccessibleName("Write i18n.js");
        notes.getAccessibleContext().setAccessibleName("Write I18N-NOTES.md");
        wire.getAccessibleContext().setAccessibleName("Wire index.html");

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(locales);
        panel.add(helper);
        panel.add(notes);
        panel.add(wire);
        panel.add(new JLabel("<html><small>Existing files are never overwritten;"
                + " the wiring never doubles and never rewrites your markup —"
                + " problems it can't fix are reported, not touched.</small></html>"));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "I18n Kit — " + project.getName());
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        I18nKit.Options opts = new I18nKit.Options(locales.isSelected(),
                helper.isSelected(), notes.isSelected(), wire.isSelected());
        // disk I/O has no place in an event dispatch; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<I18nKit.Outcome> outcomes = I18nKit.write(project, opts);
                StringBuilder report = new StringBuilder();
                for (I18nKit.Outcome o : outcomes) {
                    report.append(o.written() ? "  ✓ " : "  – ").append(o.path()).append('\n');
                    if (!o.note().isEmpty()) {
                        report.append("      ").append(o.note()).append('\n');
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("I18n Kit:\n\n" + report,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not write: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
