package org.nmox.studio.ui;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.openide.modules.OnStart;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The docs screenshot forge: boot the app with
 * {@code -J-Dnmox.shots.dir=/some/dir} and it cycles each suite tab,
 * paints the main window into a PNG per tab, and exits. Screenshots for
 * the tutorials become a one-command pipeline ({@code scripts/docs-shots.sh})
 * instead of a manual capture chore — re-runnable every release, so the
 * pictures never drift from the shipping UI.
 *
 * <p>The capture is Swing painting straight to an image — no OS
 * screen-recording permission, no window compositor, and pixel-perfect at
 * 2x for crisp docs. Without the property this is a single
 * {@code getProperty} at boot: the zero-boot-cost law holds.
 */
@OnStart
public class DocsShots implements Runnable {

    /** TopComponent preferredID → tutorial image name (insertion order = capture order). */
    static final Map<String, String> SHOTS = new LinkedHashMap<>();

    static {
        // the Workbench is the left-dock ProjectExplorer TC (display name
        // "Workbench") — MainWindowTopComponent is the Welcome launchpad
        // workbench.png is taken FIRST and so is also the first-launch
        // picture the user guide's chapter 2 shows: the Welcome filling the
        // editor area, the Workbench docked, and only the tabs a fresh
        // install actually opens (v2.118.0). The hand-staged welcome.png it
        // replaced had rotted to a 2.93.0 window still saying "Ask ORACLE".
        SHOTS.put("ProjectExplorerTopComponent", "workbench.png");
        SHOTS.put("RackTopComponent", "the-task-rack.png");
        SHOTS.put("ProjectStudioTopComponent", "project-studio.png");
        SHOTS.put("DbStudioTopComponent", "db-studio.png");
        SHOTS.put("Web3StudioTopComponent", "contract-studio.png");
        SHOTS.put("InfraDesignerTopComponent", "infra-designer.png");
        SHOTS.put("ApiClientTopComponent", "api-studio.png");
        SHOTS.put("DockerPanelTopComponent", "docker-panel.png");
        SHOTS.put("BlockStudioTopComponent", "block-studio.png");
    }

    /**
     * Forge v2: dialog shots. "Category/action-id" → image name; the action
     * is invoked exactly as a menu click would, the dialog it shows (modal
     * dialogs pump a secondary event loop, so timers keep firing) is painted,
     * then disposed — the blocked action returns with a CLOSED verdict and
     * nothing is created.
     */
    static final Map<String, String> DIALOG_SHOTS = new LinkedHashMap<>();

    static {
        DIALOG_SHOTS.put("File/org.nmox.studio.ui.actions.NewLearningSpaceAction",
                "learning-spaces.png");
        DIALOG_SHOTS.put("File/org.nmox.studio.ui.actions.StandardsKitAction",
                "wizards-and-kits.png");
        // v2.84.0: the Agent Port dialog — the action redacts its per-start
        // token under nmox.shots.dir, so a shot never carries a secret
        DIALOG_SHOTS.put("Tools/org.nmox.studio.rack.mcp.AgentPortAction",
                "agent-port.png");
    }

