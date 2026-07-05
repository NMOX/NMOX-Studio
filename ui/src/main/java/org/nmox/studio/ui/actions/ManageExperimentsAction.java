package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
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
 * Experiments, managed: the dialog that makes good on the New
 * Experiment hint ("Promote it later if it turns into something").
 * Lists every experiment with its template and age; Open aims the
 * studio at it, Promote graduates a keeper into a real project (move +
 * git init, then opened for real - recents and all), Discard stops
 * anything running there and deletes the tree.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ManageExperimentsAction")
@ActionRegistration(displayName = "#CTL_ManageExperimentsAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 116),
    @ActionReference(path = "Shortcuts", name = "DS-X")
})
@Messages("CTL_ManageExperimentsAction=Experiments...")
public final class ManageExperimentsAction implements ActionListener {

    /**
     * The one worker lane for experiment filesystem churn (create,
     * promote, discard) — shared with {@link NewExperimentAction}.
     * Discarding a node_modules tree or moving + git-initializing a
     * promotion can take a minute; on the EDT that was a beachball.
     */
    static final org.openide.util.RequestProcessor EXPERIMENTS_RP =
            new org.openide.util.RequestProcessor("Experiments", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        DefaultListModel<File> model = new DefaultListModel<>();
        Experiments.list().forEach(model::addElement);
        if (model.isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "No experiments yet — File → New Experiment… starts one."));
            return;
        }

        JList<File> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                File dir = (File) v;
                Experiments.Info info = Experiments.info(dir);
                return super.getListCellRendererComponent(l,
                        dir.getName() + "   —   " + info.template().toLowerCase()
                        + ", created " + info.created(), i, sel, focus);
            }
        });

        JButton open = new JButton("Open");
        JButton promote = new JButton("Promote…");
        JButton discard = new JButton("Discard…");
        open.setToolTipText("Aim the studio at this experiment");
        promote.setToolTipText("Graduate it: move out of ~/.nmox/experiments, drop the marker, git init");
        discard.setToolTipText("Stop anything running there and delete the tree");

        JPanel buttons = new JPanel();
        buttons.add(open);
        buttons.add(promote);
        buttons.add(discard);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Throwaway workspaces in ~/.nmox/experiments — newest first:"),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(520, 300));

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Experiments",
                true, new Object[]{DialogDescriptor.CLOSED_OPTION}, null, 0, null, null);
        java.awt.Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);

        open.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir != null) {
                dialog.dispose();
                RackService.getDefault().openProjectQuietly(dir);
            }
        });
        // move + git init / recursive delete run on EXPERIMENTS_RP with a
        // ProgressHandle — node_modules trees made these minute-long EDT
        // freezes. All three buttons grey while a worker runs (Open on a
        // dir being discarded would aim the studio at a vanishing tree).
        Runnable disableButtons = () -> {
            open.setEnabled(false);
            promote.setEnabled(false);
            discard.setEnabled(false);
        };
        Runnable enableButtons = () -> {
            open.setEnabled(true);
            promote.setEnabled(true);
            discard.setEnabled(true);
        };

        promote.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
            chooser.setDialogTitle("Promote " + dir.getName() + " into…");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showDialog(dialog, "Promote here") != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File destParent = chooser.getSelectedFile();
            disableButtons.run();
            EXPERIMENTS_RP.post(() -> {
                org.netbeans.api.progress.ProgressHandle handle =
                        org.netbeans.api.progress.ProgressHandle.createHandle("Promoting experiment…");
                handle.start();
                try {
                    File promoted = Experiments.promote(dir, destParent);
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        // a real project now: open loudly so it reaches the recents
                        RackService.getDefault().openProject(promoted);
                        DialogDisplayer.getDefault().notify(
                                new NotifyDescriptor.Message(dir.getName() + " graduated: "
                                        + promoted.getAbsolutePath() + "\n(marker removed, git initialized)",
                                        NotifyDescriptor.INFORMATION_MESSAGE));
                    });
                } catch (Exception ex) {
                    String message = "Could not promote: " + ex.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        enableButtons.run();
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                message, NotifyDescriptor.ERROR_MESSAGE));
                    });
                } finally {
                    handle.finish();
                }
            });
        });
        discard.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            Object answer = DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                    "Discard " + dir.getName() + "? Anything running there is stopped; the tree is deleted.",
                    "Discard Experiment", NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE));
            if (answer != NotifyDescriptor.YES_OPTION) {
                return;
            }
            disableButtons.run();
            EXPERIMENTS_RP.post(() -> {
                org.netbeans.api.progress.ProgressHandle handle =
                        org.netbeans.api.progress.ProgressHandle.createHandle("Discarding experiment…");
                handle.start();
                try {
                    Experiments.discard(dir);
                    SwingUtilities.invokeLater(() -> {
                        enableButtons.run();
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
                        enableButtons.run();
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
