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
 * watcher drive the pipeline for TypeScript projects. On PHP lanes it
 * runs phpstan instead (raw format), feeding the same diagnostics bus.
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
        check.setCommandPreview(this::commandPreview);
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
    protected List<String> buildCommand() {
        // PHP lane: phpstan's raw format prints one file:line:message per error
        if (effectiveKind() == ProjectInspector.ProjectKind.PHP) {
            return List.of("vendor/bin/phpstan", "analyse", "--no-progress", "--error-format=raw");
        }
        // Foundry lane: solhint over the contract sources — only when the
        // project carries a solhint config (configless solhint just errors);
        // primaryAction turns the null into an honest LCD hint
        if (effectiveKind() == ProjectInspector.ProjectKind.FOUNDRY) {
            return hasSolhintConfig() ? List.of("npx", "solhint", "{src,test}/**/*.sol") : null;
        }
        List<String> cmd = new ArrayList<>(List.of("npx", "tsc", "--noEmit", "--pretty", "false"));
        if (strictSwitch.isOn()) {
            cmd.add("--strict");
        }
        if (watchSwitch.isOn()) {
            cmd.add("--watch");
        }
        return cmd;
    }

    private final java.util.List<org.nmox.studio.rack.engine.DiagnosticsBus.Problem> collected =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static final java.util.regex.Pattern TSC_LOC = java.util.regex.Pattern.compile(
            "^(.+?)\\((\\d+),(\\d+)\\):\\s+(error|warning)\\s+TS\\d+:\\s+(.*)$");
    /** phpstan --error-format=raw line: path/File.php:42:Message */
    private static final java.util.regex.Pattern PHPSTAN_LOC = java.util.regex.Pattern.compile(
            "^(.+?\\.php):(\\d+):(.*)$");
    /** solhint stylish summary: ✖ 5 problems (4 errors, 1 warning) */
    private static final java.util.regex.Pattern SOLHINT_SUMMARY = java.util.regex.Pattern.compile(
            "\\((\\d+) errors?");

    /** True when the Foundry lane carries a solhint config to lint with. */
    private boolean hasSolhintConfig() {
        return new java.io.File(commandDir(), ".solhint.json").isFile();
    }

    @Override
    protected void onLine(String line) {
        if (effectiveKind() == ProjectInspector.ProjectKind.FOUNDRY) {
            java.util.regex.Matcher summary = SOLHINT_SUMMARY.matcher(line);
            if (summary.find()) {
                int errors = Integer.parseInt(summary.group(1));
                onEdt(() -> {
                    errorLcd.setTextColor(errors == 0 ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                    errorLcd.setText("E:" + errors);
                    cleanLed.setOn(errors == 0);
                });
            }
            return;
        }
        if (effectiveKind() == ProjectInspector.ProjectKind.PHP) {
            java.util.regex.Matcher raw = PHPSTAN_LOC.matcher(line);
            if (raw.find()) {
                java.io.File f = new java.io.File(raw.group(1));
                if (!f.isAbsolute()) {
                    f = new java.io.File(commandDir(), raw.group(1));
                }
                if (f.isFile()) {
                    collected.add(new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(
                            f, Integer.parseInt(raw.group(2)), raw.group(3).trim(), true));
                }
            }
            return;
        }
        java.util.regex.Matcher loc = TSC_LOC.matcher(line);
        if (loc.find()) {
            java.io.File f = new java.io.File(loc.group(1));
            if (!f.isAbsolute()) {
                f = new java.io.File(commandDir(), loc.group(1));
            }
            if (f.isFile()) {
                collected.add(new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(
                        f, Integer.parseInt(loc.group(2)), loc.group(5),
                        "error".equals(loc.group(4))));
            }
        }
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
    protected void primaryAction() {
        collected.clear();
        onEdt(() -> {
            cleanLed.setOn(false);
            errorLcd.setTextColor(RackStyle.LCD_TEXT);
            errorLcd.setText("E:-");
        });
        // honest absent: the Foundry lane has no checker without a config
        if (effectiveKind() == ProjectInspector.ProjectKind.FOUNDRY && !hasSolhintConfig()) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO .solhint.json — npm i -g solhint && solhint --init");
            });
            return;
        }
        launch(buildCommand());
    }

    @Override
    protected void onFinished(int exitCode) {
        boolean php = effectiveKind() == ProjectInspector.ProjectKind.PHP;
        if (php) {
            // phpstan raw prints no "Found N errors" summary: the parsed
            // lines are the count
            int errors = collected.size();
            onEdt(() -> {
                errorLcd.setTextColor(errors == 0 ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                errorLcd.setText("E:" + errors);
            });
        }
        onEdt(() -> cleanLed.setOn(exitCode == 0));
        org.nmox.studio.rack.engine.DiagnosticsBus.publish(php ? "phpstan" : "tsc",
                new java.util.ArrayList<>(collected));
    }
}
