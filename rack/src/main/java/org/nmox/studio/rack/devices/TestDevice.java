package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * VERITAS Test Harness: runs the test suite and shows a live pass/fail
 * tally parsed from runner output (jest/vitest/mocha style summaries).
 */
public class TestDevice extends CommandDevice {

    private static final String[] FRAMEWORKS = {"auto", "jest", "vitest", "mocha", "playwright", "cypress", "pytest", "cargo", "go", "mvn", "rspec", "phpunit", "mix", "rebar3", "clojure", "swift", "dotnet", "dart", "sbt", "stack", "zig", "dune", "crystal"};
    private static final Pattern PASSED = Pattern.compile("(\\d+)\\s+(?:passed|passing)");
    private static final Pattern FAILED = Pattern.compile("(\\d+)\\s+(?:failed|failing)");

    private final Knob frameworkKnob;
    private final ToggleSwitch coverageSwitch;
    private final LcdDisplay tallyLcd;
    private volatile int passed;
    private volatile int failed;

    public TestDevice() {
        super("test", "VERITAS", "TEST HARNESS", new Color(99, 197, 70), 2);

        RackButton run = place(new RackButton("TEST", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        frameworkKnob = place(new Knob("RUNNER", FRAMEWORKS, 0), 180, 40);
        coverageSwitch = place(new ToggleSwitch("COVER", false), 254, 42);
        tallyLcd = place(new LcdDisplay(120, 1), 324, 52);
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
        ProjectInspector.ProjectKind kind = effectiveKind();
        switch (kind) {
            case RUST: return "cargo";
            case ELIXIR: return "mix";
            case ERLANG: return "rebar3";
            case CLOJURE: return "clojure";
            case SWIFT: return "swift";
            case DOTNET: return "dotnet";
            case DART: return "dart";
            case SCALA: return "sbt";
            case HASKELL: return "stack";
            case ZIG: return "zig";
            case OCAML: return "dune";
            case CRYSTAL: return "crystal";
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

    /** Tests run where the selected runner's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        ProjectInspector.ProjectKind kind = switch (effectiveFramework()) {
            case "cargo" -> ProjectInspector.ProjectKind.RUST;
            case "mix" -> ProjectInspector.ProjectKind.ELIXIR;
            case "rebar3" -> ProjectInspector.ProjectKind.ERLANG;
            case "clojure" -> ProjectInspector.ProjectKind.CLOJURE;
            case "swift" -> ProjectInspector.ProjectKind.SWIFT;
            case "dotnet" -> ProjectInspector.ProjectKind.DOTNET;
            case "dart" -> ProjectInspector.ProjectKind.DART;
            case "sbt" -> ProjectInspector.ProjectKind.SCALA;
            case "stack" -> ProjectInspector.ProjectKind.HASKELL;
            case "zig" -> ProjectInspector.ProjectKind.ZIG;
            case "dune" -> ProjectInspector.ProjectKind.OCAML;
            case "crystal" -> ProjectInspector.ProjectKind.CRYSTAL;
            case "go" -> ProjectInspector.ProjectKind.GO;
            case "mvn" -> ProjectInspector.ProjectKind.MAVEN;
            case "gradle" -> ProjectInspector.ProjectKind.GRADLE;
            case "pytest" -> ProjectInspector.ProjectKind.PYTHON;
            case "rspec" -> ProjectInspector.ProjectKind.RUBY;
            case "phpunit" -> ProjectInspector.ProjectKind.PHP;
            default -> ProjectInspector.ProjectKind.NODE;
        };
        return ProjectInspector.kindDir(projectDir(), kind);
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
            case "mix" -> cmd.addAll(List.of("mix", "test"));
            case "rebar3" -> cmd.addAll(List.of("rebar3", "eunit"));
            case "clojure" -> cmd.addAll(List.of("clojure", "-X:test"));
            case "swift" -> cmd.addAll(List.of("swift", "test"));
            case "dotnet" -> cmd.addAll(List.of("dotnet", "test"));
            case "dart" -> cmd.addAll(List.of("dart", "test"));
            case "sbt" -> cmd.addAll(List.of("sbt", "test"));
            case "stack" -> cmd.addAll(List.of("stack", "test"));
            case "zig" -> cmd.addAll(List.of("zig", "build", "test"));
            case "dune" -> cmd.addAll(List.of("dune", "runtest"));
            case "crystal" -> cmd.addAll(List.of("crystal", "spec"));
            case "go" -> cmd.addAll(List.of("go", "test", "./..."));
            case "mvn" -> cmd.addAll(List.of("mvn", "-q", "test"));
            case "gradle" -> cmd.addAll(List.of("gradle", "test"));
            case "rspec" -> cmd.addAll(new java.io.File(commandDir(), "spec").isDirectory()
                    ? List.of("bundle", "exec", "rspec")
                    : List.of("rake", "test"));
            case "phpunit" -> cmd.addAll(new java.io.File(commandDir(), "vendor/bin/phpunit").isFile()
                    ? List.of("./vendor/bin/phpunit")
                    : List.of("phpunit"));
            default -> cmd.addAll(List.of("npm", "test"));
        }
        if (coverageSwitch.isOn()) {
            // resolve AUTO first: keyed on the raw "auto" knob the flag never
            // fired, so COVER did nothing in the default case even for jest/vitest
            switch (effectiveFramework()) {
                case "jest", "vitest" -> cmd.add("--coverage");
                case "pytest" -> cmd.add("--cov");
                default -> {
                    // no portable coverage flag for this runner (mocha needs
                    // nyc, cargo needs llvm-cov, go needs -coverprofile);
                    // leave the command for the tool to decide
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
