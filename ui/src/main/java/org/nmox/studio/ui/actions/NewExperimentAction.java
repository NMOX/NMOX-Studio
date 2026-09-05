package org.nmox.studio.ui.actions;

import org.nmox.studio.core.spi.LiveRuns;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.projectstudio.Experiments;
import org.nmox.studio.rack.projectstudio.ProjectTemplates;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * New Experiment: the fastest way to try a stack (v2.36.0, David's
 * ask — the first tool for learning, set up for success). Pick a
 * template, and the studio generates it under ~/.nmox/experiments —
 * no git, no recents, pre-trusted — runs the dependency install so
 * the FIRST Run succeeds, aims the rack, and opens the experiment's
 * own EXPERIMENT.md walkthrough: what to press, what to edit, where
 * this stack's IDE intelligence lives. Keepers graduate via
 * Experiments.promote; the rest die without ceremony. The dialog also
 * fronts the 92-space learning catalog for the guided path.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.NewExperimentAction")
@ActionRegistration(displayName = "#CTL_NewExperimentAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 115),
    @ActionReference(path = "Shortcuts", name = "DS-E")
})
@Messages("CTL_NewExperimentAction=New Experiment…")
public final class NewExperimentAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JComboBox<ProjectTemplates> template = new JComboBox<>(ProjectTemplates.values());
        template.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l,
                    Object v, int i, boolean s, boolean f) {
                ProjectTemplates t = (ProjectTemplates) v;
                return super.getListCellRendererComponent(l,
                        t.getDisplayName() + "  —  " + t.getDescription(), i, s, f);
            }
        });
        template.getAccessibleContext().setAccessibleName("Experiment template");
        JTextField name = new JTextField();
        name.getAccessibleContext().setAccessibleName("Experiment name (optional)");
        JCheckBox installBox = new JCheckBox(
                "Install dependencies so the first Run just works", true);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        rows.add(new JLabel("Template:"));
        rows.add(template);
        rows.add(new JLabel("Name (optional — a throwaway name is fine):"));
        rows.add(name);
        rows.add(installBox);
        rows.add(new JLabel("<html><small>Lives in ~/.nmox/experiments — no git, no recents, "
                + "already trusted. It opens with a walkthrough; promote it later if it "
                + "turns into something.</small></html>"));
        // the guided path, one click away: David's framing is that this
        // dialog is the front door for learning a stack, and the catalog
        // (50 languages / 24 frameworks / 18 libraries) is its deep end
        JButton spaces = new JButton("Guided instead? Browse 92 Learning Spaces…");
        spaces.setToolTipText("Languages, frameworks, and libraries — sample code, a tutorial, a live REPL");
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        south.add(spaces);
        panel.add(rows, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);

        DialogDescriptor descriptor = new DialogDescriptor(panel, "New Experiment");
        java.awt.Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        // dispose() does NOT change the descriptor's value — it stays at
        // its OK initial, so without this flag the create path ran AFTER
        // the hand-off and a phantom experiment appeared (caught live on
        // this button's first real press, v2.36.0)
        boolean[] wentToSpaces = {false};
        spaces.addActionListener(a -> {
            wentToSpaces[0] = true;
            dialog.dispose();
            javax.swing.Action learn = org.openide.awt.Actions.forID("File",
                    "org.nmox.studio.ui.actions.NewLearningSpaceAction");
            if (learn != null) {
                learn.actionPerformed(new ActionEvent(spaces, ActionEvent.ACTION_PERFORMED, "learn"));
            }
        });
        dialog.setVisible(true);
        if (wentToSpaces[0] || descriptor.getValue() != DialogDescriptor.OK_OPTION) {
            return;
        }
        ProjectTemplates chosen = (ProjectTemplates) template.getSelectedItem();
        String chosenName = name.getText();
        boolean install = installBox.isSelected();
        // the full template write ran on the EDT until v1.36 — a big
        // template froze the whole UI; now it runs on the experiments lane
        ManageExperimentsAction.EXPERIMENTS_RP.post(() -> {
            org.netbeans.api.progress.ProgressHandle handle =
                    org.netbeans.api.progress.ProgressHandle.createHandle("Creating experiment…");
            handle.start();
            try {
                File dir = Experiments.create(chosen, chosenName);
                SwingUtilities.invokeLater(() -> {
                    RackService.getDefault().openProjectQuietly(dir);
                    // set up for success: the install runs now, not on the
                    // learner's first confused Run. Same blessing as the New
                    // Project wizard (v1.224.0): the product's own template,
                    // at the user's explicit request, in a pre-trusted dir;
                    // the package.json guard keeps non-Node stacks silent.
                    if (install && new File(dir, "package.json").isFile()) {
                        String pm = org.nmox.studio.rack.devices.ProjectInspector
                                .nodePackageManager(dir);
                        StatusDisplayer.getDefault()
                                .setStatusText("Installing dependencies with " + pm + "…");
                        // joins the toolbar ■ (v2.71.0), like the wizard's install
                        String runLabel = pm + " install — " + dir.getName();
                        String runId = "experiment-setup:" + dir.getAbsolutePath() + "#" + System.nanoTime();
                        org.netbeans.api.progress.ProgressHandle installing =
                                org.netbeans.api.progress.ProgressHandle.createHandle(runLabel, () -> {
                                    LiveRuns.stop(runId);
                                    return true;
                                });
                        installing.start();
                        CommandExecutor.Handle setup = CommandExecutor.run("Experiment Setup", dir, Map.of(),
                                List.of(pm, "install"), line -> {
                                }, code -> {
                                    installing.finish();
                                    LiveRuns.remove(runId);
                                    if (LiveRuns.wasStoppedByUser(runId)) {
                                        StatusDisplayer.getDefault().setStatusText(
                                                "Install stopped — run " + pm + " install when you are ready");
                                        return;
                                    }
                                    reportInstall(pm, code);
                                });
                        LiveRuns.add(new LiveRuns.Run(runId, runLabel, setup::kill));
                    }
                    // the teaching moment: the walkthrough is the first
                    // thing the learner sees, open in the editor
                    openInEditor(new File(dir, Experiments.GUIDE));
                    org.openide.windows.TopComponent workbench = org.openide.windows.WindowManager
                            .getDefault().findTopComponent("ProjectExplorerTopComponent");
                    if (workbench != null) {
                        workbench.open();
                        workbench.requestActive();
                    }
                });
            } catch (Exception ex) {
                String message = "Could not create the experiment: " + ex.getMessage();
                // deferred a dispatch: shown while the wizard is still disposing,
                // the error can stack behind the main window and soft-lock the app
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            } finally {
                handle.finish();
            }
        });
    }

    /**
     * Says how the install went — a silent failure here recreates the
     * v1.212.0 empty-lambda class (a learner with no Node would press
     * Run into a wall with no explanation).
     */
    private static void reportInstall(String pm, int code) {
        SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(
                code == 0
                ? "Dependencies installed — press F6 and follow EXPERIMENT.md"
                : pm + " install failed (exit " + code + ") — Tools ▸ Environment Doctor can help"));
    }

    /** The manager's Open re-uses the same guide-opening path (v2.36.1). */
    static void openGuide(File file) {
        openInEditor(file);
    }

    private static void openInEditor(File file) {
        try {
            org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil
                    .toFileObject(org.openide.filesystems.FileUtil.normalizeFile(file));
            if (fo != null) {
                org.openide.cookies.OpenCookie open = org.openide.loaders.DataObject
                        .find(fo).getLookup().lookup(org.openide.cookies.OpenCookie.class);
                if (open != null) {
                    open.open();
                }
            }
        } catch (Exception ignored) {
            // the walkthrough is on disk regardless; the file tree can open it
        }
    }
}
