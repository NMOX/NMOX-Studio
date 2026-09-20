package org.nmox.studio.rack.devices;

import org.nmox.studio.core.util.PlainText;
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

    /**
     * What every refusal says when the rack is aimed at a directory with
     * no recognized manifest. One home: the sentence is read by the
     * launch gate, the sequence gate AND {@link #noCommandReason()}, and
     * three copies of a sentence are three chances for two of them to
     * drift apart.
     */
    static final String NO_MANIFEST =
            "NO PROJECT MANIFEST — USE PROJECT… TO AIM THE RACK";

    /** True when {@code tool} resolves on the IDE's augmented PATH — the
     *  shared availability probe every console's grey-honestly path uses
     *  (moved here from StellarDevice in the v1.141.0 debt sprint: a
     *  device borrowing another device's static was a reach-in).
     *
     *  <p>Both halves of the answer belong to {@link
     *  org.nmox.studio.core.process.ToolLocator}: this asked it for the
     *  augmented PATH and then re-implemented the suffix rule beside it,
     *  WRONG — it knew {@code .exe} and not the {@code .cmd} npm ships
     *  its shims as, the arm the v1.42.0 Windows lane added to the other
     *  two homes. No console probes an npm shim today (stellar, slither
     *  and anchor are real binaries), so it was a latent trap rather
     *  than a live bug; sharing the predicate keeps it that way. */
    protected static boolean toolOnPath(String tool) {
        for (String dir : org.nmox.studio.core.process.ToolLocator.augmentedPath()
                .split(java.io.File.pathSeparator)) {
            if (org.nmox.studio.core.process.ToolLocator.foundIn(new java.io.File(dir), tool) != null) {
                return true;
            }
        }
        return false;
    }


    /** Exit codes from STOP-button kills (SIGINT/SIGKILL/SIGTERM); not real failures. */
    private static final Set<Integer> KILL_EXIT_CODES = Set.of(130, 137, 143);
    /**
     * The user pressed STOP on this run (v2.69.15). A server that traps
     * TERM and exits 0 (node's http-server, measured on the rack walk)
     * used to read "OK" after the user stopped it; the gesture is the
     * truth — a stop is a stop, whatever the exit code.
     */
    private volatile boolean stopRequested;

    /** Pure: a run is STOPPED when the user asked for it or the exit code says a signal took it. */
    static boolean stoppedByUserOrSignal(boolean stopRequested, int code) {
        return stopRequested || KILL_EXIT_CODES.contains(code);
    }

    /**
     * The USER's stop — the STOP button, or a patched STOP jack. Sets the
     * flag the verdict reads, then stops. The internal cancels
     * ({@code RackDevice.exec} kills any previous run before every spawn;
     * dispose; panic) call {@link #stopProcess()} directly and stay
     * unflagged — the first cut flagged them too and every run in a
     * pipeline read STOPPED, so no ok/fail trigger ever rippled on.
     */
    protected final void stopByUser() {
        markStoppedByUser();
        stopProcess();
    }

    /** The verdict flag alone (v2.75.0): Stop All sets it, then panics. */
    @Override
    protected void markStoppedByUser() {
        stopRequested = true;
    }

    /** The toolbar ■ / RUNNING row / ⌘I stop is the USER's stop: it reads STOPPED (v2.74.0). */
    @Override
    protected void stopFromOutside() {
        stopByUser();
    }

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
            // the preview MEANS its markup; the argv (a package.json script name, a typed
            // SOLDER command) and the directory are external and ride PlainText.escape
            return "<html><code>$ " + PlainText.escape(String.join(" ", cmd))
                    + "</code><br>in " + PlainText.escape(commandDir().getAbsolutePath()) + "</html>";
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
    /**
     * What the LCD says when this device has no command for the project's
     * toolchain. Names the device and the toolchain so the reason is
     * self-evident ("NO CRATE VERB FOR GLEAM"), and says the honest thing
     * about it: the toolchain simply doesn't offer that operation, so
     * there is nothing to fix. Devices with a better sentence — a knob to
     * turn, a file to add — override this.
     */
    protected String noCommandReason() {
        ProjectInspector.ProjectKind k = effectiveKind();
        if (k == null || (k == ProjectInspector.ProjectKind.NONE && requiresProjectManifest())) {
            // NONE is not a toolchain with a missing verb, it is a rack
            // with nothing aimed at it, and the manifest sentence says what
            // to DO about that. The command check runs before the manifest
            // check, so without this the honest-grey path would answer a
            // question the user never asked ("NO IGNITION VERB FOR NONE").
            return NO_MANIFEST;
        }
        return "NO " + getTitle() + " VERB FOR " + k.name()
                + " — THAT TOOLCHAIN HAS NO SUCH COMMAND";
    }

    protected boolean requiresProjectManifest() {
        return true;
    }

    /**
     * A refused launch speaks on the patch bay as well as the faceplate.
     *
     * <p>The LCD said "NO PROJECT MANIFEST" or "UNTRUSTED WORKSPACE" and
     * nothing left the rear jacks, so a pipeline waiting on this device
     * waited forever: POLYGLOT_GAUNTLET's QUORUM is wired on DONE and
     * never fired when one lane was refused (the 2026-09-17 rack audit).
     * A refusal is a verdict — FAIL and DONE both carry {@code false},
     * exactly what a run that failed emits, so every downstream reads it
     * the same way (KVASIR's EXPLAIN keys on that bit). Every refusal in
     * the family routes through here: the base launch guards, GOVERNOR's
     * missing snapshot, the chain consoles' missing tools and manifests.
     */
    protected final void refuseLaunch(String reason) {
        onEdt(() -> {
            statusLcd.setTextColor(RackStyle.LCD_AMBER);
            statusLcd.setText(reason);
        });
        emit("fail", Signal.trigger(false));
        emit("done", Signal.trigger(false));
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
     * Puts the trust gate back to production — the real prompt.
     *
     * <p>Six test classes each kept their own {@code originalTrust} field and
     * their own restore. Each was correct; together they were six chances to
     * forget, and what this seam disables is the gate that stands between a
     * cloned repository and a spawned process. A forgotten restore leaks
     * {@code trustCheck = f -> true} into every later test in the fork, and the
     * build stays green while proving nothing about the law it is there to
     * prove. One restore, stated once.
     */
    static void resetTrustCheck() {
        trustCheck = org.nmox.studio.rack.service.WorkspaceTrust::requestTrust;
    }

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
            // A null command is how a device says "this toolchain has no such
            // verb" (gleam has no `outdated`, elm has no `run`, …). Two dozen
            // call sites comment this as "CHECK greys" / "IGNITION greys" —
            // but nothing ever greyed: the button stayed lit and the click did
            // NOTHING. To a beginner on a Gleam or Racket learning space that
            // reads as a broken product, and there is no way to find out
            // otherwise. Say it instead. DebugDevice has spoken this exact
            // refusal honestly since v1.77.1; this brings the whole family up
            // to that bar at the one choke point they all pass through.
            refuseLaunch(noCommandReason());
            return false;
        }
        if (requiresProjectManifest() && !ProjectInspector.hasProjectManifest(projectDir())) {
            refuseLaunch(NO_MANIFEST);
            return false;
        }
        if (requiresProjectManifest() && !trustCheck.test(projectDir())) {
            refuseLaunch("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            return false;
        }
        // captured per launch: a relaunch must not skew a still-running
        // command's elapsed-time readout
        final long launchedAt = System.currentTimeMillis();
        stopRequested = false;
        // a new run announces afresh: READY once, the URL when it changes
        clearServingAnnouncement();
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
        }, (code, superseded) -> {
            if (superseded) {
                // A newer launch already replaced this run (ledger 18): the
                // faceplate, the serving registry, the SERVING gate and the
                // ok/fail/done jacks all belong to that run now. This one
                // was killed to make room for it — it did not finish, and
                // saying so would tell a live server it had stopped.
                return;
            }
            long elapsed = System.currentTimeMillis() - launchedAt;
            boolean ok = overallSuccess(code);
            boolean stopped = stoppedByUserOrSignal(stopRequested, code);
            stopRequested = false;
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
            withdrawPrintedServer();
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
            refuseLaunch(NO_MANIFEST);
            return;
        }
        if (requiresProjectManifest() && !trustCheck.test(projectDir())) {
            refuseLaunch("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
            return;
        }
        final long launchedAt = System.currentTimeMillis();
        stopRequested = false;
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
        }, (code, superseded) -> {
            if (superseded) {
                // ledger 18, and a sequence has a second way to be wrong
                // about it: a replaced step must not march the rest of a
                // train the user has already swapped out into the run that
                // replaced it. The chain ends where it was replaced.
                return;
            }
            boolean stopped = stoppedByUserOrSignal(stopRequested, code);
            stopRequested = false;
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
            withdrawPrintedServer();
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
        announcePrintedServer(line);
    }

    /** The local URL this run printed and announced (v2.69.16); one banner registers once, exit clears it. */
    private volatile String printedServerUrl;

    /**
     * A command that prints a local address IS a server (v2.69.16): the
     * rule the IDE's Run lane and the serve devices already follow, now the
     * default for every command device that does not shape its own output
     * (SOLDER's npx http-server, NPM-9000's npm run dev, DYNAMO's gulp
     * serve). Registers only once the process has SAID it is listening
     * (the v1.93.0 serving-truth law), never opens a browser, and the exit
     * handler withdraws it. Devices whose output can name a local address
     * that is NOT this machine's server opt out via {@link #announcesPrintedServers()}.
     */
    protected final void announcePrintedServer(String line) {
        if (!announcesPrintedServers()) {
            return;
        }
        String url = ServeUrls.firstLocalUrl(line);
        if (url != null && !url.equals(printedServerUrl)) {
            printedServerUrl = url;
            registerServing(url, org.nmox.studio.rack.service.ServingRegistry.Kind.WEB);
        }
    }

    /** Whether a printed local address means a server on THIS machine that this run owns. */
    protected boolean announcesPrintedServers() {
        return true;
    }

    /** Package-private for the serving tests: what the exit handler runs before {@link #onFinished(int)}. */
    void withdrawPrintedServer() {
        if (printedServerUrl != null) {
            deregisterServing();
            printedServerUrl = null;
        }
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

    // ---- the serving announcement: URL first, then READY ----

    /** READY fires once per run; reset at every launch and on {@link #clearServingAnnouncement()}. */
    private final java.util.concurrent.atomic.AtomicBoolean readyFired =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** The URL this run last announced on its URL jack; null until the first announce. */
    private volatile String announcedUrl;

    /** The URL this run has announced so far, or null. */
    protected final String announcedUrl() {
        return announcedUrl;
    }

    /**
     * Announces a live server on the cables and in the registry, in the ONE
     * order that lets a patch work: {@code url} (DATA, when it changed), the
     * registry, then {@code ready} (TRIGGER, once per run). Every preset and
     * template wires {@code server.url → SCOPE.url} and {@code server.ready →
     * SCOPE.open}, and the router is one FIFO thread — so a device that emitted
     * READY before URL had SCOPE open its LCD's stale default on a first
     * serve: the Angular template on 4200 opened {@code http://localhost:5173}
     * (the 2026-09-17 rack walk). Twelve serve devices each spelled the pair
     * by hand and nine had them the wrong way round; this is the one home,
     * and {@code ServingAnnounceOrderGateTest} refuses a device emitting
     * {@code ready} on its own. Returns whether the URL was new.
     */
    protected final boolean announceServing(String url,
            org.nmox.studio.rack.service.ServingRegistry.Kind kind) {
        boolean changed = announce(url);
        if (changed && kind != null) {
            registerServing(url, kind);
        }
        ready();
        return changed;
    }

    /**
     * The cables-only announcement for a server this PROCESS does not own —
     * STELLAR's quickstart net lives in a detached container that outlives
     * the start command, so no registry entry and no SERVING gate (the
     * v1.93.0 serving-truth law); the URL still flows for SCOPE and READY
     * still fires, in the same order as {@link #announceServing}.
     */
    protected final boolean announceServingUnowned(String url) {
        return announceServing(url, null);
    }

    private boolean announce(String url) {
        if (url == null || url.equals(announcedUrl)) {
            return false;
        }
        announcedUrl = url;
        emit("url", Signal.data(url));
        return true;
    }

    private void ready() {
        if (readyFired.compareAndSet(false, true)) {
            emit("ready", Signal.trigger());
        }
    }

    /** Forgets the announced URL and the READY latch so a restart re-announces even on the same port. */
    protected final void clearServingAnnouncement() {
        announcedUrl = null;
        readyFired.set(false);
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
