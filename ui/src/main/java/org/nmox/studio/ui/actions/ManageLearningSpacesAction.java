package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.rack.projectstudio.LearningSpace;
import org.nmox.studio.rack.projectstudio.Experiments;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Learning spaces, managed: the inverse of New Learning Space…
 *
 * <p>Creating a space was one menu item away and removing one was
 * impossible — the directories under {@code ~/.nmox/learn} live forever,
 * so a language you tried once stayed in the daily PROJECTS list for the
 * life of the install (which is exactly what the v1.288.0 Workbench walk
 * ran into). Open aims the studio at a space; Discard stops anything
 * running there, deletes the tree, and drops the space from the recent
 * projects list so the Workbench does not show a row for a directory
 * that is gone.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ManageLearningSpacesAction")
@ActionRegistration(displayName = "#CTL_ManageLearningSpacesAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 113)
})
@Messages("CTL_ManageLearningSpacesAction=Learning Spaces…")
public final class ManageLearningSpacesAction implements ActionListener {

    /**
     * One worker lane for learning-space filesystem churn. A space with
     * an installed toolchain underneath it (node_modules, a cargo
     * target/, a .venv) is a slow tree delete; on the EDT that is a
     * beachball — the ManageExperimentsAction lane, in miniature.
     */
    private static final org.openide.util.RequestProcessor SPACES_RP =
            new org.openide.util.RequestProcessor("Learning Spaces", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        // listing stats ~/.nmox/learn — file IO stays off the paint thread
        // (the v1.33.1 lesson: a network-mounted home must not freeze a click)
        SPACES_RP.post(() -> {
            java.util.List<File> spaces = LearningSpace.list();
            SwingUtilities.invokeLater(() -> showDialog(spaces));
        });
    }

    private void showDialog(java.util.List<File> spaces) {
        if (spaces.isEmpty()) {
            // the empty shelf OFFERS the door, default button acts
            // (the experiments manager's v2.36.1 sentence, mirrored)
            Object browse = "Browse the 92 tutorials…";
            NotifyDescriptor d = new NotifyDescriptor(
                    "No learning spaces yet — pick a language, framework, or"
                    + " library and it arrives with sample code, a walkthrough,"
                    + " and a live REPL.",
                    "Learning Spaces", NotifyDescriptor.OK_CANCEL_OPTION,
                    NotifyDescriptor.PLAIN_MESSAGE,
                    new Object[]{browse, NotifyDescriptor.CANCEL_OPTION}, browse);
            if (DialogDisplayer.getDefault().notify(d) == browse) {
                javax.swing.Action pick = org.openide.awt.Actions.forID("File",
                        "org.nmox.studio.ui.actions.NewLearningSpaceAction");
                if (pick != null) {
                    pick.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "pick"));
                }
            }
            return;
        }
        DefaultListModel<File> model = new DefaultListModel<>();
        spaces.forEach(model::addElement);

        JList<File> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                File dir = (File) v;
                LearningSpace.Info info = LearningSpace.info(dir);
                String label = "?".equals(info.name()) ? dir.getName() : info.name();
                return super.getListCellRendererComponent(l,
                        dir.getName() + "   —   " + label + ", created " + info.created()
                        + ManageExperimentsAction.age(info.created()), i, sel, focus);
            }
        });

        JButton open = new JButton("Open");
        JButton promote = new JButton("Promote…");
        JButton discard = new JButton("Discard…");
        open.setToolTipText("Aim the studio at this learning space");
        promote.setToolTipText("Graduate it: move out of ~/.nmox/learn, drop the marker, git init");
        discard.setToolTipText("Stop anything running there and delete the tree");

        JPanel buttons = new JPanel();
        buttons.add(open);
        buttons.add(promote);
        buttons.add(discard);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel header = new JLabel("Sizing…");
        header.getAccessibleContext().setAccessibleName("Learning spaces shelf summary");
        panel.add(header, BorderLayout.NORTH);
        SPACES_RP.post(() -> {
            long bytes = 0;
            for (File sp : spaces) {
                bytes += Experiments.sizeOf(sp);
            }
            long total = bytes;
            SwingUtilities.invokeLater(() ->
                    header.setText(LearningSpace.shelfSummary(spaces.size(), total)));
        });
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(520, 300));

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Learning Spaces",
                true, new Object[]{DialogDescriptor.CLOSED_OPTION}, null, 0, null, null);
        java.awt.Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);

        promote.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            javax.swing.JFileChooser chooser =
                    new javax.swing.JFileChooser(System.getProperty("user.home"));
            chooser.setDialogTitle("Promote " + dir.getName() + " into…");
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showDialog(dialog, "Promote here") != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            File destParent = chooser.getSelectedFile();
            SPACES_RP.post(() -> {
                try {
                    File promoted = LearningSpace.promote(dir, destParent);
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        // a real project now: open loudly so it reaches the recents
                        RackService.getDefault().openProject(promoted);
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                dir.getName() + " graduated: " + promoted.getAbsolutePath()
                                + "\n(marker removed, git initialized)",
                                NotifyDescriptor.INFORMATION_MESSAGE));
                    });
                } catch (Exception ex) {
                    String message = "Could not promote: " + ex.getMessage();
                    SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                            new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
                }
            });
        });

        open.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir != null) {
                dialog.dispose();
                RackService.getDefault().openProject(dir);
            }
        });
        discard.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            // an irreversible tree delete: the reflexive Enter/Space must NOT
            // land on YES. NotifyDescriptor.Confirmation hard-codes
            // initialValue=OK_OPTION and setValue never moves the default
            // button, so use the full constructor with NO_OPTION (the v1.98.0
            // dialog-safety idiom, as ManageExperimentsAction does).
            NotifyDescriptor confirm = new NotifyDescriptor(
                    "Discard " + dir.getName() + "? Anything running there is stopped"
                            + " and the whole space is deleted from disk.",
                    "Discard Learning Space", NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE,
                    new Object[]{NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},
                    NotifyDescriptor.NO_OPTION);
            if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.YES_OPTION) {
                return;
            }
            open.setEnabled(false);
            discard.setEnabled(false);
            SPACES_RP.post(() -> {
                org.netbeans.api.progress.ProgressHandle handle =
                        org.netbeans.api.progress.ProgressHandle.createHandle("Discarding learning space…");
                handle.start();
                try {
                    LearningSpace.discard(dir);
                    // the tree is gone; a recents row pointing at it would be
                    // a lie until the next prune, so drop it now (v1.288.0)
                    ProjectAim aim = ProjectAim.find();
                    if (aim != null) {
                        aim.forgetRecentProject(dir);
                    }
                    SwingUtilities.invokeLater(() -> {
                        open.setEnabled(true);
                        discard.setEnabled(true);
                        model.removeElement(dir);
                        if (model.isEmpty()) {
                            dialog.dispose();
                        } else {
                            list.setSelectedIndex(0);
                        }
                    });
                } catch (Exception ex) {
                    String message = "Could not discard: " + ex.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        open.setEnabled(true);
                        discard.setEnabled(true);
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                message, NotifyDescriptor.ERROR_MESSAGE));
                    });
                } finally {
                    handle.finish();
                }
            });
        });

        dialog.setVisible(true);
    }
}
