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
 *
 * <p>v2.162.0: with {@code -Dnmox.shots.staged=1} the run also STAGES the
 * user guide's six hand-photographed states (a racked project front and
 * rear, an open file, an experiment with its walkthrough, KVASIR's
 * diagnosis, the learning-space shelf) through
 * {@code rack.service.DocsStaging}, so a translated guide is illustrated
 * in its own language there too — the fixtures live under the forge's
 * throwaway {@code user.home}.
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
        // v2.185.0: the Browser's EMPTY state. The claim that it shows a page
        // of ours rather than fetching a news site was provable only by the
        // ABSENCE of a network call — and an absence is equally consistent
        // with a blank pane. A painted window is the positive evidence.
        SHOTS.put("WebBrowserTopComponent", "browser-to-source.png");
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
        String census = System.getProperty("nmox.popup.census");
        if (census != null && !census.isBlank()) {
            PopupCensus.arm(new File(census));
            return;
        }
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
            if (staged()) {
                try {
                    // the shelf the spaces-shelf dialog shot lists: six catalogue
                    // spaces with a lived-in spread of ages, named in the running
                    // locale's own words
                    java.util.Map<String, Integer> shelf = new LinkedHashMap<>();
                    shelf.put("first-web-page", 0);
                    shelf.put("angular", 23);
                    shelf.put("elm", 41);
                    shelf.put("nim", 41);
                    shelf.put("gleam", 45);
                    shelf.put("lisp-clisp", 34);
                    org.nmox.studio.rack.service.DocsStaging.seedLearningSpaces(
                            new File(System.getProperty("user.home")), shelf);
                } catch (java.io.IOException ex) {
                    java.util.logging.Logger.getLogger(DocsShots.class.getName())
                            .warning("learning-space shelf not seeded: " + ex);
                }
            }
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
            fakeRun(parts[0], parts[1]);
        }

        /** The fake runs this session registered, so a staged shot can swap them. */
        private static final java.util.Set<String> FAKE_RUNS = new java.util.LinkedHashSet<>();

        /** Registers one fake run (+ its serving when a url is given); the killer is a no-op. */
        static String fakeRun(String label, String url) {
            String id = "docs-shot:" + label;
            org.nmox.studio.core.spi.LiveRuns.add(new org.nmox.studio.core.spi.LiveRuns.Run(id, label, () -> { }));
            if (url != null) {
                org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                        new org.nmox.studio.rack.service.ServingRegistry.Serving(id, label, url,
                                org.nmox.studio.rack.service.ServingRegistry.Kind.WEB, new File(System.getProperty("user.home"))));
            }
            FAKE_RUNS.add(id);
            return id;
        }

        /** Withdraws every fake run and serving this session registered. */
        static void clearFakeRuns() {
            for (String id : FAKE_RUNS) {
                org.nmox.studio.core.spi.LiveRuns.remove(id);
                org.nmox.studio.rack.service.ServingRegistry.getDefault().deregister(id);
            }
            FAKE_RUNS.clear();
        }

        /** "label|url" → {label, url}; "label" → {label, null}. Pure, pinned. */
        static String[] fakeRunParts(String spec) {
            int bar = spec.indexOf('|');
            return bar < 0 ? new String[] {spec.trim(), null}
                    : new String[] {spec.substring(0, bar).trim(), spec.substring(bar + 1).trim()};
        }

        void next() {
            if (!queue.hasNext()) {
                nextStaged();
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

        // --- v2.162.0: staged shots -----------------------------------------

        /**
         * A staged shot: {@code arrange} puts the app into the state the
         * guide describes (on the EDT), {@code ready} is polled every 250 ms
         * up to {@code maxWaitMs}, then the usual settle and paint. A stage
         * that throws is skipped with a warning — never a stalled run.
         */
        record Staged(String file, Runnable arrange,
                java.util.function.BooleanSupplier ready, int maxWaitMs, boolean required) {

            Staged(String file, Runnable arrange, java.util.function.BooleanSupplier ready, int maxWaitMs) {
                this(file, arrange, ready, maxWaitMs, false);
            }
        }

        /**
         * v2.164.0: a scene whose picture only means something once its
         * real service answered — a daemon, a chain, a debuggee, a page.
         * It is staged, arranged and waited for through its own
         * {@link org.nmox.studio.core.spi.DocsScene}, and a scene that
         * never became ready is SKIPPED with a warning: an empty Docker
         * table or a debugger that never paused is not the picture.
         */
        private static Staged sceneShot(String id, File home, boolean aim, int maxWaitMs) {
            return new Staged(id + ".png", () -> {
                File dir = stageScene(id, home);
                if (aim && dir != null) {
                    org.nmox.studio.rack.service.DocsStaging.aim(dir);
                }
                arrangeScene(id);
            }, () -> sceneReady(id), maxWaitMs, true);
        }

        private static boolean sceneReady(String id) {
            org.nmox.studio.core.spi.DocsScene target = scene(id);
            try {
                return target != null && target.ready();
            } catch (RuntimeException | LinkageError ex) {
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("scene " + id + " could not report readiness: " + ex);
                return false;
            }
        }

        /** {@code -Dnmox.shots.staged=<anything>} turns the staged phase on for one run. */
        static boolean staged() {
            String s = System.getProperty("nmox.shots.staged");
            return s != null && !s.isBlank();
        }

        /** KVASIR faceplate texts that mean the consult never happened — no shot then. */
        static final java.util.List<String> KVASIR_REFUSALS = java.util.List.of(
                "NO API KEY", "NEEDS CONSENT", "NEEDS YOUR OK", "NOTHING TO EXPLAIN",
                "PRESS EXPLAIN", "CONSULTING", "OFFLINE", "COOLING DOWN");

        /** True when the KVASIR LCD holds a diagnosis rather than a refusal or its idle hint. */
        static boolean kvasirAnswered(String lcds) {
            if (lcds == null || lcds.isBlank()) {
                return false;
            }
            for (String refusal : KVASIR_REFUSALS) {
                if (lcds.contains(refusal)) {
                    return false;
                }
            }
            return true;
        }

        private final java.util.Iterator<Staged> stagedQueue = stagedShots().iterator();
        private boolean thinkSeen;
        private long explainPressedAt;
        private long firstStageAt;

        /**
         * The first staged picture follows a project aim, which starts the
         * boot-time indexing (the spellchecker's "Building dictionary" bar):
         * hold it long enough that the status line is quiet in the picture.
         */
        static final int FIRST_STAGE_HOLD_MS = 8_000;

        /**
         * The six states the user guide's hand-staged shots show, in the
         * order the guide meets them. The fixtures live under the forge's
         * (throwaway) user.home: a Classic Web site racked with the Classic
         * Web Bench preset for the rack front, rear and editor shots; an
         * Express experiment with its walkthrough for the experiments shot
         * and, with VERITAS → KVASIR → MONITOR racked and a failed run fed
         * to the recorder, for KVASIR's real diagnosis — which arrives in
         * the running locale's language (v2.162.0) from the real API, so
         * the run needs a key in the environment and consent seeded in the
         * userdir (scripts/docs-shots.sh does both).
         */
        private java.util.List<Staged> stagedShots() {
            if (!staged()) {
                return java.util.List.of();
            }
            File home = new File(System.getProperty("user.home"));
            File[] classic = new File[1];
            File[] experiment = new File[1];
            return java.util.List.of(
                    new Staged("task-rack.png", () -> {
                        clearFakeRuns();
                        classic[0] = io(() -> org.nmox.studio.rack.service.DocsStaging.classicSite(home));
                        org.nmox.studio.rack.service.DocsStaging.aim(classic[0]);
                        fakeRun("Run \u2014 " + org.nmox.studio.rack.service.DocsStaging.CLASSIC_NAME, "http://localhost:8080/");
                        front("RackTopComponent");
                        firstStageAt = System.currentTimeMillis();
                    }, () -> System.currentTimeMillis() - firstStageAt > FIRST_STAGE_HOLD_MS, FIRST_STAGE_HOLD_MS + 1_000),
                    new Staged("rack-rear.png",
                            org.nmox.studio.rack.service.DocsStaging::flipRack, () -> true, 0),
                    new Staged("editor.png", () -> {
                        org.nmox.studio.rack.service.DocsStaging.flipRack(); // back to the front
                        org.nmox.studio.rack.service.DocsStaging.openFile(new File(classic[0], "js/app.js"));
                    }, () -> true, 0),
                    new Staged("experiment-walkthrough.png", () -> {
                        clearFakeRuns();
                        experiment[0] = io(() -> org.nmox.studio.rack.service.DocsStaging.expressExperiment(home));
                        org.nmox.studio.rack.service.DocsStaging.aim(experiment[0]);
                        fakeRun("Run \u2014 " + org.nmox.studio.rack.service.DocsStaging.EXPERIMENT_NAME, "http://localhost:3000/");
                        show("ProjectExplorerTopComponent"); // the Workbench beside the walkthrough
                        org.nmox.studio.rack.service.DocsStaging.openFile(
                                new File(experiment[0], org.nmox.studio.rack.projectstudio.Experiments.GUIDE));
                    }, () -> true, 0),
                    new Staged("kvasir-explain.png", () -> {
                        java.util.logging.Logger.getLogger(DocsShots.class.getName()).info(
                                "kvasir stage begins on the rack: " + org.nmox.studio.rack.service.DocsStaging.rackSummary());
                        String fixed = org.nmox.studio.rack.service.DocsStaging.ensureRackOn(experiment[0]);
                        if (!fixed.isEmpty()) {
                            java.util.logging.Logger.getLogger(DocsShots.class.getName()).info(
                                    "kvasir stage " + fixed + " \u2014 now: " + org.nmox.studio.rack.service.DocsStaging.rackSummary());
                        }
                        front("RackTopComponent");
                        // node:test's own shape for one failing test in the Express API's suite
                        org.nmox.studio.rack.service.DocsStaging.seedFailedRun(
                                org.nmox.studio.rack.service.DocsStaging.FAILING_DEVICE, "npm test",
                                java.util.List.of(
                                        "\u2716 GET /health answers ok (14.2ms)",
                                        "  AssertionError [ERR_ASSERTION]: Expected values to be strictly equal:",
                                        "  'ok' !== 'okay'",
                                        "\u2139 fail 1"), 1);
                        thinkSeen = false;
                        explainPressedAt = 0;
                    }, () -> {
                        // press only once KVASIR shows a failure is ready to explain:
                        // the patch loads and the recorder fills on their own time
                        if (explainPressedAt == 0 && !org.nmox.studio.rack.service.DocsStaging.kvasirRacked()) {
                            String fixed = org.nmox.studio.rack.service.DocsStaging.ensureRackOn(experiment[0]);
                            if (!fixed.isEmpty()) {
                                java.util.logging.Logger.getLogger(DocsShots.class.getName()).info(
                                        "kvasir stage " + fixed + " \u2014 now: " + org.nmox.studio.rack.service.DocsStaging.rackSummary());
                            }
                            return false;
                        }
                        String lcds = org.nmox.studio.rack.service.DocsStaging.kvasirLcds();
                        if (explainPressedAt == 0) {
                            if (lcds.contains("PRESS EXPLAIN")
                                    && org.nmox.studio.rack.service.DocsStaging.pressKvasirExplain()) {
                                explainPressedAt = System.currentTimeMillis();
                            }
                            return false;
                        }
                        boolean thinking = org.nmox.studio.rack.service.DocsStaging.kvasirThinking();
                        if (thinking) {
                            thinkSeen = true;
                        }
                        // done when the consult has come and gone — or, for a refusal
                        // too quick for a 250 ms poll to see the LED, when the idle
                        // hint has been replaced
                        return !thinking && (thinkSeen || (!lcds.contains("PRESS EXPLAIN")
                                && System.currentTimeMillis() - explainPressedAt > 1_500));
                    }, 120_000),
                    // v2.163.0: the scenes whose windows only mean something
                    // with data in them. Each module stages its own fixture
                    // through its own writer (core.spi.DocsScene) because the
                    // forge can reach neither the board's package-private IO
                    // nor three modules the ui module does not depend on.
                    new Staged("task-board.png", () -> {
                        clearFakeRuns();
                        // every scene writes into the SAME demo project, so one
                        // aim serves them all and the reader meets one shop
                        // rather than four unrelated fixtures
                        File demo = null;
                        for (String id : java.util.List.of(
                                org.nmox.studio.ui.tasks.DocsTaskBoard.ID,
                                "infra-designer", "db-studio", "api-studio")) {
                            File staged = stageScene(id, home);
                            if (staged != null) {
                                demo = staged;
                            }
                        }
                        if (demo != null) {
                            org.nmox.studio.rack.service.DocsStaging.aim(demo);
                        }
                        front("TasksTopComponent");
                    }, () -> true, 0),
                    new Staged("sprint-overview.png", () -> {
                        org.nmox.studio.ui.tasks.TasksTopComponent board = tasks();
                        if (board != null) {
                            board.docsShowOverview();
                        }
                    }, () -> true, 0),
                    new Staged("standup.png", () -> {
                        org.nmox.studio.ui.tasks.TasksTopComponent board = tasks();
                        if (board != null) {
                            // the report is MODAL: invokeLater so the show
                            // blocks that lambda and not this queue — a modal
                            // pump keeps our timers firing (the v2.141.0 law)
                            java.awt.EventQueue.invokeLater(board::docsShowStandup);
                        }
                    }, () -> visibleDialog() != null, 20_000),
                    new Staged("infra-designer.png", () -> {
                        closeAllDialogs(); // the standup report has been painted
                        front("InfraDesignerTopComponent");
                    }, () -> true, 0),
                    new Staged("db-studio.png", () -> {
                        front("DbStudioTopComponent");
                        arrangeScene("db-studio"); // connect, fill the console, run
                    }, () -> false, 8_000),
                    new Staged("api-studio.png", () -> {
                        front("ApiClientTopComponent");
                        arrangeScene("api-studio"); // select the starter request and send
                    }, () -> false, 8_000),
                    new Staged("presentation-mode.png", () -> {
                        org.nmox.studio.rack.service.DocsStaging.openFile(
                                new File(classic[0], "js/app.js"));
                        presentationMode();
                    }, () -> true, 0),
                    new Staged("editor-screenshot-2x.png", () -> {
                        // the previous picture left Presentation Mode ON; this
                        // one shows the editor at its ordinary size, so press
                        // the same toggle again
                        presentationMode();
                    }, () -> true, 0),
                    // v2.164.0: the tutorials' four live scenes. The debugger
                    // goes LAST: a paused session keeps its views and toolbar
                    // for the rest of the run
                    sceneShot("docker-panel", home, false, 60_000),
                    sceneShot("contract-studio", home, true, 90_000),
                    sceneShot("story-06-devtools-pick", home, false, 60_000),
                    sceneShot("debug-javascript", home, true, 90_000));
        }

        /**
         * The component a staged picture paints. Most are the whole main
         * window; two are not. The Standup is a modal dialog, and a dialog
         * is painted by its ROOT PANE because a native title bar is not
         * Swing-painted (the v1.125.0 law). The editor screenshot is the
         * editor tab alone — the same tab Save Editor Screenshot saves.
         */
        private static java.awt.Component targetFor(String file) {
            if ("standup.png".equals(file)) {
                java.awt.Dialog dialog = visibleDialog();
                if (dialog instanceof javax.swing.JDialog jd) {
                    return jd.getRootPane();
                }
                if (dialog != null) {
                    return dialog;
                }
            }
            if ("editor-screenshot-2x.png".equals(file)) {
                org.openide.windows.Mode editor = WindowManager.getDefault().findMode("editor");
                TopComponent tab = editor == null ? null : editor.getSelectedTopComponent();
                if (tab != null) {
                    return tab;
                }
            }
            return WindowManager.getDefault().getMainWindow();
        }

        /** Presses View ▸ Presentation Mode, which is a toggle. */
        private static void presentationMode() {
            javax.swing.Action present = org.openide.awt.Actions.forID(
                    "View", "org.nmox.studio.editor.present.PresentationModeAction");
            if (present != null) {
                present.actionPerformed(new java.awt.event.ActionEvent(
                        WindowManager.getDefault().getMainWindow(),
                        java.awt.event.ActionEvent.ACTION_PERFORMED, "docs-shot"));
            }
        }

        /**
         * This run's scene content, or empty when the forge was given none —
         * in which case the scenes are skipped rather than staged in English,
         * so a missing fixtures file loses those pictures loudly (the script
         * checks for them) instead of quietly painting the wrong language.
         */
        private static String fixtures() {
            String path = System.getProperty("nmox.shots.fixtures");
            if (path == null || path.isBlank()) {
                return "";
            }
            try {
                return java.nio.file.Files.readString(new File(path).toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException unreadable) {
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("fixtures unreadable at " + path + ": " + unreadable);
                return "";
            }
        }

        /** The language being painted, as the launcher's --locale named it. */
        private static String lang() {
            return System.getProperty("nmox.shots.lang", "");
        }

        private static org.nmox.studio.core.spi.DocsScene scene(String id) {
            for (org.nmox.studio.core.spi.DocsScene candidate : org.nmox.studio.core.spi.DocsScene.all()) {
                if (candidate.id().equals(id)) {
                    return candidate;
                }
            }
            return null;
        }

        /** Writes one scene's fixtures; returns the directory to aim at, or null. */
        private static File stageScene(String id, File home) {
            org.nmox.studio.core.spi.DocsScene target = scene(id);
            String content = fixtures();
            if (target == null || content.isEmpty()) {
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("scene " + id + (target == null ? " is not registered" : " has no fixtures")
                                + " — its picture will be missing");
                return null;
            }
            try {
                return target.stage(home, content, lang());
            } catch (java.io.IOException | RuntimeException | LinkageError | java.util.ServiceConfigurationError ex) {
                // an Error here once stopped the whole forge silently (a cross-loader
                // org.json type): skip the scene, say so, keep painting
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .log(java.util.logging.Level.WARNING, "scene " + id + " could not be staged", ex);
                return null;
            }
        }

        /** Puts a scene's window into the state its picture shows — EDT. */
        private static void arrangeScene(String id) {
            org.nmox.studio.core.spi.DocsScene target = scene(id);
            if (target == null) {
                return;
            }
            try {
                target.arrange();
            } catch (RuntimeException | LinkageError ex) {
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("scene " + id + " could not be arranged: " + ex);
            }
        }

        private static org.nmox.studio.ui.tasks.TasksTopComponent tasks() {
            TopComponent tc = WindowManager.getDefault().findTopComponent("TasksTopComponent");
            return tc instanceof org.nmox.studio.ui.tasks.TasksTopComponent board ? board : null;
        }

        private interface IoCall<T> {
            T call() throws java.io.IOException;
        }

        private static <T> T io(IoCall<T> call) {
            try {
                return call.call();
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }

        private static void front(String preferredId) {
            TopComponent tc = WindowManager.getDefault().findTopComponent(preferredId);
            if (tc != null) {
                if (!tc.isOpened()) {
                    tc.open();
                }
                tc.requestActive();
            }
        }

        private static void show(String preferredId) {
            TopComponent tc = WindowManager.getDefault().findTopComponent(preferredId);
            if (tc != null) {
                if (!tc.isOpened()) {
                    tc.open();
                }
                tc.requestVisible();
            }
        }

        void nextStaged() {
            if (!stagedQueue.hasNext()) {
                nextDialog();
                return;
            }
            Staged stage = stagedQueue.next();
            try {
                stage.arrange().run();
            } catch (RuntimeException | LinkageError ex) {
                java.util.logging.Logger.getLogger(DocsShots.class.getName())
                        .warning("staged shot " + stage.file() + " could not be arranged \u2014 skipped: " + ex);
                nextStaged();
                return;
            }
            long deadline = System.currentTimeMillis() + stage.maxWaitMs();
            javax.swing.Timer poll = new javax.swing.Timer(250, null);
            poll.addActionListener(e -> {
                if (!stage.ready().getAsBoolean() && System.currentTimeMillis() <= deadline) {
                    return;
                }
                poll.stop();
                javax.swing.Timer settle = new javax.swing.Timer(SETTLE_MS, e2 -> {
                    String lcds = "kvasir-explain.png".equals(stage.file())
                            ? org.nmox.studio.rack.service.DocsStaging.kvasirLcds() : null;
                    if (stage.required() && !stage.ready().getAsBoolean()) {
                        java.util.logging.Logger.getLogger(DocsShots.class.getName())
                                .warning(stage.file() + " skipped \u2014 its scene never became ready within "
                                        + stage.maxWaitMs() + " ms");
                    } else if (lcds != null && !kvasirAnswered(lcds)) {
                        java.util.logging.Logger.getLogger(DocsShots.class.getName())
                                .warning("kvasir-explain.png skipped \u2014 KVASIR did not answer; its faceplate reads: "
                                        + lcds.replace('\n', ' ').strip() + " \u2014 the rack: "
                                        + org.nmox.studio.rack.service.DocsStaging.rackSummary()
                                        + " \u2014 EXPLAIN pressed: " + (explainPressedAt != 0));
                    } else {
                        captureComponent(targetFor(stage.file()), stage.file());
                    }
                    nextStaged();
                });
                settle.setRepeats(false);
                settle.start();
            });
            poll.start();
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
