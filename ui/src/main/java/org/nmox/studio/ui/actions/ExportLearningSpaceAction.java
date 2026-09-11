package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.nmox.studio.rack.projectstudio.SpaceExporter;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * File ▸ Export as Learning Space… (v2.39.3, the developer-teacher
 * persona): the aimed project becomes a drop-in space file — sample
 * files, tutorial, run driver, and any .nmox-checkpoints.json — that
 * a teacher hands their class. Validation is the exporter's: the file
 * is written only after it round-trips through the student picker's
 * own parser, and a broken checkpoint refuses the whole export with
 * its reasons.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ExportLearningSpaceAction")
@ActionRegistration(displayName = "#CTL_ExportLearningSpaceAction")
@ActionReference(path = "Menu/File", position = 114)
@Messages({
    "CTL_ExportLearningSpaceAction=Export as Learning Space…",
    "ExportLearningSpaceAction_aimFirst=Aim the studio at the project you want to export first.",
    "ExportLearningSpaceAction_nameField=Space name",
    "ExportLearningSpaceAction_blurbField=One-line blurb",
    "ExportLearningSpaceAction_categoryField=Category",
    "ExportLearningSpaceAction_familyField=Family",
    "ExportLearningSpaceAction_commandField=Run command",
    "ExportLearningSpaceAction_nameLabel=Name:",
    "ExportLearningSpaceAction_blurbLabel=Blurb (one line in the picker):",
    "ExportLearningSpaceAction_categoryLabel=Category:",
    "ExportLearningSpaceAction_familyLabel=Family (groups the picker, e.g. \"JavaScript UI\"):",
    "ExportLearningSpaceAction_commandLabel=Run command (what START runs, space-separated):",
    "ExportLearningSpaceAction_note=<html><small>Exports text files (heavy dirs and binaries excluded, caps spoken), TUTORIAL.md as the walkthrough, and .nmox-checkpoints.json as Check My Work checkpoints. The file is only written if it parses with the same code your students' picker uses.</small></html>",
    "ExportLearningSpaceAction_title=Export as Learning Space — {0}",
    "ExportLearningSpaceAction_exported=Exported to:\n  {0}\n\n{1} sample files. Hand this file to your students — it drops into ~/.nmox/learn-catalog.d and \"{2}\" appears in their New Learning Space picker, checkpoints included.",
    "ExportLearningSpaceAction_leftOut=\n\nLeft out (spoken, never silent):",
    "ExportLearningSpaceAction_leftOutItem=\n  – {0}",
    "ExportLearningSpaceAction_messageName=Message",
    "ExportLearningSpaceAction_notExported=Not exported: {0}"
})
public final class ExportLearningSpaceAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.ExportLearningSpaceAction_aimFirst()));
            return;
        }
        JTextField name = new JTextField(project.getName());
        name.getAccessibleContext().setAccessibleName(Bundle.ExportLearningSpaceAction_nameField());
        JTextField blurb = new JTextField();
        blurb.getAccessibleContext().setAccessibleName(Bundle.ExportLearningSpaceAction_blurbField());
        JComboBox<LearningCatalog.Category> category =
                new JComboBox<>(LearningCatalog.Category.values());
        category.getAccessibleContext().setAccessibleName(Bundle.ExportLearningSpaceAction_categoryField());
        JTextField family = new JTextField("Web");
        family.getAccessibleContext().setAccessibleName(Bundle.ExportLearningSpaceAction_familyField());
        JTextField command = new JTextField(
                new File(project, "package.json").isFile() ? "npm run dev" : "");
        command.getAccessibleContext().setAccessibleName(Bundle.ExportLearningSpaceAction_commandField());

        JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_nameLabel()));
        rows.add(name);
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_blurbLabel()));
        rows.add(blurb);
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_categoryLabel()));
        rows.add(category);
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_familyLabel()));
        rows.add(family);
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_commandLabel()));
        rows.add(command);
        rows.add(new JLabel(Bundle.ExportLearningSpaceAction_note()));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor d = new DialogDescriptor(panel,
                Bundle.ExportLearningSpaceAction_title(project.getName()));
        if (DialogDisplayer.getDefault().notify(d) != DialogDescriptor.OK_OPTION) {
            return;
        }
        SpaceExporter.Options opts = new SpaceExporter.Options(
                name.getText().strip(), blurb.getText().strip(),
                (LearningCatalog.Category) category.getSelectedItem(),
                family.getText().strip(),
                List.of(Arrays.stream(command.getText().strip().split("\\s+"))
                        .filter(s -> !s.isBlank()).toArray(String[]::new)));
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                SpaceExporter.Outcome out = SpaceExporter.export(project, opts);
                StringBuilder msg = new StringBuilder(Bundle.ExportLearningSpaceAction_exported(
                        out.file(), String.valueOf(out.filesIncluded()), opts.name()));
                if (!out.skipped().isEmpty()) {
                    msg.append(Bundle.ExportLearningSpaceAction_leftOut());
                    for (String s : out.skipped()) {
                        msg.append(Bundle.ExportLearningSpaceAction_leftOutItem(s));
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(msg.toString(), Bundle.ExportLearningSpaceAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ExportLearningSpaceAction_notExported(ex.getMessage()), Bundle.ExportLearningSpaceAction_messageName()),
                                NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
