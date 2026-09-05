package org.nmox.studio.tools.npm;

import org.nmox.studio.core.spi.LiveRuns;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.spi.project.ActionProvider;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Makes the platform's native Run / Build / Test / Clean actions
 * (toolbar, F6/F11, project menu) drive a web project the way its
 * toolchain expects. Each run aims the rack at the project, streams to
 * the same output bus the rack devices use, and shows in the status-bar
 * progress widget with a Cancel that actually kills the process — so the
 * IDE-native gestures and the rack are two faces of one mechanism, not
 * two parallel worlds.
 */
final class WebProjectActionProvider implements ActionProvider {

    private static final String[] SUPPORTED = {
        COMMAND_RUN, COMMAND_BUILD, COMMAND_TEST, COMMAND_CLEAN
    };

    /** Distinguishes concurrent Runs of one project in the serving registry. */
    private static final java.util.concurrent.atomic.AtomicLong RUN_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private final WebProject project;

    /**
     * Drops ANSI escapes before the URL scan. The old strip removed only
     * {@code [36m}-style tails and left the ESC byte itself, which the
     * URL pattern's permissive char class then swallowed INTO the
     * captured URL (v1.216.0) — a colored banner produced a serving URL
     * with a trailing escape byte. Full CSI sequences plus any stray ESC.
     */
    /**
     * The serving URL a dev-server output line announces, or null.
     * Extracted pure (v1.216.0) so every branch is plainly testable:
     * python -m http.server (the STATIC lane) prints "Serving HTTP on
     * 0.0.0.0 port 8000" — a banner with no localhost URL in it, so it
     * maps to the pinned localhost URL (the same announce IGNITION's
     * static lane makes; the command carries -u for the same reason
     * RunDevice's does — piped python block-buffers the banner without
     * it, the v1.37.0 lesson). Everything else rides the shared scan.
     */
    static String servingUrlFor(String line) {
        String plain = stripAnsi(line);
        // the STATIC lane's port is probed, not pinned (v1.320.0), so the
        // URL comes from the port the banner NAMES — announcing the old
        // constant while python bound the next port up would register a
        // serving nothing listens on (the v1.93.0 class)
        return plain.contains("Serving HTTP")
                ? "http://localhost:" + org.nmox.studio.rack.devices.ServeUrls
                        .bannerPort(plain, WebProjectCommands.STATIC_PORT) + "/"
                : org.nmox.studio.rack.devices.ServeUrls.firstLocalUrl(plain);
    }

    static String stripAnsi(String line) {
        return line.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                .replace("\u001B", "")
                .replaceAll("\\[[;\\d]*m", "");
    }

    WebProjectActionProvider(WebProject project) {
        this.project = project;
    }

    @Override
    public String[] getSupportedActions() {
        return SUPPORTED.clone();
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) {
        return resolve(command) != null;
    }

