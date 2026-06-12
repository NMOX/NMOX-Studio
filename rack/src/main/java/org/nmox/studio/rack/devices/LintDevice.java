package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * PURITY Lint Filter: static analysis pass. Counts problems from the
 * "x problems (y errors, z warnings)" summary that eslint prints.
 */
public class LintDevice extends CommandDevice {

    private static final String[] LINTERS = {"eslint", "stylelint"};
    private static final Pattern SUMMARY =
            Pattern.compile("(\\d+)\\s+problems?\\s*\\((\\d+)\\s+errors?,\\s*(\\d+)\\s+warnings?\\)");

    private final Knob linterKnob;
    private final ToggleSwitch fixSwitch;
    private final LcdDisplay countLcd;
    private final Led cleanLed;

    public LintDevice() {
        super("lint", "PURITY", "LINT FILTER", new Color(168, 110, 221), 2);

        RackButton run = place(new RackButton("LINT", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        linterKnob = place(new Knob("LINTER", LINTERS, 0), 112, 40);
        fixSwitch = place(new ToggleSwitch("FIX", false), 182, 42);
        countLcd = place(new LcdDisplay(120, 1), 252, 52);
        cleanLed = place(new Led("CLEAN", RackStyle.GO), 386, 58);
        countLcd.setText("E:- W:-");

        run.addActionListener(e -> primaryAction());

        param("linter", linterKnob);
        param("fix", fixSwitch);
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        if ("stylelint".equals(linterKnob.getSelectedOption())) {
            cmd.addAll(List.of("npx", "stylelint", "**/*.css"));
        } else {
            cmd.addAll(List.of("npx", "eslint", "."));
        }
        if (fixSwitch.isOn()) {
            cmd.add("--fix");
        }
        return cmd;
    }

    private final java.util.List<org.nmox.studio.rack.engine.DiagnosticsBus.Problem> collected =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile java.io.File currentFile;
    private static final java.util.regex.Pattern ESLINT_LOC =
            java.util.regex.Pattern.compile("^\\s+(\\d+):(\\d+)\\s+(error|warning)\\s+(.*?)(?:\\s\\s+[\\w@/-]+)?$");

    @Override
    protected void onLine(String line) {
        if (!line.startsWith(" ") && !line.isBlank() && (line.contains("/") || line.endsWith(".js") || line.endsWith(".ts"))) {
            java.io.File f = new java.io.File(line.trim());
            currentFile = f.isAbsolute() ? f : new java.io.File(commandDir(), line.trim());
        }
        java.util.regex.Matcher loc = ESLINT_LOC.matcher(line);
        java.io.File file = currentFile;
        if (loc.find() && file != null && file.isFile()) {
            collected.add(new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(
                    file, Integer.parseInt(loc.group(1)), loc.group(4).trim(),
                    "error".equals(loc.group(3))));
        }
        Matcher m = SUMMARY.matcher(line);
        if (m.find()) {
            String errors = m.group(2), warnings = m.group(3);
            onEdt(() -> {
                countLcd.setTextColor("0".equals(errors)
                        ? org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT : new Color(255, 90, 80));
                countLcd.setText("E:" + errors + " W:" + warnings);
            });
        }
    }

    @Override
    protected void primaryAction() {
        collected.clear();
        currentFile = null;
        onEdt(() -> {
            cleanLed.setOn(false);
            countLcd.setText("E:- W:-");
        });
        launch(buildCommand());
    }

    @Override
    protected void onFinished(int exitCode) {
        onEdt(() -> cleanLed.setOn(exitCode == 0));
        org.nmox.studio.rack.engine.DiagnosticsBus.publish("eslint",
                new java.util.ArrayList<>(collected));
    }
}
