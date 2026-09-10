package org.nmox.studio.ui.actions;

import org.nmox.studio.core.util.PlainText;
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
    @ActionReference(path = "Menu/File", position = 121),
    @ActionReference(path = "Shortcuts", name = "DS-X")
})
@Messages({
    "CTL_ManageExperimentsAction=Experiments…",
    "ManageExperimentsAction_shelfA11y=Experiments shelf",
    "ManageExperimentsAction_startOne=Start an Experiment…",
    "ManageExperimentsAction_ageToday= · today",
    "ManageExperimentsAction_ageDay= · {0} day ago",
    "ManageExperimentsAction_ageDays= · {0} days ago",
    "ManageExperimentsAction_emptyShelf=No experiments yet. An experiment is the fastest way to try a stack —\na throwaway workspace that opens with its own walkthrough, dependencies\ninstalled, ready to Run.",
    "ManageExperimentsAction_title=Experiments",
    "ManageExperimentsAction_row={0}   —   {1}, created {2}{3}",
    "ManageExperimentsAction_open=Open",
    "ManageExperimentsAction_duplicate=Duplicate",
    "ManageExperimentsAction_duplicateTip=Fork it: a full copy beside the original, to try a second approach",
    "ManageExperimentsAction_duplicateName=Duplicate the selected experiment",
    "ManageExperimentsAction_promote=Promote…",
    "ManageExperimentsAction_discard=Discard…",
    "ManageExperimentsAction_openTip=Aim the studio at this experiment",
    "ManageExperimentsAction_promoteTip=Graduate it: move out of ~/.nmox/experiments, drop the marker, git init",
    "ManageExperimentsAction_discardTip=Stop anything running there and delete the tree",
    "ManageExperimentsAction_experimentSingular=experiment",
    "ManageExperimentsAction_experimentPlural=experiments",
    "ManageExperimentsAction_headerSizing={0} in ~/.nmox/experiments — newest first. Sizing…",
    "ManageExperimentsAction_forked=Forked {0} → {1} — the original is untouched.",
    "ManageExperimentsAction_couldNotDuplicate=Could not duplicate: {0}",
    "ManageExperimentsAction_messageName=Message",
    "ManageExperimentsAction_promoteInto=Promote {0} into…",
    "ManageExperimentsAction_promoteHere=Promote here",
    "ManageExperimentsAction_promoting=Promoting experiment…",
    "ManageExperimentsAction_graduated={0} graduated: {1}\n(marker removed, git initialized)",
    "ManageExperimentsAction_couldNotPromote=Could not promote: {0}",
    "ManageExperimentsAction_discardQuestion=Discard {0}? Anything running there is stopped; the tree is deleted.",
    "ManageExperimentsAction_discardTitle=Discard Experiment",
    "ManageExperimentsAction_discarding=Discarding experiment…",
    "ManageExperimentsAction_discarded=Discarded {0} — discarding is what keeps experiments cheap to start.",
    "ManageExperimentsAction_couldNotDiscard=Could not discard: {0}"
})
public final class ManageExperimentsAction implements ActionListener {

    /** The empty shelf's one useful button (v2.36.1). */
    static final String START_ONE = Bundle.ManageExperimentsAction_startOne();

    /**
     * The one worker lane for experiment filesystem churn (create,
     * promote, discard) — shared with {@link NewExperimentAction}.
     * Discarding a node_modules tree or moving + git-initializing a
     * promotion can take a minute; on the EDT that was a beachball.
     */
    static final org.openide.util.RequestProcessor EXPERIMENTS_RP =
            new org.openide.util.RequestProcessor("Experiments", 1);

