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

// "biome"/"auto" appended, never inserted: knob positions persist by
    // index in saved patches (the v1.59.0 law). New devices default to auto.
    private static final String[] LINTERS = {"eslint", "stylelint", "biome", "auto"};
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
        linterKnob = place(new Knob("LINTER", LINTERS, 3), 112, 40);
        fixSwitch = place(new ToggleSwitch("FIX", false), 182, 42);
        countLcd = place(new LcdDisplay(120, 1), 252, 52);
        countLcd.getAccessibleContext().setAccessibleName("findings");
        cleanLed = place(new Led("CLEAN", RackStyle.GO), 386, 58);
        countLcd.setText("E:- W:-");

        run.addActionListener(e -> primaryAction());

        param("linter", linterKnob);
        param("fix", fixSwitch);
    }

    /** The dialed linter with auto resolved — the project's own toolchain. */
    private String effectiveLinter() {
        String linter = linterKnob.getSelectedOption();
        if ("auto".equals(linter)) {
            // a biome.json means the project lints with biome
            return ProjectInspector.hasBiome(projectDir()) ? "biome" : "eslint";
        }
        return linter;
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        String linter = effectiveLinter();
        switch (linter) {
            case "stylelint" -> cmd.addAll(List.of("npx", "stylelint", "**/*.css"));
            case "biome" -> cmd.addAll(List.of("npx", "@biomejs/biome", "lint", "."));
            default -> cmd.addAll(List.of("npx", "eslint", "."));
        }
        if (fixSwitch.isOn()) {
            // biome spells autofix --write, the others --fix
            cmd.add("biome".equals(linter) ? "--write" : "--fix");
        }
        return cmd;
    }

    private final java.util.List<org.nmox.studio.rack.engine.DiagnosticsBus.Problem> collected =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile java.io.File currentFile;
    // Greedy capture of the rest of the line (linear-time): the old lazy
    // ".*?" with an optional trailing rule-name group could backtrack
    // catastrophically (ReDoS) on a long message. The message is trimmed at
    // the call site and may carry the rule name, which is fine to show.
    private static final java.util.regex.Pattern ESLINT_LOC =
            java.util.regex.Pattern.compile("^\\s+(\\d+):(\\d+)\\s+(error|warning)\\s+(.*)$");
    // biome's block header: path:line:col rule ━━━ ; and its summary lines
    // "Found N errors." / "Found N warnings." (singular forms too)
    private static final java.util.regex.Pattern BIOME_LOC =
            java.util.regex.Pattern.compile("^([^\\s:]+):(\\d+):(\\d+)\\s+(\\S+)");
    private static final java.util.regex.Pattern BIOME_FOUND =
            java.util.regex.Pattern.compile("Found\\s+(\\d+)\\s+(error|warning)s?\\.");
    private volatile String activeLinter = "eslint";
    private volatile String biomeErrors = "0", biomeWarnings = "0";

    @Override
    protected void onLine(String line) {
        if ("biome".equals(activeLinter)) {
            java.util.regex.Matcher b = BIOME_LOC.matcher(line);
            if (b.find()) {
                java.io.File f = new java.io.File(b.group(1));
                f = f.isAbsolute() ? f : new java.io.File(commandDir(), b.group(1));
                if (f.isFile()) {
                    collected.add(new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(
                            f, Numbers.intOrZero(b.group(2)), b.group(4), true));
                }
            }
            java.util.regex.Matcher found = BIOME_FOUND.matcher(line);
            if (found.find()) {
                if ("error".equals(found.group(2))) {
                    biomeErrors = found.group(1);
                } else {
                    biomeWarnings = found.group(1);
                }
                String errors = biomeErrors, warnings = biomeWarnings;
                onEdt(() -> {
                    countLcd.setTextColor("0".equals(errors)
                            ? org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT : new Color(255, 90, 80));
                    countLcd.setText("E:" + errors + " W:" + warnings);
                });
            }
            return;
        }
        if (!line.startsWith(" ") && !line.isBlank() && (line.contains("/") || line.endsWith(".js") || line.endsWith(".ts"))) {
            java.io.File f = new java.io.File(line.trim());
            currentFile = f.isAbsolute() ? f : new java.io.File(commandDir(), line.trim());
        }
        java.util.regex.Matcher loc = ESLINT_LOC.matcher(line);
        java.io.File file = currentFile;
        if (loc.find() && file != null && file.isFile()) {
            collected.add(new org.nmox.studio.rack.engine.DiagnosticsBus.Problem(
                    file, Numbers.intOrZero(loc.group(1)), loc.group(4).trim(),
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
        activeLinter = effectiveLinter();
        biomeErrors = "0";
        biomeWarnings = "0";
        onEdt(() -> {
            cleanLed.setOn(false);
            countLcd.setText("E:- W:-");
        });
        launch(buildCommand());
    }

    /** Test seams: arm a parse run and read what it collected. */
    void beginParseForTest() {
        collected.clear();
        currentFile = null;
        activeLinter = effectiveLinter();
        biomeErrors = "0";
        biomeWarnings = "0";
    }

    java.util.List<org.nmox.studio.rack.engine.DiagnosticsBus.Problem> collectedForTest() {
        return new java.util.ArrayList<>(collected);
    }

    String lcdTextForTest() {
        return countLcd.getText();
    }

    @Override
    protected void onFinished(int exitCode) {
        onEdt(() -> cleanLed.setOn(exitCode == 0));
        // the squiggle/Action Items label names the tool that actually ran
        org.nmox.studio.rack.engine.DiagnosticsBus.publish(
                "biome".equals(activeLinter) ? "biome" : "eslint",
                new java.util.ArrayList<>(collected));
    }
}
