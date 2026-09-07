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
import org.nmox.studio.rack.projectstudio.A11yKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * File ▸ A11y Kit (Web)… — the kit family's accessibility member
 * (v2.38.0, David's ask): an a11y.css starter (focus ring,
 * visually-hidden, skip-link reveal, reduced-motion), an
 * A11Y-NOTES.md checklist pointing at the product's own tools, and
 * idempotent index.html wiring (lang, skip link, stylesheet) with a
 * warning — never an edit — when the viewport disables pinch zoom.
 * Kit laws throughout: never-clobber, run-twice-nothing-doubles,
 * honest refusals.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.A11yKitAction")
@ActionRegistration(displayName = "#CTL_A11yKitAction")
@ActionReference(path = "Menu/File", position = 125)
@Messages({
    "CTL_A11yKitAction=A11y Kit (Web)…",
    "A11yKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "A11yKitAction_stylesheetBox=a11y.css — focus ring, visually-hidden, skip link, reduced motion",
    "A11yKitAction_notesBox=A11Y-NOTES.md — the keyboard walk and the questions automation can't answer",
    "A11yKitAction_wireBox=Wire index.html — lang, skip link, the stylesheet (idempotent)",
    "A11yKitAction_stylesheetName=Write a11y.css",
    "A11yKitAction_notesName=Write A11Y-NOTES.md",
    "A11yKitAction_wireName=Wire index.html",
    "A11yKitAction_note=<html><small>Existing files are never overwritten; the wiring never doubles and never rewrites your markup — problems it can't fix are reported, not touched.</small></html>",
    "A11yKitAction_title=A11y Kit — {0}",
    "A11yKitAction_lineWritten=  ✓ {0}",
    "A11yKitAction_lineKept=  – {0}",
    "A11yKitAction_report=A11y Kit:\n\n{0}",
    "A11yKitAction_messageName=Message",
    "A11yKitAction_couldNotWrite=Could not write: {0}"
})
public final class A11yKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.A11yKitAction_aimFirst()));
            return;
        }

        JCheckBox stylesheet = new JCheckBox(
                Bundle.A11yKitAction_stylesheetBox(), true);
        JCheckBox notes = new JCheckBox(
                Bundle.A11yKitAction_notesBox(), true);
        JCheckBox wire = new JCheckBox(
                Bundle.A11yKitAction_wireBox(), true);
        stylesheet.getAccessibleContext().setAccessibleName(Bundle.A11yKitAction_stylesheetName());
        notes.getAccessibleContext().setAccessibleName(Bundle.A11yKitAction_notesName());
        wire.getAccessibleContext().setAccessibleName(Bundle.A11yKitAction_wireName());

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(stylesheet);
        panel.add(notes);
        panel.add(wire);
        panel.add(new JLabel(Bundle.A11yKitAction_note()));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.A11yKitAction_title(project.getName()));
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        A11yKit.Options opts = new A11yKit.Options(
                stylesheet.isSelected(), notes.isSelected(), wire.isSelected());
        // disk I/O has no place in an event dispatch; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<A11yKit.Outcome> outcomes = A11yKit.write(project, opts);
                StringBuilder report = new StringBuilder();
                for (A11yKit.Outcome o : outcomes) {
                    report.append(o.written() ? Bundle.A11yKitAction_lineWritten(o.path())
                            : Bundle.A11yKitAction_lineKept(o.path())).append('\n');
                    if (!o.note().isEmpty()) {
                        report.append("      ").append(o.note()).append('\n');
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.A11yKitAction_report(report), Bundle.A11yKitAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = Bundle.A11yKitAction_couldNotWrite(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.A11yKitAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
