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

    // append-only: persisted patches store the knob index, not the label
    private static final String[] FRAMEWORKS = {"auto", "jest", "vitest", "mocha", "playwright", "cypress", "pytest", "cargo", "go", "mvn", "rspec", "phpunit", "mix", "rebar3", "clojure", "swift", "dotnet", "dart", "sbt", "stack", "zig", "dune", "crystal", "bun", "deno", "forge", "gleam", "julia", "nim", "dlang", "racket", "elm", "purescript", "vlang", "fortran", "ada", "cairo", "move", "aiken", "node"};
    private static final Pattern PASSED = Pattern.compile("(\\d+)\\s+(?:passed|passing)");
    private static final Pattern FAILED = Pattern.compile("(\\d+)\\s+(?:failed|failing)");
    // node:test speaks TAP, whose summary counts read "# pass 3" / "# fail 1"
    // — no "passed"/"failed" word anywhere, so the tally above never moved on
    // a `node --test` project (v1.252.0, the QA persona's find)
    private static final Pattern TAP_PASS = Pattern.compile("^#\\s*pass\\s+(\\d+)\\s*$");
    private static final Pattern TAP_FAIL = Pattern.compile("^#\\s*fail\\s+(\\d+)\\s*$");
    private static final String[] COVERAGE_MINIMUMS = {"off", "50", "60", "70", "80", "90"};
    /** Failing test names, per runner family. */
    private static final Pattern[] FAILURE_LINES = {
        Pattern.compile("^\\s*[✕×✗]\\s+(.+?)(?:\\s+\\(?\\d+\\s*ms\\)?)?$"), // jest/vitest
        Pattern.compile("^FAILED\\s+(\\S+)"),                                  // pytest node id
        Pattern.compile("^test (\\S+) \\.\\.\\. FAILED$"),                     // cargo
        Pattern.compile("^--- FAIL: (\\S+)"),                                  // go
        Pattern.compile("^\\[FAIL[.:][^\\]]*\\]\\s+([A-Za-z0-9_]+)"),          // forge
        // TAP (node:test): "not ok 4 - SAVE10 takes ten percent off"; the
        // trailing " # ..." directive is TAP metadata, not part of the name
        Pattern.compile("^not ok \\d+\\s+-\\s+(.+?)(?:\\s+#.*)?$"),
    };
    /** Coverage summaries: istanbul text-summary, pytest-cov TOTAL, go -cover. */
    private static final Pattern[] COVERAGE_LINES = {
        Pattern.compile("^Lines\\s*:\\s*([\\d.]+)%"),
        Pattern.compile("^TOTAL\\s+\\d+\\s+\\d+\\s+([\\d.]+)%"),
        Pattern.compile("coverage:\\s+([\\d.]+)% of statements"),
    };

    private final Knob frameworkKnob;
    private final ToggleSwitch coverageSwitch;
    private final Knob covMinKnob;
    private final LcdDisplay tallyLcd;
    private final java.util.List<String> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int passed;
    private volatile int failed;
    private volatile double coverage = -1;

    public TestDevice() {
        super("test", "VERITAS", "TEST HARNESS", new Color(99, 197, 70), 2);

        RackButton run = place(new RackButton("TEST", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        frameworkKnob = place(new Knob("RUNNER", FRAMEWORKS, 0), 180, 40);
        coverageSwitch = place(new ToggleSwitch("COVER", false), 254, 42);
        covMinKnob = place(new Knob("MIN COV", COVERAGE_MINIMUMS, 0), 324, 40);
        covMinKnob.setToolTipText("Coverage floor (needs COVER on): below it, FAIL fires instead of OK");
        tallyLcd = place(new LcdDisplay(160, 1), 44, 82);
        tallyLcd.getAccessibleContext().setAccessibleName("test tally");
        tallyLcd.setText("P:0 F:0");
        RackButton failuresButton = place(new RackButton("FAILURES", RackStyle.QUERY), 220, 84);
        failuresButton.setToolTipText("The failing tests by name — with one-click re-run of just those");

        run.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopByUser());
        failuresButton.addActionListener(e -> showFailures());

        param("framework", frameworkKnob);
        param("coverage", coverageSwitch);
        param("covMin", covMinKnob);
    }

    @Override
    protected void primaryAction() {
        passed = 0;
        failed = 0;
        coverage = -1;
        failures.clear();
        onEdt(() -> {
            tallyLcd.setTextColor(org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT);
            tallyLcd.setText("P:0 F:0");
        });
        launch(buildCommand());
    }

    /**
     * A green suite with thin coverage is not green. With COVER on and
     * MIN COV dialed, a measured percentage under the floor fails the
     * run - jacks and LEDs alike. Unmeasured coverage never gates:
     * the device refuses to punish runners it can't read.
     */
    @Override
    protected boolean overallSuccess(int exitCode) {
        if (exitCode != 0) {
            return false;
        }
        int floor = coverageMinimum();
        if (floor > 0 && coverage >= 0 && coverage < floor) {
            double measured = coverage;
            onEdt(() -> {
                tallyLcd.setTextColor(new Color(255, 90, 80));
                tallyLcd.setText("COV " + Math.round(measured) + "% < MIN " + floor);
            });
            return false;
        }
        return true;
    }

    int coverageMinimum() {
        String sel = covMinKnob.getSelectedOption();
        return "off".equals(sel) ? 0 : Integer.parseInt(sel);
    }

    /** The failing test's name if this line reports one, else null. */
    static String failedTestName(String line) {
        for (Pattern p : FAILURE_LINES) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /**
     * The pass/fail counts this line reports, as {passed, failed} with -1
     * for "this line says nothing about that count". Word-based summaries
     * (jest/vitest/mocha) and TAP summaries (node:test) both land here so
     * the tally has ONE tested home.
     */
    static int[] tallyFrom(String line) {
        int p = -1, f = -1;
        Matcher m = PASSED.matcher(line);
        if (m.find()) {
            p = Numbers.intOrZero(m.group(1));
        }
        m = FAILED.matcher(line);
        if (m.find()) {
            f = Numbers.intOrZero(m.group(1));
        }
        m = TAP_PASS.matcher(line);
        if (m.matches()) {
            p = Numbers.intOrZero(m.group(1));
        }
        m = TAP_FAIL.matcher(line);
        if (m.matches()) {
            f = Numbers.intOrZero(m.group(1));
        }
        return new int[]{p, f};
    }

    /** The coverage percentage if this line carries one, else -1. */
    static double coveragePercent(String line) {
        for (Pattern p : COVERAGE_LINES) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        }
        return -1;
    }

    /**
     * A failure NAME escaped for the regex name filters the runners
     * accept (jest/vitest/bun {@code -t}, node
     * {@code --test-name-pattern}, go {@code -run}, forge
     * {@code --match-test}, deno's {@code /.../} filter form). The
     * names arrive verbatim from test output, and real test names
     * carry regex metacharacters constantly — "applies discount
     * (10%)", "totals $10.00", "[edge] handles null". Passed RAW, the
     * name becomes a DIFFERENT regex: measured live on node 22,
     * {@code --test-name-pattern 'applies discount (10%)'} skipped
     * the failing test of that exact name and reported PASS — a
     * re-run-failed that answers green while the failure never ran.
     * {@link Pattern#quote} is not the fix: its {@code \Q...\E}
     * envelope is Java-only, and these engines are JS regex
     * (jest/vitest/bun/node/deno), RE2 (go) and Rust regex (forge).
     * Backslash-escaping each metacharacter is the one dialect all of
     * them share.
     */
    public static String regexLiteral(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * The re-run command for exactly the named failures, per runner -
     * null when the runner has no name filter worth trusting.
     */
    static List<String> rerunFailedCommand(String framework, List<String> names) {
        if (names.isEmpty()) {
            return null;
        }
        String alternation = names.stream().map(TestDevice::regexLiteral)
                .collect(java.util.stream.Collectors.joining("|"));
        return switch (framework) {
            case "jest" -> List.of("npx", "jest", "-t", alternation);
            // node:test filters by regex over the test name (node 18.13+)
            case "node" -> List.of("node", "--test", "--test-name-pattern", alternation);
            case "bun" -> List.of("bun", "test", "-t", alternation);
            // deno's --filter is a SUBSTRING match unless wrapped in
            // slashes, so the bare "a|b" join could never match two
            // names — the /.../ form makes it the regex the join needs.
            // "/" inside a NAME is additionally escaped here (it would
            // terminate the wrapper; JS regex accepts \/ — go's RE2
            // rejects it, so the shared escaper must not learn it)
            case "deno" -> List.of("deno", "test", "--filter",
                    "/" + alternation.replace("/", "\\/") + "/");
            case "vitest" -> List.of("npx", "vitest", "run", "-t", alternation);
            case "pytest" -> {
                List<String> cmd = new ArrayList<>(List.of("python3", "-m", "pytest"));
                cmd.addAll(names); // node ids re-run directly
                yield cmd;
            }
            case "cargo" -> {
                List<String> cmd = new ArrayList<>(List.of("cargo", "test"));
                cmd.addAll(names);
                yield cmd;
            }
            case "go" -> List.of("go", "test", "./...", "-run", alternation);
            case "forge" -> List.of("forge", "test", "--match-test", alternation);
            default -> null;
        };
    }

    private void showFailures() {
        java.util.List<String> snapshot = new ArrayList<>(failures);
        javax.swing.JDialog dialog = new javax.swing.JDialog(
                (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this),
                "VERITAS — failing tests", false);
        javax.swing.JList<String> list = new javax.swing.JList<>(
                snapshot.toArray(new String[0]));
        list.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        javax.swing.JPanel south = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        javax.swing.JButton rerun = new javax.swing.JButton(
                "Re-run failed (" + snapshot.size() + ")");
        rerun.setEnabled(rerunFailedCommand(effectiveFramework(), snapshot) != null);
        rerun.addActionListener(ev -> {
            List<String> cmd = rerunFailedCommand(effectiveFramework(), snapshot);
            if (cmd != null) {
                dialog.setVisible(false);
                passed = 0;
                failed = 0;
                failures.clear();
                launch(cmd);
            }
        });
        south.add(rerun);
        if (snapshot.isEmpty()) {
            south.add(new javax.swing.JLabel("Nothing failing — run the suite first."));
        }
        dialog.add(new javax.swing.JScrollPane(list), java.awt.BorderLayout.CENTER);
        dialog.add(south, java.awt.BorderLayout.SOUTH);
        dialog.setSize(560, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** AUTO resolves to the project's "test" script, else its test framework dependency. */
    private String effectiveFramework() {
        String fw = frameworkKnob.getSelectedOption();
        if (!"auto".equals(fw)) {
            return fw;
        }
        ProjectInspector.ProjectKind kind = effectiveKind();
        switch (kind) {
            case BUN: return "bun";
            case DENO: return "deno";
            case RUST: return "cargo";
            case FOUNDRY: return "forge";
            case ELIXIR: return "mix";
            case ERLANG: return "rebar3";
            case GLEAM: return "gleam";
            case JULIA: return "julia";
            case NIM: return "nim";
            case DLANG: return "dlang";
            case RACKET: return "racket";
            case ELM: return "elm";
            case PURESCRIPT: return "purescript";
            case VLANG: return "vlang";
            case CAIRO: return "cairo";
            case MOVE: return "move";
            case AIKEN: return "aiken";
            // CLARITY and TACT tests ride the npm harness beside the
            // manifest (vitest/simnet, jest/@ton/sandbox) — fall through
            // to the script/dependency detection below
            case FORTRAN: return "fortran";
            case ADA: return "ada"; // no universal Ada test verb: greys
            case RESCRIPT: return "rescript"; // build-only: no test runner
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
            case "bun" -> ProjectInspector.ProjectKind.BUN;
            case "deno" -> ProjectInspector.ProjectKind.DENO;
            case "cargo" -> ProjectInspector.ProjectKind.RUST;
            case "forge" -> ProjectInspector.ProjectKind.FOUNDRY;
            case "mix" -> ProjectInspector.ProjectKind.ELIXIR;
            case "rebar3" -> ProjectInspector.ProjectKind.ERLANG;
            case "gleam" -> ProjectInspector.ProjectKind.GLEAM;
            case "julia" -> ProjectInspector.ProjectKind.JULIA;
            case "nim" -> ProjectInspector.ProjectKind.NIM;
            case "dlang" -> ProjectInspector.ProjectKind.DLANG;
            case "racket" -> ProjectInspector.ProjectKind.RACKET;
            case "elm" -> ProjectInspector.ProjectKind.ELM;
            case "purescript" -> ProjectInspector.ProjectKind.PURESCRIPT;
            case "vlang" -> ProjectInspector.ProjectKind.VLANG;
            case "cairo" -> ProjectInspector.ProjectKind.CAIRO;
            case "move" -> ProjectInspector.ProjectKind.MOVE;
            case "aiken" -> ProjectInspector.ProjectKind.AIKEN;
            case "fortran" -> ProjectInspector.ProjectKind.FORTRAN;
            case "ada" -> ProjectInspector.ProjectKind.ADA;
            case "rescript" -> ProjectInspector.ProjectKind.RESCRIPT;
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
            case "bun" -> cmd.addAll(List.of("bun", "test"));
            case "deno" -> cmd.addAll(List.of("deno", "test"));
            case "cargo" -> cmd.addAll(List.of("cargo", "test"));
            case "forge" -> cmd.addAll(List.of("forge", "test"));
            case "mix" -> cmd.addAll(List.of("mix", "test"));
            case "rebar3" -> cmd.addAll(List.of("rebar3", "eunit"));
            case "gleam" -> cmd.addAll(List.of("gleam", "test"));
            case "julia" -> cmd.addAll(List.of("julia", "--project=.", "-e", "using Pkg; Pkg.test()"));
            case "nim" -> cmd.addAll(List.of("nimble", "test"));
            case "dlang" -> cmd.addAll(List.of("dub", "test"));
            case "racket" -> cmd.addAll(List.of("raco", "test", "."));
            case "elm" -> cmd.addAll(List.of("npx", "elm-test"));
            case "purescript" -> cmd.addAll(List.of("spago", "test"));
            case "vlang" -> cmd.addAll(List.of("v", "test", "."));
            case "cairo" -> cmd.addAll(List.of("scarb", "test"));
            case "move" -> cmd.addAll(ProjectInspector.moveTestCommand(commandDir()));
            case "aiken" -> cmd.addAll(List.of("aiken", "check")); // check compiles AND runs the declared tests — incl. the `fail` ones
            case "fortran" -> cmd.addAll(List.of("fpm", "test"));
            case "ada" -> { } // Alire has no universal test verb — VERITAS greys
            // ReScript has no standard test runner — leave cmd empty (VERITAS greys)
            case "rescript" -> { }
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
        int[] tally = tallyFrom(line);
        if (tally[0] >= 0) {
            passed = tally[0];
        }
        if (tally[1] >= 0) {
            failed = tally[1];
        }
        String failure = failedTestName(line);
        if (failure != null && failures.size() < 500) {
            failures.add(failure);
        }
        double cov = coveragePercent(line);
        if (cov >= 0) {
            coverage = cov;
        }
        if (passed + failed > 0) {
            final int p = passed, f = failed;
            final double c = coverage;
            onEdt(() -> {
                tallyLcd.setTextColor(f > 0 ? new Color(255, 90, 80)
                        : org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT);
                tallyLcd.setText("P:" + p + " F:" + f
                        + (c >= 0 ? " C:" + Math.round(c) + "%" : ""));
            });
        }
    }
}
