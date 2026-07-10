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
                        : "<html>git — " + root + "<br>click for History / Refresh</html>");
                if (label != null && isDisplayable()) {
                    if (!poll.isRunning()) {
                        poll.start();
                    }
                } else {
                    poll.stop();
                }
            });
        }

        /** Click → the platform git module's own windows, plus a manual Refresh. */
        private void showChipMenu() {
            if (!chip.visible()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            // Only context-free entries live here. The platform's git
            // actions (Show Changes, Diff, Annotate) are NodeActions that
            // read the GLOBAL selection, and no NMOX window publishes one
            // (ledger 29) — verified live: even Team > Git is disabled from
            // this surface. Shipping those buttons here means shipping dead
            // buttons; the Team menu provides them once a file is open.
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

        /** The aimed project dir as a FileObject — Team actions read their root from it. */
        private static FileObject projectContext() {
            File dir = RackService.getDefault().getRack().getProjectDir();
            return FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        }


    }
}
