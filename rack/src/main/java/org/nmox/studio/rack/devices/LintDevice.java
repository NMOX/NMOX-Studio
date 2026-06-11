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

        linterKnob = place(new Knob("LINTER", LINTERS, 0), 44, 40);
        fixSwitch = place(new ToggleSwitch("FIX", false), 120, 42);
        RackButton run = place(new RackButton("LINT", new Color(80, 235, 100)), 184, 52);
        countLcd = place(new LcdDisplay(120, 1), 252, 52);
        cleanLed = place(new Led("CLEAN", new Color(80, 235, 100)), 386, 58);
        countLcd.setText("E:- W:-");

        run.addActionListener(e -> primaryAction());

        param("linter", linterKnob);
        param("fix", fixSwitch);
    }

    @Override
    protected void primaryAction() {
        onEdt(() -> {
            cleanLed.setOn(false);
            countLcd.setText("E:- W:-");
        });
        launch(buildCommand());
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

    @Override
    protected void onLine(String line) {
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
    protected void onFinished(int exitCode) {
        onEdt(() -> cleanLed.setOn(exitCode == 0));
    }
}
