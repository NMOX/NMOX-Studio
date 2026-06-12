package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * TYPEGUARD Type Checker: tsc --noEmit over the project. In WATCH mode
 * the compiler stays resident and TYPEGUARD fires OK/FAIL after every
 * incremental check - patch REFLEX out of the loop and let tsc's own
 * watcher drive the pipeline for TypeScript projects.
 */
public class TypecheckDevice extends CommandDevice {

    private static final Pattern FOUND_ERRORS = Pattern.compile("Found (\\d+) errors?");
    private static final long WATCH_FIRE_COOLDOWN_MS = 1200;

    private final ToggleSwitch watchSwitch;
    private final ToggleSwitch strictSwitch;
    private final LcdDisplay errorLcd;
    private final Led cleanLed;
    private volatile long lastWatchFire;

    public TypecheckDevice() {
        super("typecheck", "TYPEGUARD", "TYPE CHECKER", new Color(49, 120, 198), 2);

        RackButton check = place(new RackButton("CHECK", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        watchSwitch = place(new ToggleSwitch("WATCH", false), 180, 42);
        strictSwitch = place(new ToggleSwitch("STRICT", false), 250, 42);
        errorLcd = place(new LcdDisplay(110, 1), 324, 52);
        errorLcd.setText("E:-");
        cleanLed = place(new Led("SOUND", RackStyle.GO), 442, 58);

        check.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        param("watch", watchSwitch);
        param("strict", strictSwitch);
    }

    @Override
    protected void primaryAction() {
        onEdt(() -> {
            cleanLed.setOn(false);
            errorLcd.setTextColor(RackStyle.LCD_TEXT);
            errorLcd.setText("E:-");
        });
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>(List.of("npx", "tsc", "--noEmit", "--pretty", "false"));
        if (strictSwitch.isOn()) {
            cmd.add("--strict");
        }
        if (watchSwitch.isOn()) {
            cmd.add("--watch");
        }
        return cmd;
    }

    @Override
    protected void onLine(String line) {
        Matcher m = FOUND_ERRORS.matcher(line);
        if (!m.find()) {
            return;
        }
        int errors = Integer.parseInt(m.group(1));
        onEdt(() -> {
            errorLcd.setTextColor(errors == 0 ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
            errorLcd.setText("E:" + errors);
            cleanLed.setOn(errors == 0);
        });
        // in watch mode this is the per-cycle verdict; fire triggers per check
        if (watchSwitch.isOn() && isProcessRunning()) {
            long now = System.currentTimeMillis();
            if (now - lastWatchFire < WATCH_FIRE_COOLDOWN_MS) {
                return;
            }
            lastWatchFire = now;
            onEdt(() -> {
                okLed.setOn(errors == 0);
                failLed.setOn(errors != 0);
                statusLcd.setTextColor(errors == 0 ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                statusLcd.setText(errors == 0 ? "TYPES SOUND — WATCHING" : errors + " TYPE ERRORS — WATCHING");
            });
            emit(errors == 0 ? "ok" : "fail", Signal.trigger(errors == 0));
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        onEdt(() -> cleanLed.setOn(exitCode == 0));
    }
}
