package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nmox.studio.rack.devices.PreflightPlan.Check;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * PREFLIGHT Ship Check: "done" as a machine state instead of a
 * feeling. CHECK runs the whole readiness list - git clean, tests,
 * build, lint, audit - one LED per item, and the verdict LCD says
 * READY TO SHIP or names the blocker. Patch OK into LAUNCHPAD and
 * "you cannot deploy what isn't verified" stops being a policy
 * document and becomes a cable.
 */
public class PreflightDevice extends RackDevice {

    private static final Color OK = RackStyle.GO;
    private static final Color FAIL = RackStyle.STOP;
    private static final Color SOFT = RackStyle.MUTATE;

    private final LcdDisplay checklistLcd;
    private final LcdDisplay verdictLcd;
    private final Led runningLed;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean stopRequested;

    public PreflightDevice() {
        super("preflight", "PREFLIGHT", "SHIP CHECK", new Color(126, 217, 87), 3);

        RackButton check = place(new RackButton("CHECK", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        check.setToolTipText("Run the readiness list: git clean, tests, build, lint, audit");
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        runningLed = place(new Led("RUN", RackStyle.MUTATE), 180, 58);

        checklistLcd = place(new LcdDisplay(380, 6), 232, 34);
        verdictLcd = place(new LcdDisplay(240, 1), 630, 34);
        checklistLcd.appendLine("CHECK TO VERIFY READINESS");
        verdictLcd.setText("UNVERIFIED");
        verdictLcd.setToolTipText("the machine's opinion of whether you can ship");

        check.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> {
            stopRequested = true;
            stopProcess();
        });

        addInPort("run", "RUN", SignalType.TRIGGER);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);
        addOutPort("done", "DONE", SignalType.TRIGGER);
        addOutPort("out", "OUT", SignalType.DATA);
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("run".equals(in.getId())) {
            primaryAction();
        }
    }

    @Override
    public void resume() {
        // a verdict is a measurement, not a service: nothing to resurrect
    }

    private void primaryAction() {
        if (!running.compareAndSet(false, true)) {
            return; // one preflight at a time
        }
        stopRequested = false;
        List<Check> checks = PreflightPlan.forProject(projectDir());
        onEdt(() -> {
            runningLed.setBlinking(true);
            checklistLcd.clear();
            verdictLcd.setTextColor(RackStyle.LCD_AMBER);
            verdictLcd.setText("CHECKING…");
            if (checks.isEmpty()) {
                checklistLcd.appendLine("NOTHING TO CHECK — NO TOOLCHAIN, NO REPO", SOFT);
            }
        });
        if (checks.isEmpty()) {
            finish(true, 0, 0);
            return;
        }
        runCheck(checks, 0, 0, 0);
    }

    private void runCheck(List<Check> checks, int index, int failures, int warnings) {
        if (stopRequested || index >= checks.size()) {
            finish(!stopRequested && failures == 0, failures, warnings);
            return;
        }
        Check check = checks.get(index);
        onEdt(() -> checklistLcd.appendLine("· " + check.name() + " …"));
        StringBuilder output = new StringBuilder();
        exec(check.command(), Map.of(), projectDir(), line -> {
            if (output.length() < 20_000) {
                output.append(line).append('\n');
            }
        }, code -> {
            boolean passed = PreflightPlan.passed(check, code, output.toString());
            boolean soft = check.soft();
            onEdt(() -> {
                // rewrite the pending line with the verdict
                replaceLastLine(check.name(), passed, soft, output.toString());
            });
            emit("out", Signal.data("preflight " + check.name() + ": "
                    + (passed ? "ok" : soft ? "warn" : "FAIL")));
            runCheck(checks, index + 1,
                    failures + (!passed && !soft ? 1 : 0),
                    warnings + (!passed && soft ? 1 : 0));
        });
    }

    private void replaceLastLine(String name, boolean passed, boolean soft, String output) {
        // LcdDisplay has no line replace; append the verdict line instead -
        // the scroll reads like a checklist completing
        if (passed) {
            checklistLcd.appendLine("✓ " + name, OK);
        } else if (soft) {
            checklistLcd.appendLine("! " + name + " — ADVISORY", SOFT);
        } else {
            String hint = firstUsefulLine(output);
            checklistLcd.appendLine("✗ " + name + (hint.isEmpty() ? "" : " — " + hint), FAIL);
        }
    }

    static String firstUsefulLine(String output) {
        for (String line : output.split("\n")) {
            String t = line.strip();
            if (!t.isEmpty() && !t.startsWith(">") && !t.startsWith("$")) {
                return t.length() > 40 ? t.substring(0, 40) + "…" : t;
            }
        }
        return "";
    }

    private void finish(boolean ready, int failures, int warnings) {
        running.set(false);
        onEdt(() -> {
            runningLed.setBlinking(false);
            runningLed.setOn(false);
            if (stopRequested) {
                verdictLcd.setTextColor(RackStyle.LCD_AMBER);
                verdictLcd.setText("STOPPED");
                return;
            }
            if (ready) {
                verdictLcd.setTextColor(RackStyle.LCD_TEXT);
                verdictLcd.setText(warnings == 0 ? "READY TO SHIP"
                        : "READY (" + warnings + " ADVISORY)");
            } else {
                verdictLcd.setTextColor(new Color(255, 90, 80));
                verdictLcd.setText("NOT READY — " + failures + " BLOCKER"
                        + (failures == 1 ? "" : "S"));
            }
        });
        if (stopRequested) {
            return;
        }
        emit(ready ? "ok" : "fail", Signal.trigger(ready));
        emit("done", Signal.trigger(ready));
    }
}
