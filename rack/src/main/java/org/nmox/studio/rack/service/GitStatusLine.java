package org.nmox.studio.rack.service;

import org.nmox.studio.core.util.PlainText;
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
@org.openide.util.NbBundle.Messages({
    "GitStatusLine_chipTooltip=<html>git — {0}<br>click for Show Changes / Diff / Annotate / History</html>",
    "GitStatusLine_aimFirst=Aim a project first.",
    "GitStatusLine_ghNotFound=GitHub CLI (gh) not found — install it (brew install gh) and run gh auth login.",
    "GitStatusLine_exitCode=exit {0}",
    "GitStatusLine_ghListFailed=gh could not list pull requests — {0}",
    "GitStatusLine_ghNotPrList=gh answered with something that is not a PR list.",
    "GitStatusLine_noOpenPulls=No open pull requests.",
    "GitStatusLine_colNumber=#",
    "GitStatusLine_colTitle=Title",
    "GitStatusLine_colAuthor=Author",
    "GitStatusLine_colBranch=Branch",
    "GitStatusLine_pullsTableName=Open pull requests",
    "GitStatusLine_openInBrowser=Open in Browser",
    "GitStatusLine_reviewThreads=Review Threads…",
    "GitStatusLine_checkout=Checkout…",
    "GitStatusLine_close=Close",
    "GitStatusLine_pullsDialogTitle=Open pull requests",
    "GitStatusLine_prOpenFailed=Could not open the PR in the Browser.",
    "GitStatusLine_ghThreadsFailed=gh could not read the review threads — {0}",
    "GitStatusLine_ghNotCommentList=gh returned something that is not a comment list.",
    "GitStatusLine_noReviewComments=No review comments on #{0}.",
    "GitStatusLine_showingFirstComments=[showing the first {0} comments]",
    "GitStatusLine_threadsAreaName=Review threads of pull request {0}",
    "GitStatusLine_threadsDialogTitle=Review threads — #{0} {1}",
    "GitStatusLine_gitNotFoundCheckout=git not found — nothing was checked out.",
    "GitStatusLine_gitStatusFailedCheckout=git status failed — nothing was checked out.",
    "GitStatusLine_checkoutRefused=Checkout refused: {0}",
    "GitStatusLine_checkoutConfirm=Check out pull request #{0} ({1}) into the aimed project?\nThe working tree switches branches; nothing is committed or pushed.{2}",
    "GitStatusLine_checkoutTitle=Checkout pull request",
    "GitStatusLine_checkoutCancelled=Checkout cancelled — nothing changed.",
    "GitStatusLine_ghNotFoundCheckout=GitHub CLI (gh) not found — nothing was checked out.",
    "GitStatusLine_leftoversReset=; the attempt's leftovers were reset, the tree is as it was",
    "GitStatusLine_leftoversStaged=; the attempt left staged changes (git reset --hard restores them)",
    "GitStatusLine_ghCheckoutFailed=gh could not check out #{0} — {1}",
    "GitStatusLine_checkedOut=Checked out #{0} ({1}).",
    "GitStatusLine_diffReadFailed=Could not read the staged diff: {0}",
    "GitStatusLine_notARepo=Not a git repository, or git failed — nothing was sent.",
    "GitStatusLine_nothingStaged=Nothing staged — stage changes first (git add), then draft.",
    "GitStatusLine_diffConsentDetail=the STAGED diff of {0} (up to {1} characters) and its changed-file list",
    "GitStatusLine_draftAreaName=Drafted commit message",
    "GitStatusLine_draftHint=Edit as needed, then Copy — committing stays yours.",
    "GitStatusLine_copy=Copy",
    "GitStatusLine_draftTitle=KVASIR commit message — draft",
    "GitStatusLine_draftCopied=Commit message copied — paste it into your commit.",
    "GitStatusLine_showChanges=Show Changes",
    "GitStatusLine_diffProject=Diff Project",
    "GitStatusLine_annotate=Annotate",
    "GitStatusLine_history=History",
    "GitStatusLine_pullRequests=Pull Requests…",
    "GitStatusLine_draftCommit=Draft Commit Message with KVASIR…",
    "GitStatusLine_refresh=Refresh",
    "GitStatusLine_whyNoEditorFile=no file is open in the editor",
    "GitStatusLine_whyNoFolder=the project folder did not resolve",
    "GitStatusLine_whyNoGitModule=the git module is not installed",
    "GitStatusLine_whyRejectedContext=git rejected this context",
    "GitStatusLine_verbUnavailable={0} unavailable ({1}) — use the Team menu",
    "GitStatusLine_noRepository=Git: no repository",
    "GitStatusLine_historyUnavailable=Git history unavailable: {0}"
})
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
                chipLabel.setText(PlainText.plain(label == null ? "" : label));
                // the tooltip MEANS its <br>; the repo path is the one external piece and rides
                // PLAIN-TOOLTIP-EXEMPT: PlainText.escape (a directory can be named <img src=…>)
                chipLabel.setToolTipText(label == null ? null
                        : Bundle.GitStatusLine_chipTooltip(PlainText.escape(String.valueOf(root))));
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

        /**
         * Pull Requests (competitive-lens R6): lists the repo's open
         * PRs via the USER'S OWN gh CLI — their auth, their config; the
         * product never holds a forge token. Fixed-argv read-only spawn
         * behind the structural mayRunProcess guard; every refusal
         * speaks (no gh, no auth, not a GitHub repo — gh's own first
         * error line is the honest message).
         */
        private void showPullRequests() {
            if (!chip.mayRunProcess()) {
                return;
            }
            File dir = RackService.getDefault().getRack().getProjectDir();
            if (dir == null) {
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText(Bundle.GitStatusLine_aimFirst());
                return;
            }
            RP.post(() -> {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult r;
                try {
                    r = org.nmox.studio.core.process.ProcessSupport.runBounded(
                            java.util.List.of("gh", "pr", "list", "--limit",
                                    String.valueOf(org.nmox.studio.rack.engine
                                            .GitPulls.LIMIT),
                                    "--json", "number,title,author,headRefName,url"),
                            dir, java.time.Duration.ofSeconds(10));
                } catch (java.io.IOException ex) {
                    status(Bundle.GitStatusLine_ghNotFound());
                    return;
                }
                if (r.exitCode() != 0) {
                    String first = (r.stderr() == null ? "" : r.stderr())
                            .lines().findFirst().orElse(Bundle.GitStatusLine_exitCode(String.valueOf(r.exitCode())));
                    status(Bundle.GitStatusLine_ghListFailed(first));
                    return;
                }
                java.util.List<org.nmox.studio.rack.engine.GitPulls.Pull> pulls;
                try {
                    pulls = org.nmox.studio.rack.engine.GitPulls.parse(r.stdout());
                } catch (RuntimeException notJson) {
                    status(Bundle.GitStatusLine_ghNotPrList());
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> showPullsDialog(pulls));
            });
        }

        private void showPullsDialog(
                java.util.List<org.nmox.studio.rack.engine.GitPulls.Pull> pulls) {
            if (pulls.isEmpty()) {
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText(Bundle.GitStatusLine_noOpenPulls());
                return;
            }
            String[] cols = {Bundle.GitStatusLine_colNumber(), Bundle.GitStatusLine_colTitle(), Bundle.GitStatusLine_colAuthor(), Bundle.GitStatusLine_colBranch()};
            Object[][] rows = new Object[pulls.size()][];
            for (int i = 0; i < pulls.size(); i++) {
                var p = pulls.get(i);
                rows[i] = new Object[]{p.number(), p.title(), p.author(), p.branch()};
            }
            javax.swing.JTable table = org.nmox.studio.core.util.PlainTables
                    .disableHtml(new javax.swing.JTable(rows, cols) {
                        @Override
                        public boolean isCellEditable(int r, int c) {
                            return false;
                        }
                    });
            table.getAccessibleContext().setAccessibleName(Bundle.GitStatusLine_pullsTableName());
            table.setRowSelectionInterval(0, 0);
            javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
            scroll.setPreferredSize(new java.awt.Dimension(560, 260));
            Object open = Bundle.GitStatusLine_openInBrowser();
            Object threads = Bundle.GitStatusLine_reviewThreads();
            Object checkout = Bundle.GitStatusLine_checkout();
            Object close = Bundle.GitStatusLine_close();
            org.openide.NotifyDescriptor nd = new org.openide.NotifyDescriptor(
                    scroll, Bundle.GitStatusLine_pullsDialogTitle(),
                    org.openide.NotifyDescriptor.DEFAULT_OPTION,
                    org.openide.NotifyDescriptor.PLAIN_MESSAGE,
                    new Object[]{open, threads, checkout, close}, open);
            Object choice = org.openide.DialogDisplayer.getDefault().notify(nd);
            int row = table.getSelectedRow();
            if (row < 0 || choice == close) {
                return;
            }
            var pull = pulls.get(row);
            if (choice == open) {
                String url = pull.url();
                org.nmox.studio.core.spi.EmbeddedBrowser browser =
                        org.nmox.studio.core.spi.EmbeddedBrowser.find();
                if (url.isBlank()
                        || browser == null || !browser.open(url)) {
                    org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText(Bundle.GitStatusLine_prOpenFailed());
                }
            } else if (choice == threads) {
                showReviewThreads(pull);
            } else if (choice == checkout) {
                checkoutPull(pull);
            }
        }

        /**
         * Review Threads (v2.62.0): the PR's review comments through the
         * user's own gh — fixed argv, bounded, parsed by the pure
         * GitReviews (cap + body clip), shown as TEXT in a text area so a
         * body that begins with {@code <html>} stays characters (the
         * v1.306.0 html-render class). Read-only: nothing is posted.
         */
        private void showReviewThreads(org.nmox.studio.rack.engine.GitPulls.Pull pull) {
            // the v1.40.0 boot law, stated structurally at every spawn
            if (!chip.mayRunProcess()) {
                return;
            }
            java.io.File dir = RackService.getDefault().getRack().getProjectDir();
            if (dir == null) {
                status(Bundle.GitStatusLine_aimFirst());
                return;
            }
            RP.post(() -> {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult r;
                try {
                    r = org.nmox.studio.core.process.ProcessSupport.runBounded(
                            java.util.List.of("gh", "api",
                                    "repos/{owner}/{repo}/pulls/" + pull.number() + "/comments"),
                            dir, java.time.Duration.ofSeconds(15));
                } catch (java.io.IOException ex) {
                    status(Bundle.GitStatusLine_ghNotFound());
                    return;
                }
                if (r.exitCode() != 0) {
                    String first = (r.stderr() == null ? "" : r.stderr())
                            .lines().findFirst().orElse(Bundle.GitStatusLine_exitCode(String.valueOf(r.exitCode())));
                    status(Bundle.GitStatusLine_ghThreadsFailed(first));
                    return;
                }
                java.util.List<org.nmox.studio.rack.engine.GitReviews.Comment> comments;
                try {
                    comments = org.nmox.studio.rack.engine.GitReviews.parse(r.stdout());
                } catch (RuntimeException ex) {
                    status(Bundle.GitStatusLine_ghNotCommentList());
                    return;
                }
                if (comments.isEmpty()) {
                    status(Bundle.GitStatusLine_noReviewComments(String.valueOf(pull.number())));
                    return;
                }
                boolean truncated = org.nmox.studio.rack.engine.GitReviews.truncated(r.stdout());
                String text = org.nmox.studio.rack.engine.GitReviews.render(comments)
                        + (truncated ? "\n\n" + Bundle.GitStatusLine_showingFirstComments(
                                String.valueOf(org.nmox.studio.rack.engine.GitReviews.LIMIT)) : "");
                java.awt.EventQueue.invokeLater(() -> {
                    javax.swing.JTextArea area = new javax.swing.JTextArea(text, 24, 80);
                    area.setEditable(false);
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    area.getAccessibleContext().setAccessibleName(
                            Bundle.GitStatusLine_threadsAreaName(String.valueOf(pull.number())));
                    org.openide.DialogDisplayer.getDefault().notify(new org.openide.NotifyDescriptor(
                            new javax.swing.JScrollPane(area),
                            Bundle.GitStatusLine_threadsDialogTitle(String.valueOf(pull.number()), pull.title()),
                            org.openide.NotifyDescriptor.DEFAULT_OPTION,
                            org.openide.NotifyDescriptor.PLAIN_MESSAGE,
                            new Object[]{Bundle.GitStatusLine_close()}, Bundle.GitStatusLine_close()));
                });
            });
        }

        /**
         * Checkout (v2.62.0): {@code gh pr checkout N} in the aimed repo —
         * the one verb on the chip that MOVES the working tree, so it sits
         * behind two guards in order: the pure GitCheckoutGuard refuses a
         * tree with uncommitted changes out loud (a checkout never carries
         * or clobbers work — untracked files alone are allowed, git leaves
         * them in place), then a safe-default confirm names the branch
         * (Enter = No, the v1.98.0 idiom). The chip refreshes afterwards.
         */
        private void checkoutPull(org.nmox.studio.rack.engine.GitPulls.Pull pull) {
            // the v1.40.0 boot law, stated structurally at every spawn
            if (!chip.mayRunProcess()) {
                return;
            }
            java.io.File dir = RackService.getDefault().getRack().getProjectDir();
            if (dir == null) {
                status(Bundle.GitStatusLine_aimFirst());
                return;
            }
            RP.post(() -> {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult st;
                try {
                    st = org.nmox.studio.core.process.ProcessSupport.runBounded(
                            java.util.List.of("git", "status", "--porcelain"),
                            dir, java.time.Duration.ofSeconds(10));
                } catch (java.io.IOException ex) {
                    status(Bundle.GitStatusLine_gitNotFoundCheckout());
                    return;
                }
                if (st.exitCode() != 0) {
                    status(Bundle.GitStatusLine_gitStatusFailedCheckout());
                    return;
                }
                org.nmox.studio.rack.engine.GitCheckoutGuard.Verdict verdict =
                        org.nmox.studio.rack.engine.GitCheckoutGuard.judge(st.stdout());
                if (!verdict.allowed()) {
                    status(Bundle.GitStatusLine_checkoutRefused(verdict.reason()));
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> {
                    String note = verdict.reason().isBlank() ? "" : "\n\n" + verdict.reason();
                    org.openide.NotifyDescriptor confirm = new org.openide.NotifyDescriptor(
                            Bundle.GitStatusLine_checkoutConfirm(String.valueOf(pull.number()), pull.branch(), note),
                            Bundle.GitStatusLine_checkoutTitle(),
                            org.openide.NotifyDescriptor.YES_NO_OPTION,
                            org.openide.NotifyDescriptor.QUESTION_MESSAGE,
                            new Object[]{org.openide.NotifyDescriptor.YES_OPTION,
                                org.openide.NotifyDescriptor.NO_OPTION},
                            org.openide.NotifyDescriptor.NO_OPTION);
                    if (org.openide.DialogDisplayer.getDefault().notify(confirm)
                            != org.openide.NotifyDescriptor.YES_OPTION) {
                        status(Bundle.GitStatusLine_checkoutCancelled());
                        return;
                    }
                    RP.post(() -> {
                        org.nmox.studio.core.process.ProcessSupport.BoundedResult co;
                        try {
                            co = org.nmox.studio.core.process.ProcessSupport.runBounded(
                                    java.util.List.of("gh", "pr", "checkout",
                                            String.valueOf(pull.number())),
                                    dir, java.time.Duration.ofSeconds(60));
                        } catch (java.io.IOException ex) {
                            status(Bundle.GitStatusLine_ghNotFoundCheckout());
                            return;
                        }
                        if (co.exitCode() != 0) {
                            String first = (co.stderr() == null ? "" : co.stderr())
                                    .lines().findFirst().orElse(Bundle.GitStatusLine_exitCode(String.valueOf(co.exitCode())));
                            // gh can die AFTER staging the PR's files (measured on a
                            // shallow clone: "cannot set up tracking information") —
                            // a tree the guard proved clean must not stay dirty from
                            // an attempt the user never saw succeed; HEAD is unchanged,
                            // so reset --hard restores exactly the pre-attempt tracked
                            // state and never touches untracked files
                            String after = porcelain(dir);
                            if (org.nmox.studio.rack.engine.GitCheckoutGuard
                                    .leftoversToRestore(st.stdout(), after)) {
                                try {
                                    org.nmox.studio.core.process.ProcessSupport.runBounded(
                                            java.util.List.of("git", "reset", "--hard"),
                                            dir, java.time.Duration.ofSeconds(30));
                                    first += Bundle.GitStatusLine_leftoversReset();
                                } catch (java.io.IOException ex) {
                                    first += Bundle.GitStatusLine_leftoversStaged();
                                }
                                java.awt.EventQueue.invokeLater(() -> refreshCount());
                            }
                            status(Bundle.GitStatusLine_ghCheckoutFailed(String.valueOf(pull.number()), first));
                            return;
                        }
                        status(Bundle.GitStatusLine_checkedOut(String.valueOf(pull.number()), pull.branch()));
                        java.awt.EventQueue.invokeLater(() -> refreshCount());
                    });
                });
            });
        }

        /**
         * Draft Commit Message with KVASIR: the STAGED diff (fixed-argv
         * read-only git spawn, the GitFacts family — no project code
         * executes, so no trust gate) goes to the API behind the key
         * gate and its OWN consent kind (a diff is a new disclosure
         * class — neither the failure context nor a selection), and the
         * draft lands in an EDITABLE dialog. Nothing here ever runs
         * git commit — the user does the committing.
         */
        private void draftCommitMessage() {
            // the v1.40.0 boot law, stated structurally: every spawn in
            // this chip sits behind mayRunProcess (the menu already
            // requires a visible chip, but the gate reads guards, not
            // reachability)
            if (!chip.mayRunProcess()) {
                return;
            }
            File dir = RackService.getDefault().getRack().getProjectDir();
            if (dir == null) {
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText(Bundle.GitStatusLine_aimFirst());
                return;
            }
            RP.post(() -> {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult stat;
                org.nmox.studio.core.process.ProcessSupport.BoundedResult diff;
                try {
                    stat = org.nmox.studio.core.process.ProcessSupport.runBounded(
                            java.util.List.of("git", "diff", "--staged", "--stat"),
                            dir, java.time.Duration.ofSeconds(5));
                    diff = org.nmox.studio.core.process.ProcessSupport.runBounded(
                            java.util.List.of("git", "diff", "--staged"),
                            dir, java.time.Duration.ofSeconds(5));
                } catch (java.io.IOException ex) {
                    status(Bundle.GitStatusLine_diffReadFailed(ex.getMessage()));
                    return;
                }
                if (stat.exitCode() != 0 || diff.exitCode() != 0) {
                    status(Bundle.GitStatusLine_notARepo());
                    return;
                }
                String rawDiff = diff.stdout();
                if (rawDiff == null || rawDiff.isBlank()) {
                    status(Bundle.GitStatusLine_nothingStaged());
                    return;
                }
                org.nmox.studio.rack.engine.KvasirCommitEngine engine =
                        new org.nmox.studio.rack.engine.KvasirCommitEngine(
                                new org.nmox.studio.rack.engine.KvasirClient(),
                                KvasirKeys::read,
                                project -> KvasirConsent.requestKindConsent("git.diff",
                                        Bundle.GitStatusLine_diffConsentDetail(project,
                                                String.valueOf(org.nmox.studio.rack.engine
                                                        .KvasirCommitMessage.MAX_DIFF_CHARS))));
                org.nmox.studio.rack.engine.KvasirCommitEngine.Draft drafted =
                        engine.draft(dir.getName(), stat.stdout(), rawDiff,
                                AskKvasirModel.chosen());
                if (drafted.status() != org.nmox.studio.rack.engine
                        .KvasirCommitEngine.Status.DRAFTED) {
                    status(drafted.message());
                    return;
                }
                java.awt.EventQueue.invokeLater(() -> showDraft(drafted.message()));
            });
        }

        /** Porcelain of the aimed tree after an attempt; "" when git cannot answer. */
        private static String porcelain(java.io.File dir) {
            try {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult r =
                        org.nmox.studio.core.process.ProcessSupport.runBounded(
                                java.util.List.of("git", "status", "--porcelain"),
                                dir, java.time.Duration.ofSeconds(10));
                return r.exitCode() == 0 && r.stdout() != null ? r.stdout() : "";
            } catch (java.io.IOException ex) {
                return "";
            }
        }

        private static void status(String message) {
            java.awt.EventQueue.invokeLater(() -> org.openide.awt
                    .StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message)));
        }

        /** The editable draft — Copy puts it on the clipboard; never commits. */
        private void showDraft(String message) {
            javax.swing.JTextArea area = new javax.swing.JTextArea(message, 12, 72);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                    java.awt.Font.PLAIN, 12));
            area.getAccessibleContext().setAccessibleName(Bundle.GitStatusLine_draftAreaName());
            javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
            panel.add(new javax.swing.JLabel(
                    Bundle.GitStatusLine_draftHint()),
                    java.awt.BorderLayout.NORTH);
            panel.add(new javax.swing.JScrollPane(area), java.awt.BorderLayout.CENTER);
            Object copy = Bundle.GitStatusLine_copy();
            Object close = Bundle.GitStatusLine_close();
            org.openide.NotifyDescriptor nd = new org.openide.NotifyDescriptor(panel,
                    Bundle.GitStatusLine_draftTitle(),
                    org.openide.NotifyDescriptor.DEFAULT_OPTION,
                    org.openide.NotifyDescriptor.PLAIN_MESSAGE,
                    new Object[]{copy, close}, copy);
            if (org.openide.DialogDisplayer.getDefault().notify(nd) == copy) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer
                                .StringSelection(area.getText()), null);
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText(Bundle.GitStatusLine_draftCopied());
            }
        }

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
            JMenuItem changes = new JMenuItem(Bundle.GitStatusLine_showChanges());
            changes.addActionListener(e -> runGitAction(STATUS_INSTANCE, Bundle.GitStatusLine_showChanges(), null));
            menu.add(changes);
            JMenuItem diff = new JMenuItem(Bundle.GitStatusLine_diffProject());
            diff.addActionListener(e -> runGitAction(DIFF_INSTANCE, Bundle.GitStatusLine_diffProject(), null));
            menu.add(diff);
            JMenuItem annotate = new JMenuItem(Bundle.GitStatusLine_annotate());
            annotate.addActionListener(e -> {
                // registry read must happen on the EDT, before RP work
                File editorFile = currentEditorFile();
                if (editorFile == null) {
                    teamMenuFallback(Bundle.GitStatusLine_annotate(), Bundle.GitStatusLine_whyNoEditorFile());
                    return;
                }
                runGitAction(ANNOTATE_INSTANCE, Bundle.GitStatusLine_annotate(), editorFile);
            });
            menu.add(annotate);
            JMenuItem history = new JMenuItem(Bundle.GitStatusLine_history());
            history.addActionListener(e -> openHistory());
            menu.add(history);
            JMenuItem pulls = new JMenuItem(Bundle.GitStatusLine_pullRequests());
            pulls.addActionListener(e -> showPullRequests());
            menu.add(pulls);
            menu.addSeparator();
            JMenuItem draft = new JMenuItem(Bundle.GitStatusLine_draftCommit());
            draft.addActionListener(e -> draftCommitMessage());
            menu.add(draft);
            menu.addSeparator();
            JMenuItem refresh = new JMenuItem(Bundle.GitStatusLine_refresh());
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
                        teamMenuFallback(verb, Bundle.GitStatusLine_whyNoFolder());
                        return;
                    }
                    Action action = resolveGitAction(instancePath, context);
                    if (action == null || !action.isEnabled()) {
                        teamMenuFallback(verb, action == null
                                ? Bundle.GitStatusLine_whyNoGitModule()
                                : Bundle.GitStatusLine_whyRejectedContext());
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
                    org.nmox.studio.core.util.PlainStatus.text(Bundle.GitStatusLine_verbUnavailable(verb, why)));
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
                StatusDisplayer.getDefault().setStatusText(Bundle.GitStatusLine_noRepository());
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
                        Bundle.GitStatusLine_historyUnavailable(ex.getMessage()));
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
