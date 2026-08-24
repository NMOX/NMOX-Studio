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
@ActionReference(path = "Menu/File", position = 122)
@Messages("CTL_A11yKitAction=A11y Kit (Web)…")
public final class A11yKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        JCheckBox stylesheet = new JCheckBox(
                "a11y.css — focus ring, visually-hidden, skip link, reduced motion", true);
        JCheckBox notes = new JCheckBox(
                "A11Y-NOTES.md — the keyboard walk and the questions automation can't answer", true);
        JCheckBox wire = new JCheckBox(
                "Wire index.html — lang, skip link, the stylesheet (idempotent)", true);
        stylesheet.getAccessibleContext().setAccessibleName("Write a11y.css");
        notes.getAccessibleContext().setAccessibleName("Write A11Y-NOTES.md");
        wire.getAccessibleContext().setAccessibleName("Wire index.html");

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(stylesheet);
        panel.add(notes);
        panel.add(wire);
        panel.add(new JLabel("<html><small>Existing files are never overwritten;"
                + " the wiring never doubles and never rewrites your markup —"
                + " problems it can't fix are reported, not touched.</small></html>"));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "A11y Kit — " + project.getName());
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
                    report.append(o.written() ? "  ✓ " : "  – ").append(o.path()).append('\n');
                    if (!o.note().isEmpty()) {
                        report.append("      ").append(o.note()).append('\n');
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("A11y Kit:\n\n" + report,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not write: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
