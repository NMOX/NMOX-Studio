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
@ActionReference(path = "Menu/File", position = 124)
@Messages("CTL_ExportLearningSpaceAction=Export as Learning Space…")
public final class ExportLearningSpaceAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at the project you want to export first."));
            return;
        }
        JTextField name = new JTextField(project.getName());
        name.getAccessibleContext().setAccessibleName("Space name");
        JTextField blurb = new JTextField();
        blurb.getAccessibleContext().setAccessibleName("One-line blurb");
        JComboBox<LearningCatalog.Category> category =
                new JComboBox<>(LearningCatalog.Category.values());
        category.getAccessibleContext().setAccessibleName("Category");
        JTextField family = new JTextField("Web");
        family.getAccessibleContext().setAccessibleName("Family");
        JTextField command = new JTextField(
                new File(project, "package.json").isFile() ? "npm run dev" : "");
        command.getAccessibleContext().setAccessibleName("Run command");

        JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        rows.add(new JLabel("Name:"));
        rows.add(name);
        rows.add(new JLabel("Blurb (one line in the picker):"));
        rows.add(blurb);
        rows.add(new JLabel("Category:"));
        rows.add(category);
        rows.add(new JLabel("Family (groups the picker, e.g. \"JavaScript UI\"):"));
        rows.add(family);
        rows.add(new JLabel("Run command (what START runs, space-separated):"));
        rows.add(command);
        rows.add(new JLabel("<html><small>Exports text files (heavy dirs and binaries"
                + " excluded, caps spoken), TUTORIAL.md as the walkthrough, and"
                + " .nmox-checkpoints.json as Check My Work checkpoints. The file is"
                + " only written if it parses with the same code your students'"
                + " picker uses.</small></html>"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor d = new DialogDescriptor(panel,
                "Export as Learning Space — " + project.getName());
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
                StringBuilder msg = new StringBuilder("Exported to:\n  "
                        + out.file() + "\n\n" + out.filesIncluded()
                        + " sample files. Hand this file to your students — it"
                        + " drops into ~/.nmox/learn-catalog.d and \""
                        + opts.name() + "\" appears in their New Learning Space"
                        + " picker, checkpoints included.");
                if (!out.skipped().isEmpty()) {
                    msg.append("\n\nLeft out (spoken, never silent):");
                    for (String s : out.skipped()) {
                        msg.append("\n  – ").append(s);
                    }
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(msg.toString(),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("Not exported: " + ex.getMessage(),
                                NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
