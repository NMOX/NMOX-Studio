package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * Base for devices whose job is "run a development tool and report".
 * Provides the standard control cluster (activity meter, RUN/OK/FAIL
 * LEDs, status LCD) and the standard back-panel ports:
 *
 *   IN:  RUN (trigger)
 *   OUT: OK (trigger on success), FAIL (trigger on failure),
 *        DONE (trigger always), OUT (data, one signal per output line)
 */
public abstract class CommandDevice extends RackDevice {

    /** Exit codes from STOP-button kills (SIGINT/SIGKILL/SIGTERM); not real failures. */
    private static final Set<Integer> KILL_EXIT_CODES = Set.of(130, 137, 143);

    protected final VuMeter activity = new VuMeter("ACTIVITY", false);
    protected final Led runLed = new Led("RUN", RackStyle.MUTATE);
    protected final Led okLed = new Led("OK", RackStyle.GO);
    protected final Led failLed = new Led("FAIL", RackStyle.STOP);
    protected final LcdDisplay statusLcd;

    private volatile long lastToastAt;

    protected CommandDevice(String typeId, String title, String tagline, Color accent, int units) {
        super(typeId, title, tagline, accent, units);

        int clusterX = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 158;
        place(activity, clusterX, 40);
        place(runLed, clusterX, 78);
        place(okLed, clusterX + 40, 78);
        place(failLed, clusterX + 80, 78);

        int lcdW = 240;
        statusLcd = new LcdDisplay(lcdW, 1);
        // one name here covers the shared status panel on every command device
        statusLcd.getAccessibleContext().setAccessibleName("status");
        place(statusLcd, clusterX - lcdW - 14, 40);
        statusLcd.setText("READY");

        addInPort("run", "RUN", SignalType.TRIGGER);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);
        addOutPort("done", "DONE", SignalType.TRIGGER);
        addOutPort("out", "OUT", SignalType.DATA);
    }

    /** The command the main RUN action executes (null = nothing to do). */
    protected abstract List<String> buildCommand();

    /**
     * The toolchain AUTO knobs should assume: the rack-wide ROSETTA
     * override when set, else the highest-precedence detected kind.
     */
    protected ProjectInspector.ProjectKind effectiveKind() {
        String override = getRack() != null ? getRack().getToolchainOverride() : null;
        if (override != null) {
            try {
                return ProjectInspector.ProjectKind.valueOf(override);
            } catch (IllegalArgumentException ignored) {
                // unknown override name; fall through to detection
            }
        }
        return ProjectInspector.detectKind(projectDir());
    }

    /**
     * Where this device's commands run: the effective toolchain's
     * manifest directory - in a monorepo, cargo commands run in the
     * Cargo.toml directory, npm commands beside package.json.
     */
    protected java.io.File commandDir() {
        java.io.File ws = workspaceDir();
        if (ws != null && effectiveKind() == ProjectInspector.ProjectKind.NODE) {
            return ws;
        }
        return ProjectInspector.kindDir(projectDir(), effectiveKind());
    }

    /**
     * The WAYPOINT-chosen package directory, or null when the rack
     * steers at the repository root. Only Node lanes re-root: a
     * ROSETTA-dialed cargo lane must keep running at its Cargo.toml.
     */
    protected final java.io.File workspaceDir() {
        var rack = getRack();
        String override = rack == null ? null : rack.getWorkspaceOverride();
        return override == null ? null : new java.io.File(override);
    }

    /**
     * The command the CI exporter should emit; defaults to the primary
     * command. Devices whose RUN reads live UI state that must not leak
     * into a workflow (SPECTER's HEADED toggle — headless CI runners)
     * override this with the CI-safe spelling.
     */
    protected List<String> ciCommand() {
        return buildCommand();
    }

    /** The primary command, exposed for the CI exporter; null = no step. */
    public final List<String> exportCommand() {
        try {
            return ciCommand();
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(CommandDevice.class.getName())
                    .log(java.util.logging.Level.WARNING,
                            "CI export skips " + getTitle() + ": command could not be built", ex);
            return null;
        }
    }

    /** Where the exported step runs, for the CI exporter. */
    public final java.io.File exportDir() {
        try {
            return commandDir();
        } catch (RuntimeException ex) {
            return projectDir();
        }
    }

    /**
     * What the primary button will run, for transparency tooltips:
     * the exact command line and the directory it runs in.
     */
    protected String commandPreview() {
        try {
            List<String> cmd = buildCommand();
            if (cmd == null || cmd.isEmpty()) {
                return null;
            }
            return "<html><code>$ " + String.join(" ", cmd)
                    + "</code><br>in " + commandDir().getAbsolutePath() + "</html>";
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** The action triggered by the RUN input jack; defaults to launching. */
    protected void primaryAction() {
        launch(buildCommand());
    }

    /**
     * Whether a finished run counts as success - the verdict behind the
     * OK/FAIL LEDs and jacks. Exit 0 by default; quality gates override
     * to demand more than "the tool didn't crash" (VITALS closes the
     * gate when scores land under its floor).
     */
    protected boolean overallSuccess(int exitCode) {
        return exitCode == 0;
    }

    @Override
    public void resume() {
        primaryAction();
    }

    /**
     * Whether this device only makes sense inside an npm project. When
     * true (the default), launches are refused unless the project dir
     * has a recognized project manifest (package.json, Cargo.toml,
     * go.mod, pom.xml, pyproject.toml, Gemfile, composer.json,
     * Makefile...) - running tools against the user's home directory
     * is never what anyone wanted.
     */
    protected boolean requiresProjectManifest() {
        return true;
    }

    /**
     * Launches a command with the full standard treatment: LEDs, meter,
     * status LCD, OUT data per line, OK/FAIL/DONE triggers on exit.
     */
    protected boolean launch(List<String> command) {
        return launchWithEnv(command, Map.of());
    }

    /** Test seam for the trust gate (production: the real prompt). */
    static java.util.function.Predicate<java.io.File> trustCheck =
            org.nmox.studio.rack.service.WorkspaceTrust::requestTrust;

    /**
     * {@link #launch} with extra environment for this run only (e.g.
     * MAVEN_OPTS). Returns whether the command was actually handed to
     * the executor — false on every refusal (no command, no manifest,
     * trust declined). Serve devices emit their SERVING gate only on a
     * true return, so a Keep Safe answer can never leave a phantom
     * high gate driving downstream ENABLE cables (the v1.93.0 fix).
     */
    protected boolean launchWithEnv(List<String> command, Map<String, String> extraEnv) {
        if (isDisposed()) {
            // a queued trigger routed after removal must not report
            // launched-for-real: exec() would refuse to spawn, and the
            // true return would let the caller raise a phantom gate in
            // inverted order with exec's synthetic exit (the v1.95.1
            // review's contract finding)
            return false;
        }
        if (command == null || command.isEmpty()) {
            return false;
        }
        if (requiresProjectManifest() && !ProjectInspector.hasProjectManifest(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO PROJECT MANIFEST — USE PROJECT… TO AIM THE RACK");
            });
            return false;
        }
        if (requiresProjectManifest() && !trustCheck.test(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            });
            return false;
        }
        // captured per launch: a relaunch must not skew a still-running
        // command's elapsed-time readout
        final long launchedAt = System.currentTimeMillis();
        onEdt(() -> {
            runLed.setBlinking(true);
            okLed.setOn(false);
            failLed.setOn(false);
            statusLcd.setTextColor(RackStyle.LCD_AMBER);
            statusLcd.setText("RUNNING " + String.join(" ", command));
        });
        exec(command, extraEnv, commandDir(), line -> {
            activity.pulse(0.35 + Math.min(0.6, line.length() / 160.0));
            onLine(line);
            emit("out", Signal.data(line));
        }, code -> {
            long elapsed = System.currentTimeMillis() - launchedAt;
            boolean ok = overallSuccess(code);
            boolean stopped = KILL_EXIT_CODES.contains(code);
            onEdt(() -> {
                runLed.setBlinking(false);
                runLed.setOn(false);
                okLed.setOn(ok);
                failLed.setOn(!ok && !stopped);
                if (stopped) {
                    statusLcd.setTextColor(RackStyle.LCD_AMBER);
                    statusLcd.setText("STOPPED  " + (elapsed / 1000.0) + "s");
                } else {
                    statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                    statusLcd.setText((ok ? "OK" : "FAIL [" + code + "]") + "  " + (elapsed / 1000.0) + "s");
                }
            });
            onFinished(code);
            if (stopped) {
                // a deliberate stop is not a failure: no toast, and no
                // ok/fail triggers rippling down a pipeline someone just halted
                return;
            }
            if (!ok) {
                toastFailure(code);
            }
            emit(ok ? "ok" : "fail", Signal.trigger(ok));
            emit("done", Signal.trigger(ok));
        });
        return true;
    }

    /** One step of a multi-toolchain sequence: a command and where to run it. */
    protected record Step(List<String> command, java.io.File dir) {
        protected Step {
            command = List.copyOf(command); // callers keep no mutation handle
        }
    }

    /**
     * Runs steps back to back: LEDs and the meter span the whole
     * sequence, the LCD counts progress, the first failure stops the
     * train, and ok/fail/done fire once at the end.
     */
    protected void launchSequence(List<Step> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        if (requiresProjectManifest() && !ProjectInspector.hasProjectManifest(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO PROJECT MANIFEST — USE PROJECT… TO AIM THE RACK");
            });
            return;
        }
        if (requiresProjectManifest() && !trustCheck.test(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            });
            return;
        }
        final long launchedAt = System.currentTimeMillis();
        onEdt(() -> {
            runLed.setBlinking(true);
            okLed.setOn(false);
            failLed.setOn(false);
        });
        runStep(steps, 0, launchedAt);
    }

    private void runStep(List<Step> steps, int index, long launchedAt) {
        Step step = steps.get(index);
        onEdt(() -> {
            statusLcd.setTextColor(RackStyle.LCD_AMBER);
            statusLcd.setText((index + 1) + "/" + steps.size() + "  "
                    + String.join(" ", step.command()));
        });
        exec(step.command(), Map.of(), step.dir(), line -> {
            activity.pulse(0.35 + Math.min(0.6, line.length() / 160.0));
            onLine(line);
            emit("out", Signal.data(line));
        }, code -> {
            boolean stopped = KILL_EXIT_CODES.contains(code);
            if (code == 0 && index + 1 < steps.size() && !stopped) {
                runStep(steps, index + 1, launchedAt);
                return;
            }
            long elapsed = System.currentTimeMillis() - launchedAt;
            boolean ok = overallSuccess(code);
            onEdt(() -> {
                runLed.setBlinking(false);
                okLed.setOn(ok);
                failLed.setOn(!ok && !stopped);
                if (stopped) {
                    statusLcd.setTextColor(RackStyle.LCD_AMBER);
                    statusLcd.setText("STOPPED  " + (elapsed / 1000.0) + "s");
                } else {
                    statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                    statusLcd.setText((ok ? "OK " + steps.size() + "/" + steps.size()
                            : "FAIL [" + code + "] AT " + (index + 1) + "/" + steps.size())
                            + "  " + (elapsed / 1000.0) + "s");
                }
            });
            onFinished(code);
            if (stopped) {
                return;
            }
            if (!ok) {
                toastFailure(code);
            }
            emit(ok ? "ok" : "fail", Signal.trigger(ok));
            emit("done", Signal.trigger(ok));
        });
    }

    /**
     * Surfaces a real failure as an IDE notification balloon so a broken
     * chained pipeline is noticed even with the rack window buried.
     * Rate-limited per device; STOP-button kills never toast.
     */
    private void toastFailure(int code) {
        long now = System.currentTimeMillis();
        if (now - lastToastAt < 10_000) {
            return;
        }
        lastToastAt = now;
        onEdt(() -> {
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        getTitle() + " failed (exit " + code + ")",
                        javax.swing.UIManager.getIcon("OptionPane.errorIcon"),
                        "Project: " + projectDir().getName() + " — click to open the output",
                        e -> org.nmox.studio.rack.engine.CommandExecutor.showOutput(busName()));
            } catch (RuntimeException | LinkageError ignored) {
                // notification service unavailable (tests, stripped platform)
            }
        });
    }

    /** Hook: inspect each output line (worker thread). */
    protected void onLine(String line) {
    }

    /** Hook: inspect the exit code (worker thread). */
    protected void onFinished(int exitCode) {
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (isDisposed()) {
            return; // a signal delivered after removal must not launch anything
        }
        if ("run".equals(in.getId()) && signal.type() == SignalType.TRIGGER) {
            primaryAction();
        }
    }

    /**
     * A device deleted mid-serve must not leave a ghost URL in the registry:
     * the exit pump usually deregisters via {@code onFinished}, but a kill
     * the kernel refuses (or any path that skips the pump) would otherwise
     * strand the entry forever. Deregistering an absent id is a no-op.
     */
    @Override
    public void dispose() {
        deregisterServing();
        super.dispose();
    }

    // ---- serving registry ----

    /**
     * This device instance's registry key: stable for the instance's
     * lifetime, distinct across two racked units of the same type.
     */
    protected final String servingId() {
        return getTypeId() + "@" + Integer.toHexString(System.identityHashCode(this));
    }

    /**
     * Announces this device's live URL to the {@link org.nmox.studio.rack.service.ServingRegistry}
     * — call at the exact moment the URL signal is emitted. Re-announcing
     * the same URL is a no-op inside the registry.
     */
    protected final void registerServing(String url, org.nmox.studio.rack.service.ServingRegistry.Kind kind) {
        org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                new org.nmox.studio.rack.service.ServingRegistry.Serving(
                        servingId(), getTitle(), url, kind, projectDir()));
    }

    /** Withdraws this device's serving — call from the exit/stop path. */
    protected final void deregisterServing() {
        org.nmox.studio.rack.service.ServingRegistry.getDefault().deregister(servingId());
    }

    /** True when the changed-manifest batch carries any of these filenames. */
    protected static boolean anyNamed(java.util.List<java.nio.file.Path> changed, String... names) {
        for (java.nio.file.Path p : changed) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            for (String candidate : names) {
                if (candidate.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Convenience for one-off env additions; not yet per-launch. */
    protected void putRackEnv(String key, String value) {
        if (getRack() != null) {
            getRack().putEnv(key, value);
        }
    }

    protected static Map<String, String> noEnv() {
        return Map.of();
    }

    protected static List<String> parseArguments(String commandLine) {
        List<String> list = new java.util.ArrayList<>();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return list;
        }
        
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '\"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            list.add(current.toString());
        }
        
        return list;
    }
}