    @Override
    public void invokeAction(String command, Lookup context) {
        File dir = FileUtil.toFile(project.getProjectDirectory());
        List<String> cmd = resolve(command);
        if (dir == null || cmd == null) {
            return;
        }
        // Run/Build/Test/Clean execute PROJECT-controlled code — the
        // package.json "scripts" body, make/cargo(build.rs)/gradle build
        // scripts, npx-resolved node_modules/.bin binaries — all
        // attacker-controlled in a cloned repo. CommandExecutor.run and
        // ProcessSupport are deliberately un-gated primitives; the trust
        // gate is the caller's job (as the rack devices and debug actions
        // do it). Ask before running a stranger's tasks. requestTrust
        // prompts once then caches; headless it auto-allows.
        if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(dir)) {
            return;
        }
        // a Run while the project's own install is still live (v2.72.0):
        // refuse out loud instead of failing on a half-written node_modules
        if (InstallGuard.installing(dir)) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(InstallGuard.message(dir));
            return;
        }
        // The platform invokes us on the EDT; the fork (pb.start inside
        // CommandExecutor.run) and everything around it ride a named lane
        // (v2.70.0 — the v1.57.0 class: the rack's RUN buttons left the
        // paint thread then, the IDE's own ▶ never did). The trust dialog
        // above stays on the EDT by design; SpawnThreadGateTest pins the
        // order: gate, then post, then spawn.
        RUN_RP.post(() -> launch(command, context, dir, cmd));
    }

    /** A named lane for the IDE's own forks; four so a hung spawn can't wedge the next. */
    private static final org.openide.util.RequestProcessor RUN_RP =
            new org.openide.util.RequestProcessor("IDE Run", 4, true);

    private void launch(String command, Lookup context, File dir, List<String> cmd) {
        // the third wall (v2.73.0): a Node Run with declared, uninstalled
        // dependencies is refused out loud and pointed at Install; Build/
        // Test/Clean pass — a Build may be the thing that installs. Here,
        // on the lane, because the check READS package.json (the batch
        // review: its first home was invokeAction, on the EDT)
        if (ActionProvider.COMMAND_RUN.equals(command) && InstallGuard.needsInstall(dir)) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(InstallGuard.needsInstallMessage(dir));
            InstallDoor.offer(dir);
            return;
        }
        // the action and the rack are one mechanism: aim the rack so the
        // monitor, explorer and recent list all follow the same project.
        // Soft aim lookup (ledger 30): no provider (plain tests) — the
        // command still runs and shows output.
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            aim.aim(dir);
        }

        // Run means "show me the thing running". The dev server will
        // announce its URL to the ServingRegistry in a second or two;
        // arm the opener now so the page lands in the in-app Browser
        // instead of being printed to Output for the user to fetch by
        // hand (v1.212.0). Only RUN — nobody wants a browser tab after
        // a Build, a Test or a Clean. Preference-gated inside arm().
        if (ActionProvider.COMMAND_RUN.equals(command)) {
            org.nmox.studio.rack.service.OpenOnServe.getDefault().arm(dir);
        }

        String label = labelFor(command) + " — " + project.getName();
        AtomicReference<CommandExecutor.Handle> proc = new AtomicReference<>();
        ProgressHandle ph = ProgressHandle.createHandle(label, () -> {
            CommandExecutor.Handle h = proc.get();
            if (h != null) {
                h.kill();
            }
            return true;
        });
        ph.start();

        // Until v1.212.0 this consumer was an EMPTY LAMBDA, and that one
        // detail cost the F6 lane everything downstream: the dev server
        // printed "Local: http://localhost:5173", nobody read it, and so
        // no serving was ever registered. No ⇄ chip, nothing in ⌘I Live
        // Servers, no VITALS/BEACON target, no API Studio {{baseUrl}}
        // offer — the URL existed only as text in an Output tab. The rack
        // serve devices had always done this properly; the IDE's own Run
        // button was the one lane that dropped it on the floor.
        //
        // Announcing here is honest by the v1.93.0 serving-truth law: we
        // register only once the server has SAID it is listening, and
        // deregister when the process ends.
        // Per-invocation id (v1.216.0, arc review): keyed on the path
        // alone, a SECOND Run of the same project re-registered the same
        // id (silently replacing a live serving) and whichever run exited
        // first deregistered the OTHER one's live server — a truthful
        // serving erased, the inverse of the v1.93.0 phantom. Two dev
        // servers on two ports are two servings; each run now owns its id.
        String servingId = "ide-run:" + dir.getAbsolutePath()
                + "#" + RUN_SEQ.incrementAndGet();
        // The run is a citizen of two stop surfaces (v2.69.10): our toolbar ■
        // (LiveRuns) and the platform's Run ▸ Stop Build/Run
        // (BuildExecutionSupport). Both kill the process tree; the exit
        // handler withdraws from both.
        IdeRunItem item = new IdeRunItem(command, project.getProjectDirectory(), label,
                proc::get, () -> invokeAction(command, context));
        boolean serves = ActionProvider.COMMAND_RUN.equals(command);
        AtomicReference<String> announced = new AtomicReference<>();
        // registered BEFORE the spawn (v2.71.0): a launch failure fires the
        // exit handler synchronously, and a finished item must already be
        // a registered one — LiveRuns tolerates the inversion with a
        // tombstone, the platform's registry does not
        org.netbeans.spi.project.ui.support.BuildExecutionSupport.registerRunningItem(item);
        CommandExecutor.Handle handle = CommandExecutor.run(
                label, dir, Map.of(), cmd,
                line -> {
                    if (!serves) {
                        return;
                    }
                    String url = servingUrlFor(line);
                    if (url != null && !url.equals(announced.get())) {
                        announced.set(url);
                        org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                                new org.nmox.studio.rack.service.ServingRegistry.Serving(
                                        servingId, "Run — " + project.getName(), url,
                                        org.nmox.studio.rack.service.ServingRegistry.Kind.WEB,
                                        dir));
                    }
                },
                exit -> {
                    ph.finish();
                    item.finished();
                    LiveRuns.remove(servingId);
                    // a launch that never started (exit -1: the tool is not on
                    // PATH — the beginner's commonest wall) speaks on the status
                    // line and names the two places that hold the answer
                    // (v2.73.0); the Output tab carries the friendly reason
                    if (exit == -1) {
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                LaunchFailure.status(label));
                        // and a balloon with the door (v2.73.0): the status
                        // line fades, the bell keeps the link
                        LaunchFailure.notify(label);
                    }
                    org.netbeans.spi.project.ui.support.BuildExecutionSupport.registerFinishedItem(item);
                    // a phantom serving outlives nothing: the gate drops
                    // with the process (the v1.65.1 deregister-on-stop law)
                    if (announced.get() != null) {
                        org.nmox.studio.rack.service.ServingRegistry.getDefault()
                                .deregister(servingId);
                    }
                });
        proc.set(handle);
        LiveRuns.add(new LiveRuns.Run(servingId, label, handle::kill));
        CommandExecutor.showOutput(label);
    }

    /**
     * The kind cache behind {@code isActionEnabled}: the platform calls
     * enablement on the EDT at every menu/toolbar/selection refresh, and
     * {@code ProjectInspector.detectKind} walks the project directory
     * (dozens of listFiles passes across ~35 kinds — a network mount could
     * stall the UI per menu open). A short TTL turns that into one scan per
     * window, with staleness bounded at {@code KIND_TTL_MS} — a project's
     * toolchain does not change between two menu paints.
     */
    static final long KIND_TTL_MS = 3_000;
    private final KindCache kindCache = new KindCache(KIND_TTL_MS,
            ProjectInspector::detectKind);

    /** TTL memo for the detected kind; pure and injectable for tests. */
    static final class KindCache {
        private final long ttlMs;
        private final java.util.function.Function<File, ProjectKind> detector;
        private volatile ProjectKind kind;
        private volatile long at;

        KindCache(long ttlMs, java.util.function.Function<File, ProjectKind> detector) {
            this.ttlMs = ttlMs;
            this.detector = detector;
        }

        ProjectKind get(File dir, long now) {
            ProjectKind k = kind;
            if (k == null || now - at > ttlMs) {
                k = detector.apply(dir);
                kind = k;
                at = now;
            }
            return k;
        }
    }

    private List<String> resolve(String command) {
        File dir = FileUtil.toFile(project.getProjectDirectory());
        if (dir == null) {
            return null;
        }
        ProjectKind kind = kindCache.get(dir, System.currentTimeMillis());
        return WebProjectCommands.commandFor(dir, kind, command);
    }

    /** Progress/Output-window label for the four standard commands. */
    static String labelFor(String command) {
        switch (command) {
            case COMMAND_RUN:
                return "Run";
            case COMMAND_BUILD:
                return "Build";
            case COMMAND_TEST:
                return "Test";
            case COMMAND_CLEAN:
                return "Clean";
            default:
                return command;
        }
    }
}
