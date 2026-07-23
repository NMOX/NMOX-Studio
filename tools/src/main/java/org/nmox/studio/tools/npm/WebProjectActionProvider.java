package org.nmox.studio.tools.npm;

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

    private final WebProject project;

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

        // the action and the rack are one mechanism: aim the rack so the
        // monitor, explorer and recent list all follow the same project.
        // Soft aim lookup (ledger 30): no provider (plain tests) — the
        // command still runs and shows output.
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            aim.aim(dir);
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

        CommandExecutor.Handle handle = CommandExecutor.run(
                label, dir, Map.of(), cmd, line -> {
                }, exit -> ph.finish());
        proc.set(handle);
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

    private static String labelFor(String command) {
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
