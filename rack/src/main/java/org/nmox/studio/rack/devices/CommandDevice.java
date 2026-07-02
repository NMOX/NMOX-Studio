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

    private volatile long startedAt;
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
        return ProjectInspector.kindDir(projectDir(), effectiveKind());
    }

    /** The primary command, exposed for the CI exporter; null = no step. */
    public final List<String> exportCommand() {
        try {
            return buildCommand();
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
    protected void launch(List<String> command) {
        launchWithEnv(command, Map.of());
    }

    /** {@link #launch} with extra environment for this run only (e.g. MAVEN_OPTS). */
    protected void launchWithEnv(List<String> command, Map<String, String> extraEnv) {
        if (command == null || command.isEmpty()) {
            return;
        }
        if (requiresProjectManifest() && !ProjectInspector.hasProjectManifest(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO PROJECT MANIFEST — USE PROJECT… TO AIM THE RACK");
            });
            return;
        }
        if (requiresProjectManifest() && !org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            });
            return;
        }
        startedAt = System.currentTimeMillis();
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
            long elapsed = System.currentTimeMillis() - startedAt;
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
    }

    /** One step of a multi-toolchain sequence: a command and where to run it. */
    protected record Step(List<String> command, java.io.File dir) {
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
        if (requiresProjectManifest() && !org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectDir())) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            });
            return;
        }
        startedAt = System.currentTimeMillis();
        onEdt(() -> {
            runLed.setBlinking(true);
            okLed.setOn(false);
            failLed.setOn(false);
        });
        runStep(steps, 0);
    }

    private void runStep(List<Step> steps, int index) {
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
                runStep(steps, index + 1);
                return;
            }
            long elapsed = System.currentTimeMillis() - startedAt;
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
                        e -> org.nmox.studio.rack.engine.CommandExecutor.showOutput(getTitle()));
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
        if ("run".equals(in.getId()) && signal.type() == SignalType.TRIGGER) {
            primaryAction();
        }
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
