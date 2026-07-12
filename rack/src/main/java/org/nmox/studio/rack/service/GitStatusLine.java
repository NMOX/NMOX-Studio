package org.nmox.studio.rack.service;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.core.util.GitFacts;
import org.openide.awt.StatusDisplayer;
import org.openide.awt.StatusLineElementProvider;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.RequestProcessor;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

/**
 * The git chip: "⎇ main ±3" in the status line when the aimed project
 * lives in a repository, nothing when it doesn't. Branch facts are
 * GitFacts file reads (no forks, safe on every aim event); the dirty
 * count is the one thing that needs the git binary, and it runs only
 * behind {@link GitChip#mayRunProcess()} — a fresh launch aims ~/NMOX,
 * which is not a repo, so boot stays processless (the v1.38.0 law).
 * Clicking the chip opens the platform git module's own windows
 * (Status/Diff/History/Annotate) — this chip is a doorway, not a
 * reimplementation.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 590)
public class GitStatusLine implements StatusLineElementProvider {

    // Pinned from org-netbeans-modules-git's layer (git-layer.xml shadows
    // reference exactly these Actions/Git/ instance files); resolved at
    // runtime via FileUtil.getConfigFile so a missing git module degrades
    // to a status-line message instead of a throw.

    @Override
    public Component getStatusLineElement() {
        return new GitStrip();
    }

    /** Listens and polls only while it is actually in the status bar. */
    private static final class GitStrip extends javax.swing.JPanel {

        /** One lane: branch reads and git-status runs never pile up. */
        private static final RequestProcessor RP = new RequestProcessor("Git Chip", 1);

        private final JLabel chipLabel = new JLabel();
        private final GitChip chip = new GitChip();
        /**
         * Re-arms only while the chip is visible (see publish); a tick is
         * just a poke — the process itself runs on RP behind the boot guard.
         */
        private final Timer poll = new Timer(30_000, e -> tick());
        private final Rack.Listener rackListener = new Rack.Listener() {
            @Override
            public void projectChanged() {
                onAim();
            }
        };

        GitStrip() {
            setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            chipLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            chipLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            chipLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    showChipMenu();
                }
            });
            add(chipLabel);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            RackService.getDefault().getRack().addListener(rackListener);
            // pick up whatever is already aimed; GitChip's equality guard
            // makes the inevitable overlap with projectChanged events free
            onAim();
        }

        @Override
        public void removeNotify() {
            poll.stop();
            RackService.getDefault().getRack().removeListener(rackListener);
            super.removeNotify();
        }

        /** Aim events land here; all file reads happen on RP, never the EDT. */
        private void onAim() {
            RP.post(() -> {
                File dir = RackService.getDefault().getRack().getProjectDir();
                boolean changed = chip.aim(dir);
                publish();
                if (changed) {
                    refreshCount();
                }
            });
        }

        /** Timer ticks poll only while the IDE window is actually active. */
        private void tick() {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == null) {
                return; // backgrounded IDE: the repo isn't going anywhere
            }
            RP.post(this::refreshCount);
        }

        /**
         * The ONLY process spawn in the chip, and it starts with the boot
         * guard: no aim on a repo means no fork, ever — the v1.38.0 law.
         * Always called on RP (aim path, timer tick, and Refresh all post).
         */
        private void refreshCount() {
            if (!chip.mayRunProcess()) {
                return;
            }
            chip.refreshBranch(); // checkouts in a terminal move HEAD under us
            try {
                ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                        List.of("git", "status", "--porcelain"),
                        chip.repoRoot(), Duration.ofSeconds(5));
                if (r.ok()) {
                    chip.porcelain(r.stdout());
                }
                // a failed run keeps the branch showing without a count —
                // better no number than a stale or invented one
            } catch (IOException ex) {
                // git binary missing entirely: same policy, branch only
            }
            publish();
        }

        /** Marshal the chip's current answer onto the EDT; arm/disarm the poll. */
        private void publish() {
            String label = chip.label();
            File root = chip.repoRoot();
            javax.swing.SwingUtilities.invokeLater(() -> {
                chipLabel.setText(label == null ? "" : label);
                chipLabel.setToolTipText(label == null ? null
                        : "<html>git — " + root
                        + "<br>click for Show Changes / Diff / Annotate / History</html>");
                if (label != null && isDisplayable()) {
                    if (!poll.isRunning()) {
                        poll.start();
                    }
                } else {
                    poll.stop();
                }
            });
        }

        // The git module's layer registers exactly these .instance files
        // (pinned from git-layer.xml); resolved via FileUtil.getConfigFile
        // so a missing git module degrades to the Team-menu message.
        private static final String STATUS_INSTANCE =
                "Actions/Git/org-netbeans-modules-git-ui-status-StatusAction.instance";
        private static final String DIFF_INSTANCE =
                "Actions/Git/org-netbeans-modules-git-ui-diff-DiffAction.instance";
        private static final String ANNOTATE_INSTANCE =
                "Actions/Git/org-netbeans-modules-git-ui-blame-AnnotateAction.instance";

        /** Click → the platform git module's own windows, plus a manual Refresh. */
        private void showChipMenu() {
            if (!chip.visible()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            // Context-aware verbs (v1.45.0, ledger 29): with the studios now
            // publishing the aimed DataFolder node as the global selection,
            // the git NodeActions finally have real context — the chip hands
            // them the SAME node explicitly via createContextAwareInstance,
            // so they work even when a non-publishing window is active.
            JMenuItem changes = new JMenuItem("Show Changes");
            changes.addActionListener(e -> runGitAction(STATUS_INSTANCE, "Show Changes", null));
            menu.add(changes);
            JMenuItem diff = new JMenuItem("Diff Project");
            diff.addActionListener(e -> runGitAction(DIFF_INSTANCE, "Diff Project", null));
            menu.add(diff);
            JMenuItem annotate = new JMenuItem("Annotate");
            annotate.addActionListener(e -> {
                // registry read must happen on the EDT, before RP work
                File editorFile = currentEditorFile();
                if (editorFile == null) {
                    teamMenuFallback("Annotate", "no file is open in the editor");
                    return;
                }
                runGitAction(ANNOTATE_INSTANCE, "Annotate", editorFile);
            });
            menu.add(annotate);
            JMenuItem history = new JMenuItem("History");
            history.addActionListener(e -> openHistory());
            menu.add(history);
            menu.addSeparator();
            JMenuItem refresh = new JMenuItem("Refresh");
            refresh.addActionListener(e -> RP.post(this::refreshCount));
            menu.add(refresh);
            menu.show(chipLabel, 0, -menu.getPreferredSize().height);
        }

        /**
         * Runs one of the git module's registered actions against an explicit
         * context: the aimed project's DataFolder node (the same node the
         * studios publish) or, for Annotate, the current editor file's node.
         * File/DataObject resolution runs on RP (disk IO); the action itself
         * is created, enablement-checked and performed on the EDT. A context
         * the action refuses falls back to an honest status message naming
         * the Team menu — never a silent no-op.
         */
        private void runGitAction(String instancePath, String verb, File focusFile) {
            RP.post(() -> {
                Lookup context = contextFor(focusFile);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (context == null) {
                        teamMenuFallback(verb, "the project folder did not resolve");
                        return;
                    }
                    Action action = resolveGitAction(instancePath, context);
                    if (action == null || !action.isEnabled()) {
                        teamMenuFallback(verb, action == null
                                ? "the git module is not installed"
                                : "git rejected this context");
                        return;
                    }
                    action.actionPerformed(new ActionEvent(chipLabel,
                            ActionEvent.ACTION_PERFORMED, verb));
                });
            });
        }

        /**
         * Node + DataObject + FileObject for {@code focusFile} (or the aimed
         * project dir when null) — every lookup shape the git actions' vcs
         * context extraction accepts. Runs on RP: DataObject.find touches disk.
         */
        private static Lookup contextFor(File focusFile) {
            try {
                FileObject fo = focusFile != null
                        ? FileUtil.toFileObject(FileUtil.normalizeFile(focusFile))
                        : projectContext();
                if (fo == null) {
                    return null;
                }
                DataObject dob = DataObject.find(fo);
                return Lookups.fixed(dob.getNodeDelegate(), dob, fo);
            } catch (IOException | RuntimeException ex) {
                return null;
            }
        }

        /** The registered action, context-bound; null when the git module is absent. */
        private static Action resolveGitAction(String instancePath, Lookup context) {
            try {
                FileObject cfg = FileUtil.getConfigFile(instancePath);
                if (cfg == null) {
                    return null;
                }
                InstanceCookie cookie = DataObject.find(cfg).getLookup()
                        .lookup(InstanceCookie.class);
                Object instance = cookie != null ? cookie.instanceCreate() : null;
                if (instance instanceof ContextAwareAction caa) {
                    return caa.createContextAwareInstance(context);
                }
                return instance instanceof Action action ? action : null;
            } catch (IOException | ClassNotFoundException | RuntimeException ex) {
                return null;
            }
        }

        /** The honest refusal: name where the verb still works, never a dead click. */
        private static void teamMenuFallback(String verb, String why) {
            StatusDisplayer.getDefault().setStatusText(
                    verb + " unavailable (" + why + ") — use the Team menu");
        }

        /**
         * History opens through the git module's exported API
         * (org.netbeans.modules.git.api.Git.openSearchHistory) — the one
         * entry point that needs no selection. Reflection because the
         * package is friend-restricted at dependency-resolution time; the
         * system classloader still serves exported packages, and a missing
         * git module degrades to a status message, never a throw.
         */
        private void openHistory() {
            File root = GitFacts.repoRoot(RackService.getDefault().getRack().getProjectDir());
            if (root == null) {
                StatusDisplayer.getDefault().setStatusText("Git: no repository");
                return;
            }
            try {
                ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
                Class<?> git = Class.forName("org.netbeans.modules.git.api.Git", true, system);
                // second arg is a commit-ish, not a path — passing a path
                // makes jgit report "COMMIT [path] does not exist" (found live)
                git.getMethod("openSearchHistory", File.class, String.class)
                        .invoke(null, root, GitFacts.branch(root));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                StatusDisplayer.getDefault().setStatusText(
                        "Git history unavailable: " + ex.getMessage());
            }
        }

        /** The aimed project dir as a FileObject — the git actions read their root from it. */
        private static FileObject projectContext() {
            File dir = RackService.getDefault().getRack().getProjectDir();
            return FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        }
    }

    /**
     * The file the user is editing right now — Annotate needs a concrete
     * file, not a folder. The status line is main-window chrome, not a
     * TopComponent, so clicking the chip does NOT deactivate the editor:
     * the activated TC is usually still it. Falls back to any showing
     * editor tab; null when nothing qualifies. EDT only (registry reads).
     */
    static File currentEditorFile() {
        File activated = fileOf(TopComponent.getRegistry().getActivated());
        if (activated != null) {
            return activated;
        }
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (tc.isShowing()
                    && org.openide.windows.WindowManager.getDefault()
                            .isOpenedEditorTopComponent(tc)) {
                File f = fileOf(tc);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** The TC's file on disk, or null (welcome tabs, studios, unsaved buffers). */
    static File fileOf(TopComponent tc) {
        if (tc == null) {
            return null;
        }
        DataObject dob = tc.getLookup().lookup(DataObject.class);
        return dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
    }
}
