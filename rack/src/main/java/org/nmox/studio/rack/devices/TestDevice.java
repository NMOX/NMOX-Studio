package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * VERITAS Test Harness: runs the test suite and shows a live pass/fail
 * tally parsed from runner output (jest/vitest/mocha style summaries).
 */
public class TestDevice extends CommandDevice {

    private static final String[] FRAMEWORKS = {"auto", "jest", "vitest", "mocha", "playwright", "cypress", "pytest", "cargo", "go", "mvn", "rspec", "phpunit"};
    private static final Pattern PASSED = Pattern.compile("(\\d+)\\s+(?:passed|passing)");
    private static final Pattern FAILED = Pattern.compile("(\\d+)\\s+(?:failed|failing)");

    private final Knob frameworkKnob;
    private final ToggleSwitch coverageSwitch;
    private final LcdDisplay tallyLcd;
    private volatile int passed;
    private volatile int failed;

    public TestDevice() {
        super("test", "VERITAS", "TEST HARNESS", new Color(99, 197, 70), 2);

        frameworkKnob = place(new Knob("RUNNER", FRAMEWORKS, 0), 44, 40);
        coverageSwitch = place(new ToggleSwitch("COVER", false), 120, 42);
        RackButton run = place(new RackButton("TEST", new Color(80, 235, 100)), 184, 52);
        RackButton stop = place(new RackButton("STOP", new Color(255, 70, 60)), 248, 52);
        tallyLcd = place(new LcdDisplay(120, 1), 320, 52);
        tallyLcd.setText("P:0 F:0");

        run.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        param("framework", frameworkKnob);
        param("coverage", coverageSwitch);
    }

    @Override
    protected void primaryAction() {
        passed = 0;
        failed = 0;
        onEdt(() -> {
            tallyLcd.setTextColor(org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT);
            tallyLcd.setText("P:0 F:0");
        });
        launch(buildCommand());
    }

    /** AUTO resolves to the project's "test" script, else its test framework dependency. */
    private String effectiveFramework() {
        String fw = frameworkKnob.getSelectedOption();
        if (!"auto".equals(fw)) {
            return fw;
        }
        ProjectInspector.ProjectKind kind = ProjectInspector.detectKind(projectDir());
        switch (kind) {
            case RUST: return "cargo";
            case GO: return "go";
            case MAVEN: return "mvn";
            case GRADLE: return "gradle";
            case PYTHON: return "pytest";
            case RUBY: return "rspec";
            case PHP: return "phpunit";
            default: break;
        }
        if (ProjectInspector.hasScript(projectDir(), "test")) {
            return "npm-script";
        }
        String dep = ProjectInspector.firstDependency(projectDir(),
                "vitest", "jest", "mocha", "@playwright/test", "cypress");
        if ("@playwright/test".equals(dep)) {
            return "playwright";
        }
        return dep != null ? dep : "npm-script";
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        switch (effectiveFramework()) {
            case "jest" -> cmd.addAll(List.of("npx", "jest"));
            case "vitest" -> cmd.addAll(List.of("npx", "vitest", "run"));
            case "mocha" -> cmd.addAll(List.of("npx", "mocha"));
            case "playwright" -> cmd.addAll(List.of("npx", "playwright", "test"));
            case "cypress" -> cmd.addAll(List.of("npx", "cypress", "run"));
            case "pytest" -> cmd.addAll(List.of("python3", "-m", "pytest"));
            case "cargo" -> cmd.addAll(List.of("cargo", "test"));
            case "go" -> cmd.addAll(List.of("go", "test", "./..."));
            case "mvn" -> cmd.addAll(List.of("mvn", "-q", "test"));
            case "gradle" -> cmd.addAll(List.of("gradle", "test"));
            case "rspec" -> cmd.addAll(new java.io.File(projectDir(), "spec").isDirectory()
                    ? List.of("bundle", "exec", "rspec")
                    : List.of("rake", "test"));
            case "phpunit" -> cmd.addAll(new java.io.File(projectDir(), "vendor/bin/phpunit").isFile()
                    ? List.of("./vendor/bin/phpunit")
                    : List.of("phpunit"));
            default -> cmd.addAll(List.of("npm", "test"));
        }
        if (coverageSwitch.isOn()) {
            switch (frameworkKnob.getSelectedOption()) {
                case "jest", "vitest" -> cmd.add("--coverage");
                default -> {
                    // tool decides; coverage flag not portable
                }
            }
        }
        return cmd;
    }

    @Override
    protected void onLine(String line) {
        Matcher m = PASSED.matcher(line);
        if (m.find()) {
            passed = Integer.parseInt(m.group(1));
        }
        m = FAILED.matcher(line);
        if (m.find()) {
            failed = Integer.parseInt(m.group(1));
        }
        if (passed + failed > 0) {
            final int p = passed, f = failed;
            onEdt(() -> {
                tallyLcd.setTextColor(f > 0 ? new Color(255, 90, 80)
                        : org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT);
                tallyLcd.setText("P:" + p + " F:" + f);
            });
        }
    }
}
