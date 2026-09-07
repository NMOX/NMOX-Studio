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
@Messages({
    "CTL_I18nKitAction=I18n Kit (Web)…",
    "I18nKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "I18nKitAction_localesBox=locales/en.json + es.json — one catalog per language, same keys",
    "I18nKitAction_helperBox=i18n.js — data-i18n applier, lang kept truthful, no dependencies",
    "I18nKitAction_notesBox=I18N-NOTES.md — the rules tooling can't enforce",
    "I18nKitAction_wireBox=Wire index.html — the script tag (idempotent)",
    "I18nKitAction_localesName=Write locale catalogs",
    "I18nKitAction_helperName=Write i18n.js",
    "I18nKitAction_notesName=Write I18N-NOTES.md",
    "I18nKitAction_wireName=Wire index.html",
    "I18nKitAction_note=<html><small>Existing files are never overwritten; the wiring never doubles and never rewrites your markup — problems it can't fix are reported, not touched.</small></html>",
    "I18nKitAction_title=I18n Kit — {0}",
    "I18nKitAction_lineWritten=  ✓ {0}",
    "I18nKitAction_lineKept=  – {0}",
    "I18nKitAction_report=I18n Kit:\n\n{0}",
    "I18nKitAction_messageName=Message",
    "I18nKitAction_couldNotWrite=Could not write: {0}"
})
public final class I18nKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.I18nKitAction_aimFirst()));
            return;
        }

        JCheckBox locales = new JCheckBox(
                Bundle.I18nKitAction_localesBox(), true);
        JCheckBox helper = new JCheckBox(
                Bundle.I18nKitAction_helperBox(), true);
        JCheckBox notes = new JCheckBox(
                Bundle.I18nKitAction_notesBox(), true);
        JCheckBox wire = new JCheckBox(
                Bundle.I18nKitAction_wireBox(), true);
        locales.getAccessibleContext().setAccessibleName(Bundle.I18nKitAction_localesName());
        helper.getAccessibleContext().setAccessibleName(Bundle.I18nKitAction_helperName());
        notes.getAccessibleContext().setAccessibleName(Bundle.I18nKitAction_notesName());
        wire.getAccessibleContext().setAccessibleName(Bundle.I18nKitAction_wireName());

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(locales);
        panel.add(helper);
        panel.add(notes);
        panel.add(wire);
        panel.add(new JLabel(Bundle.I18nKitAction_note()));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.I18nKitAction_title(project.getName()));
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
                    report.append(o.written() ? Bundle.I18nKitAction_lineWritten(o.path())
                            : Bundle.I18nKitAction_lineKept(o.path())).append('\n');
                    if (!o.note().isEmpty()) {
                        report.append("      ").append(o.note()).append('\n');
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.I18nKitAction_report(report), Bundle.I18nKitAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = Bundle.I18nKitAction_couldNotWrite(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.I18nKitAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
