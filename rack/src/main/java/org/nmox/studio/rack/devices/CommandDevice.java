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
    protected final Led runLed = new Led("RUN", new Color(255, 190, 60));
    protected final Led okLed = new Led("OK", new Color(80, 235, 100));
    protected final Led failLed = new Led("FAIL", new Color(255, 70, 60));
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

    /** The action triggered by the RUN input jack; defaults to launching. */
    protected void primaryAction() {
        launch(buildCommand());
    }

    /**
     * Launches a command with the full standard treatment: LEDs, meter,
     * status LCD, OUT data per line, OK/FAIL/DONE triggers on exit.
     */
    protected void launch(List<String> command) {
        if (command == null || command.isEmpty()) {
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
        exec(command, line -> {
            activity.pulse(0.35 + Math.min(0.6, line.length() / 160.0));
            onLine(line);
            emit("out", Signal.data(line));
        }, code -> {
            long elapsed = System.currentTimeMillis() - startedAt;
            boolean ok = code == 0;
            onEdt(() -> {
                runLed.setBlinking(false);
                runLed.setOn(false);
                okLed.setOn(ok);
                failLed.setOn(!ok);
                statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                statusLcd.setText((ok ? "OK" : "FAIL [" + code + "]") + "  " + (elapsed / 1000.0) + "s");
            });
            onFinished(code);
            if (!ok && !KILL_EXIT_CODES.contains(code)) {
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
                        "Project: " + projectDir().getName()
                                + " — see the \"Rack: " + getTitle() + "\" output tab",
                        null);
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
}