    /**
     * Walk-only dialog shots (v2.141.0): {@code -J-Dnmox.shots.dialogs=Category/id=file.png[#tab=N],...}
     * appends dialogs to the queue for ONE run without entering the docs map
     * above — the platform's own About and Plugin Manager dialogs are
     * photographed this way so a translated build's platform chrome can be
     * READ, and {@code #tab=N} selects a tab of the dialog's first tabbed pane
     * before the settle (the Plugin Manager opens on Updates; Installed is 3).
     * Pure, pinned by DocsShotsTest; malformed entries are skipped.
     */
    static Map<String, String> walkDialogs(String spec) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec == null || spec.isBlank()) {
            return out;
        }
        for (String entry : spec.split(",")) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1 || !entry.substring(0, eq).contains("/")) {
                continue;
            }
            out.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        return out;
    }

    /** {@code file.png#tab=3} → 3; no suffix → -1 (leave the dialog's own tab). */
    static int tabIndex(String value) {
        int hash = value.indexOf("#tab=");
        if (hash < 0) {
            return -1;
        }
        try {
            String rest = value.substring(hash + 5);
            int next = rest.indexOf('#');
            return Integer.parseInt((next < 0 ? rest : rest.substring(0, next)).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** {@code file.png#tab=3#find=NMOX} → {@code NMOX}; absent → null. */
    static String findText(String value) {
        int hash = value.indexOf("#find=");
        if (hash < 0) {
            return null;
        }
        String rest = value.substring(hash + 6);
        int next = rest.indexOf('#');
        return next < 0 ? rest : rest.substring(0, next);
    }

    /** {@code file.png#tab=3#find=NMOX} → {@code file.png}. */
    static String shotFile(String value) {
        int hash = value.indexOf('#');
        return hash < 0 ? value : value.substring(0, hash);
    }

    /** ms after selecting a tab before painting — lets componentShowing-deferred work land. */
    static final int SETTLE_MS = 2_500;
    /** ms after UI-ready before the first selection — lets the default-open set finish. */
    static final int WARMUP_MS = 5_000;
    /** A dialog that hasn't appeared by now was never coming — skip, never stall. */
    static final int DIALOG_TIMEOUT_MS = 20_000;

    @Override
    public void run() {
        String dir = System.getProperty("nmox.shots.dir");
        if (dir == null || dir.isBlank()) {
            return; // the normal boot: one property read, nothing else
        }
        File out = new File(dir);
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            javax.swing.Timer warmup = new javax.swing.Timer(WARMUP_MS,
                    e -> new Session(out).next());
            warmup.setRepeats(false);
            warmup.start();
        });
    }

    /** One capture run: walks SHOTS in order on the EDT, then exits the app. */
    static final class Session {

        private final File dir;
        private final java.util.Iterator<Map.Entry<String, String>> queue =
                SHOTS.entrySet().iterator();

        Session(File dir) {
            this.dir = dir;
            if (!dir.isDirectory() && !dir.mkdirs()) {
                // no output dir → no run: exit honestly instead of failing
                // every capture with a confusing per-file IO error
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("cannot create shots dir " + dir);
                org.openide.LifecycleManager.getDefault().exit();
            }
            // a consistent window size so every release's shots line up
            Frame main = WindowManager.getDefault().getMainWindow();
            main.setSize(1600, 1000);
            main.validate();
            seedFakeRun();
        }

        /**
         * The Workbench's RUNNING section paints only while something runs
         * (v2.75.0): {@code -Dnmox.shots.fakerun=<label>|<url>} registers one
         * run with the ■'s registry and its serving with the ⇄ chip's, so
         * the forge's workbench shot shows the section the guide describes.
         * A fake: the killer is a no-op, nothing is spawned, and the forge
         * exits at the end of its run. Never read on a normal boot.
         */
        static void seedFakeRun() {
            String spec = System.getProperty("nmox.shots.fakerun");
            if (spec == null || spec.isBlank()) {
                return;
            }
            String[] parts = fakeRunParts(spec);
            String id = "docs-shot:" + parts[0];
            org.nmox.studio.core.spi.LiveRuns.add(new org.nmox.studio.core.spi.LiveRuns.Run(id, parts[0], () -> { }));
            if (parts[1] != null) {
                org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                        new org.nmox.studio.rack.service.ServingRegistry.Serving(id, parts[0], parts[1],
                                org.nmox.studio.rack.service.ServingRegistry.Kind.WEB, new File(System.getProperty("user.home"))));
            }
        }

        /** "label|url" → {label, url}; "label" → {label, null}. Pure, pinned. */
        static String[] fakeRunParts(String spec) {
            int bar = spec.indexOf('|');
            return bar < 0 ? new String[] {spec.trim(), null}
                    : new String[] {spec.substring(0, bar).trim(), spec.substring(bar + 1).trim()};
        }

        void next() {
            if (!queue.hasNext()) {
                nextDialog();
                return;
            }
            Map.Entry<String, String> shot = queue.next();
            TopComponent tc = WindowManager.getDefault().findTopComponent(shot.getKey());
            if (tc == null) {
                next(); // module not installed here — skip, never stall the run
                return;
            }
            if (!tc.isOpened()) {
                tc.open(); // Block Studio is not in the default-open set
            }
            tc.requestActive();
            javax.swing.Timer settle = new javax.swing.Timer(SETTLE_MS, e -> {
                capture(shot.getValue());
                next();
            });
            settle.setRepeats(false);
            settle.start();
        }

        private void capture(String filename) {
            captureComponent(WindowManager.getDefault().getMainWindow(), filename);
        }

        private void captureComponent(java.awt.Component window, String filename) {
            try {
                int w = window.getWidth(), h = window.getHeight();
                if (w <= 0 || h <= 0) {
                    return; // never NPE the run on a hidden window
                }
                // 2x supersample: crisp text in the rendered docs
                BufferedImage img = new BufferedImage(w * 2, h * 2,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(2, 2);
                window.paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(dir, filename));
            } catch (Exception ex) {
                // a failed shot must never wedge the run — the script's
                // missing-file check reports it honestly
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("shot " + filename + " failed: " + ex);
            }
        }

        // --- forge v2: dialog shots -----------------------------------------

        private final java.util.Iterator<Map.Entry<String, String>> dialogQueue = allDialogs();

        private static java.util.Iterator<Map.Entry<String, String>> allDialogs() {
            Map<String, String> all = new LinkedHashMap<>(DIALOG_SHOTS);
            all.putAll(walkDialogs(System.getProperty("nmox.shots.dialogs")));
            return all.entrySet().iterator();
        }

        void nextDialog() {
            if (!dialogQueue.hasNext()) {
                org.openide.LifecycleManager.getDefault().exit();
                return;
            }
            Map.Entry<String, String> shot = dialogQueue.next();
            String[] parts = shot.getKey().split("/", 2);
            javax.swing.Action action = org.openide.awt.Actions.forID(parts[0], parts[1]);
            if (action == null) {
                nextDialog(); // action not installed here — skip, never stall
                return;
            }
            // invokeLater: a modal show blocks this actionPerformed, but the
            // modal pump keeps dispatching — our timers below still fire
            java.awt.EventQueue.invokeLater(() -> action.actionPerformed(
                    new java.awt.event.ActionEvent(
                            WindowManager.getDefault().getMainWindow(),
                            java.awt.event.ActionEvent.ACTION_PERFORMED, "docs-shot")));
            awaitDialog(shot.getValue(),
                    System.currentTimeMillis() + DIALOG_TIMEOUT_MS);
        }

        private void awaitDialog(String filename, long deadline) {
            javax.swing.Timer poll = new javax.swing.Timer(250, null);
            poll.addActionListener(e -> {
                java.awt.Dialog dialog = visibleDialog();
                if (dialog != null) {
                    poll.stop();
                    int tab = tabIndex(filename);
                    if (tab >= 0) {
                        javax.swing.JTabbedPane pane = firstTabbedPane(dialog);
                        if (pane != null && tab < pane.getTabCount()) {
                            pane.setSelectedIndex(tab);
                        }
                    }
                    javax.swing.Timer settle = new javax.swing.Timer(SETTLE_MS, e2 -> {
                        // #find=<text>: select the first table row whose cells
                        // contain the text (the Plugin Manager fills its table
                        // asynchronously, so this runs after the settle) and
                        // give the detail pane one more settle before painting
                        String find = findText(filename);
                        if (find != null && selectRow(dialog, find)) {
                            javax.swing.Timer again = new javax.swing.Timer(SETTLE_MS, e3 -> paintAndClose(dialog, filename));
                            again.setRepeats(false);
                            again.start();
                            return;
                        }
                        paintAndClose(dialog, filename);
                    });
                    settle.setRepeats(false);
                    settle.start();
                } else if (System.currentTimeMillis() > deadline) {
                    poll.stop();
                    java.util.logging.Logger.getLogger(DocsShots.class.getName())
                            .warning("dialog for " + filename + " never appeared — skipped");
                    nextDialog();
                }
            });
            poll.start();
        }

        private static boolean selectRow(java.awt.Container c, String text) {
            for (java.awt.Component child : c.getComponents()) {
                // the Plugin Manager holds one table per tab; only the
                // showing one is the reader's, the rest are behind tabs
                if (child instanceof javax.swing.JTable t && t.isShowing()) {
                    for (int r = 0; r < t.getRowCount(); r++) {
                        for (int col = 0; col < t.getColumnCount(); col++) {
                            Object v = t.getValueAt(r, col);
                            // the Plugin Manager's cells are model objects whose
                            // toString is a class name; read what the renderer paints
                            java.awt.Component painted = t.getCellRenderer(r, col)
                                    .getTableCellRendererComponent(t, v, false, false, r, col);
                            String shown = painted instanceof javax.swing.JLabel lbl
                                    ? lbl.getText() : String.valueOf(v);
                            if (shown != null && shown.contains(text)) {
                                t.setRowSelectionInterval(r, r);
                                t.scrollRectToVisible(t.getCellRect(r, 0, true));
                                return true;
                            }
                        }
                    }
                }
                if (child instanceof java.awt.Container cc && selectRow(cc, text)) {
                    return true;
                }
            }
            return false;
        }

        private void paintAndClose(java.awt.Dialog dialog, String filename) {
            // paint the root pane, not the window: the native title-bar
            // region isn't Swing-painted and would land as a black band
            // across every dialog shot
            java.awt.Component subject =
                    dialog instanceof javax.swing.RootPaneContainer rpc
                            ? rpc.getRootPane() : dialog;
            captureComponent(subject, shotFile(filename));
            closeAllDialogs(); // unblocks the modal actionPerformed
            nextDialog();
        }

        private static javax.swing.JTabbedPane firstTabbedPane(java.awt.Container c) {
            for (java.awt.Component child : c.getComponents()) {
                if (child instanceof javax.swing.JTabbedPane p) {
                    return p;
                }
                if (child instanceof java.awt.Container cc) {
                    javax.swing.JTabbedPane found = firstTabbedPane(cc);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private static java.awt.Dialog visibleDialog() {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof java.awt.Dialog d && d.isShowing()
                        && d.getWidth() > 0 && d.getHeight() > 0) {
                    return d;
                }
            }
            return null;
        }

        private static void closeAllDialogs() {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof java.awt.Dialog d && d.isShowing()) {
                    d.dispose();
                }
            }
        }
    }
}