    /**
     * " · N days ago" from the marker's created date — the nudge that
     * makes a stale shelf visible (v2.36.0). Unparseable dates (the
     * "?" placeholder, a hand-edited marker) render nothing.
     */
    static String age(String created) {
        try {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(created), java.time.LocalDate.now());
            if (days <= 0) {
                return Bundle.ManageExperimentsAction_ageToday();
            }
            return days == 1 ? Bundle.ManageExperimentsAction_ageDay(String.valueOf(days))
                    : Bundle.ManageExperimentsAction_ageDays(String.valueOf(days));
        } catch (RuntimeException unparseable) {
            return "";
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DefaultListModel<File> model = new DefaultListModel<>();
        Experiments.list().forEach(model::addElement);
        if (model.isEmpty()) {
            // the empty shelf TEACHES AND ACTS (v2.36.1): a dead-end
            // message made the learner walk back through the File menu;
            // now the shelf's front door is one click away
            NotifyDescriptor offer = new NotifyDescriptor(
                    Bundle.ManageExperimentsAction_emptyShelf(),
                    Bundle.ManageExperimentsAction_title(), NotifyDescriptor.OK_CANCEL_OPTION,
                    NotifyDescriptor.INFORMATION_MESSAGE,
                    new Object[]{START_ONE, NotifyDescriptor.CANCEL_OPTION}, START_ONE);
            if (DialogDisplayer.getDefault().notify(offer) == START_ONE) {
                new NewExperimentAction().actionPerformed(e);
            }
            return;
        }

        JList<File> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName(Bundle.ManageExperimentsAction_shelfA11y());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                File dir = (File) v;
                Experiments.Info info = Experiments.info(dir);
                return super.getListCellRendererComponent(l,
                        Bundle.ManageExperimentsAction_row(dir.getName(),
                                info.template().toLowerCase(java.util.Locale.ROOT),
                                info.created(), age(info.created())), i, sel, focus);
            }
        });

        JButton open = new JButton(Bundle.ManageExperimentsAction_open());
        JButton duplicate = new JButton(Bundle.ManageExperimentsAction_duplicate());
        duplicate.setToolTipText(Bundle.ManageExperimentsAction_duplicateTip());
        duplicate.getAccessibleContext().setAccessibleName(Bundle.ManageExperimentsAction_duplicateName());
        JButton promote = new JButton(Bundle.ManageExperimentsAction_promote());
        JButton discard = new JButton(Bundle.ManageExperimentsAction_discard());
        open.setToolTipText(Bundle.ManageExperimentsAction_openTip());
        promote.setToolTipText(Bundle.ManageExperimentsAction_promoteTip());
        discard.setToolTipText(Bundle.ManageExperimentsAction_discardTip());

        JPanel buttons = new JPanel();
        buttons.add(open);
        buttons.add(duplicate);
        buttons.add(promote);
        buttons.add(discard);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel header = new JLabel(PlainText.plain(Bundle.ManageExperimentsAction_headerSizing(
                org.nmox.studio.core.util.Plural.of(model.size(),
                        Bundle.ManageExperimentsAction_experimentSingular(),
                        Bundle.ManageExperimentsAction_experimentPlural()))));
        panel.add(header, BorderLayout.NORTH);
        // the disk cost lands when the walk finishes — node_modules
        // trees make this seconds, never an EDT freeze (v1.33.1 law)
        EXPERIMENTS_RP.post(() -> {
            long bytes = 0;
            for (int i = 0; i < model.size(); i++) {
                bytes += Experiments.sizeOf(model.get(i));
            }
            long total = bytes;
            int count = model.size();
            SwingUtilities.invokeLater(() ->
                    header.setText(PlainText.plain(Experiments.shelfSummary(count, total))));
        });
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(520, 300));

        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.ManageExperimentsAction_title(),
                true, new Object[]{DialogDescriptor.CLOSED_OPTION}, null, 0, null, null);
        java.awt.Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);

        open.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir != null) {
                dialog.dispose();
                RackService.getDefault().openProjectQuietly(dir);
                // returning to an experiment re-opens its walkthrough —
                // the guide is the experiment's memory of where you were
                // (pre-v2.36.0 experiments have none; nothing opens)
                File guide = new File(dir, Experiments.GUIDE);
                if (guide.isFile()) {
                    NewExperimentAction.openGuide(guide);
                }
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

        duplicate.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            duplicate.setEnabled(false);
            EXPERIMENTS_RP.post(() -> {
                try {
                    File fork = Experiments.duplicate(dir);
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        RackService.getDefault().openProjectQuietly(fork);
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                Bundle.ManageExperimentsAction_forked(dir.getName(), fork.getName()));
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        duplicate.setEnabled(true);
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ManageExperimentsAction_couldNotDuplicate(ex.getMessage()),
                                        Bundle.ManageExperimentsAction_messageName()),
                                NotifyDescriptor.ERROR_MESSAGE));
                    });
                }
            });
        });
        promote.addActionListener(a -> {
            File dir = list.getSelectedValue();
            if (dir == null) {
                return;
            }
            JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
            chooser.setDialogTitle(Bundle.ManageExperimentsAction_promoteInto(dir.getName()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showDialog(dialog, Bundle.ManageExperimentsAction_promoteHere()) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File destParent = chooser.getSelectedFile();
            disableButtons.run();
            EXPERIMENTS_RP.post(() -> {
                org.netbeans.api.progress.ProgressHandle handle =
                        org.netbeans.api.progress.ProgressHandle.createHandle(Bundle.ManageExperimentsAction_promoting());
                handle.start();
                try {
                    File promoted = Experiments.promote(dir, destParent);
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        // a real project now: open loudly so it reaches the recents
                        RackService.getDefault().openProject(promoted);
                        DialogDisplayer.getDefault().notify(
                                new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ManageExperimentsAction_graduated(
                                        dir.getName(), promoted.getAbsolutePath()),
                                        Bundle.ManageExperimentsAction_messageName()),
                                        NotifyDescriptor.INFORMATION_MESSAGE));
                    });
                } catch (Exception ex) {
                    String message = Bundle.ManageExperimentsAction_couldNotPromote(ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        enableButtons.run();
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.ManageExperimentsAction_messageName()), NotifyDescriptor.ERROR_MESSAGE));
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
            // Discard is an irreversible tree delete — the reflexive Enter/Space
            // must NOT land on YES. NotifyDescriptor.Confirmation hard-codes
            // initialValue=OK_OPTION and setValue never moves the default button,
            // so use the full constructor with NO_OPTION as the initial value
            // (the v1.98.0 infra dialog-safety idiom).
            NotifyDescriptor confirm = new NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ManageExperimentsAction_discardQuestion(dir.getName()),
                            Bundle.ManageExperimentsAction_messageName()),
                    Bundle.ManageExperimentsAction_discardTitle(), NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE,
                    new Object[]{NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},
                    NotifyDescriptor.NO_OPTION);
            Object answer = DialogDisplayer.getDefault().notify(confirm);
            if (answer != NotifyDescriptor.YES_OPTION) {
                return;
            }
            disableButtons.run();
            EXPERIMENTS_RP.post(() -> {
                org.netbeans.api.progress.ProgressHandle handle =
                        org.netbeans.api.progress.ProgressHandle.createHandle(Bundle.ManageExperimentsAction_discarding());
                handle.start();
                try {
                    Experiments.discard(dir);
                    SwingUtilities.invokeLater(() -> {
                        enableButtons.run();
                        model.removeElement(dir);
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                Bundle.ManageExperimentsAction_discarded(dir.getName()));
                        if (model.isEmpty()) {
                            dialog.dispose();
                        } else {
                            list.setSelectedIndex(0);
                        }
                    });
                } catch (Exception ex) {
                    String message = Bundle.ManageExperimentsAction_couldNotDiscard(ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        enableButtons.run();
                        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                                org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.ManageExperimentsAction_messageName()), NotifyDescriptor.ERROR_MESSAGE));
                    });
                } finally {
                    handle.finish();
                }
            });
        });

        dialog.setVisible(true);
    }
}
