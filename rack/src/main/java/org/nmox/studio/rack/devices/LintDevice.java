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
 *
 * <p><b>Why slither lives here (ledger 12, v2.155.0).</b> slither is a
 * static analyzer that reports findings with a severity, which is exactly
 * PURITY's job: E/W counts on the LCD, CLEAN when spotless, findings on the
 * diagnostics bus. TYPEGUARD already carries the Foundry project's solhint
 * lane, but TYPEGUARD has no selector — giving it one would add a control
 * to a faceplate whose saved patches carry none — while PURITY's LINTER
 * knob is the rack's established place to choose an analyzer, and it grows
 * by APPENDING (positions persist by index). So slither is position 8 on
 * that knob, and AUTO resolves to it on a Foundry project, where eslint was
 * never going to find anything. solhint stays TYPEGUARD's: the two answer
 * different questions (style and best-practice lint versus vulnerability
 * detectors) and a Foundry rack can run both.
 */
public class LintDevice extends CommandDevice {

    // "biome"/"auto"/"deno"/"clippy"/"slither" appended, never inserted: knob
    // positions persist by index in saved patches (the v1.59.0 law). New
    // devices default to auto.
    private static final String[] LINTERS = {"eslint", "stylelint", "biome", "auto", "deno", "clippy", "govet", "golangci", "slither"};
    private static final Pattern SUMMARY =
            Pattern.compile("(\\d+)\\s+problems?\\s*\\((\\d+)\\s+errors?,\\s*(\\d+)\\s+warnings?\\)");
    // with and without the fixable suffix: "Found 2 problems" and
    // "Found 2 problems (2 fixable via --fix)"
    private static final Pattern DENO_SUMMARY =
            Pattern.compile("^Found (\\d+) problems?\\b");
    // clippy's per-crate summary, pinned live on cargo 1.95:
    // warning: `rsprobe` (bin "rsprobe") generated 3 warnings (...)
    private static final Pattern CLIPPY_SUMMARY =
            Pattern.compile("generated (\\d+) warnings?\\b");

    /**
     * Test seam for slither's availability. Production asks the IDE's
     * augmented PATH (the same probe every console's grey-honestly path
     * uses); slither is never installed on the user's behalf — it lives in
     * whatever Python environment the user chose, and the IDE must not pick
     * one for them.
     */
    static java.util.function.Predicate<String> toolProbe = CommandDevice::toolOnPath;

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
            // KIND OUTRANKS A STRAY MANIFEST — the same resolution order
            // FormatDevice uses, so PURITY and GLOSS never disagree about
            // whose project this is (a Go module carrying a deno.json for
            // scripts is still a Go module)
            // a Cargo project lints with clippy, the toolchain's own linter
            if (effectiveKind() == ProjectInspector.ProjectKind.RUST) {
                return "clippy";
            }
            // a Go module lints with the community's linter when the
            // project opted in (a .golangci config), else go vet — the
            // toolchain's own correctness checker
            if (effectiveKind() == ProjectInspector.ProjectKind.GO) {
                return ProjectInspector.hasGolangci(projectDir())
                        ? "golangci" : "govet";
            }
            // a Foundry project's static analyzer is slither (ledger 12):
            // eslint over a contracts repo has nothing to read
            if (effectiveKind() == ProjectInspector.ProjectKind.FOUNDRY) {
                return "slither";
            }
            // a Deno workspace lints with the runtime's own linter — no
            // node_modules exists for npx to resolve anything from
            if (ProjectInspector.hasDeno(projectDir())) {
                return "deno";
            }
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
            case "deno" -> cmd.addAll(List.of("deno", "lint"));
            case "clippy" -> cmd.addAll(List.of("cargo", "clippy"));
            case "govet" -> cmd.addAll(List.of("go", "vet", "./..."));
            case "golangci" -> cmd.addAll(List.of("golangci-lint", "run"));
            // `slither .` — crytic-compile recognises the Foundry project
            // (it runs forge build itself) and a plain directory of .sol
            // files alike; the per-run --json report path is added at
            // launch (slitherLaunchCommand), so the tooltip and a CI export
            // show the command a person would type
            case "slither" -> cmd.addAll(List.of("slither", "."));
            default -> cmd.addAll(List.of("npx", "eslint", "."));
        }
        if (fixSwitch.isOn()) {
            if ("govet".equals(linter) || "slither".equals(linter)) {
                // go vet and slither have no autofix; the switch is honest
                // by doing nothing
            } else if ("clippy".equals(linter)) {
                // clippy --fix refuses a dirty working tree by default,
                // and an IDE's tree is dirty by definition mid-edit
                cmd.addAll(List.of("--fix", "--allow-dirty"));
            } else {
                // biome spells autofix --write, the others --fix
                cmd.add("biome".equals(linter) ? "--write" : "--fix");
            }
        }
        return cmd;
    }

    /** The slither argv this run hands the executor: the typed command plus its own report file. */
    List<String> slitherLaunchCommand(java.nio.file.Path report) {
        List<String> cmd = new ArrayList<>(buildCommand());
        cmd.add("--json");
        cmd.add(report.toString());
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
    /** This slither run's private report directory; null when no slither run is live. */
    private volatile java.nio.file.Path slitherReportDir;
    /** The compiler wall appeared in this slither run's output. */
    private volatile boolean slitherCompilerMissing;

    @Override
    protected void onLine(String line) {
        if ("slither".equals(activeLinter)) {
            // the findings come from the report file, not the human text;
            // the one line worth reading is the compiler wall, so the LCD
            // can say why no report arrived
            if (org.nmox.studio.rack.engine.CommandExecutor
                    .looksLikeSolidityCompilerMissing(line)) {
                slitherCompilerMissing = true;
            }
            return;
        }
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
        // deno lint's summary shape, pinned live against deno 2.9.4:
        // "Found 2 problems" (all deno lint findings are errors; a clean
        // run prints only "Checked N files", which the exit-0 path shows)
        Matcher deno = DENO_SUMMARY.matcher(line);
        if (deno.find()) {
            String errors = deno.group(1);
            onEdt(() -> {
                countLcd.setTextColor("0".equals(errors)
                        ? org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT : new Color(255, 90, 80));
                countLcd.setText("E:" + errors + " W:0");
            });
        }
        // clippy findings are warnings (hard errors fail the compile and
        // the exit code drives the FAIL LED before any summary prints)
        Matcher clippy = CLIPPY_SUMMARY.matcher(line);
        if (clippy.find()) {
            String warnings = clippy.group(1);
            onEdt(() -> {
                countLcd.setTextColor("0".equals(warnings)
                        ? org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT : new Color(255, 90, 80));
                countLcd.setText("E:0 W:" + warnings);
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
        slitherCompilerMissing = false;
        onEdt(() -> {
            cleanLed.setOn(false);
            countLcd.setTextColor(RackStyle.LCD_TEXT);
            countLcd.setText("E:- W:-");
        });
        if ("slither".equals(activeLinter)) {
            launchSlither();
            return;
        }
        launch(buildCommand());
    }

    /**
     * The slither lane's own launch. Order is the law: the availability
     * grey comes FIRST (a missing tool spawns nothing and asks nothing), the
     * trust gate rides {@link #launch} before the executor sees the argv,
     * and the report directory exists only for a run that really launched —
     * a declined trust prompt leaves no directory and no armed state behind.
     */
    private void launchSlither() {
        if (!toolProbe.test("slither")) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO SLITHER ON PATH — " + SlitherReport.INSTALL_HINT);
            });
            return;
        }
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("nmox-slither-");
        } catch (java.io.IOException ex) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO SCRATCH DIRECTORY FOR THE SLITHER REPORT");
            });
            return;
        }
        // slither refuses to overwrite a report, so the file must not exist yet
        java.nio.file.Path report = dir.resolve("slither.json");
        slitherReportDir = dir;
        if (!launch(slitherLaunchCommand(report))) {
            slitherReportDir = null;
            deleteReportDir(dir);
        }
    }

    /** Removes a run's private report directory; best effort, it is scratch. */
    private static void deleteReportDir(java.nio.file.Path dir) {
        if (dir == null) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(dir.resolve("slither.json"));
            java.nio.file.Files.deleteIfExists(dir);
        } catch (java.io.IOException | RuntimeException ignored) {
            // scratch under the temp dir; the OS reclaims it
        }
    }

    /** Test seams: arm a parse run and read what it collected. */
    void beginParseForTest() {
        collected.clear();
        currentFile = null;
        activeLinter = effectiveLinter();
        biomeErrors = "0";
        biomeWarnings = "0";
        slitherCompilerMissing = false;
    }

    /** Test seam: the report directory a launched slither run would own. */
    void armSlitherReportForTest(java.nio.file.Path dir) {
        slitherReportDir = dir;
    }

    java.util.List<org.nmox.studio.rack.engine.DiagnosticsBus.Problem> collectedForTest() {
        return new java.util.ArrayList<>(collected);
    }

    String lcdTextForTest() {
        return countLcd.getText();
    }

    String statusTextForTest() {
        return statusLcd.getText();
    }

    java.nio.file.Path slitherReportDirForTest() {
        return slitherReportDir;
    }

    @Override
    protected void onFinished(int exitCode) {
        onEdt(() -> cleanLed.setOn(exitCode == 0));
        if ("slither".equals(activeLinter)) {
            finishSlither(stoppedByUserOrSignal(false, exitCode));
            return;
        }
        // the squiggle/Action Items label names the tool that actually ran
        org.nmox.studio.rack.engine.DiagnosticsBus.publish(
                "biome".equals(activeLinter) ? "biome" : "eslint",
                new java.util.ArrayList<>(collected));
    }

    /**
     * Reads this run's report, publishes the findings under "slither" and
     * clears the scratch directory. A run with no readable report publishes
     * NOTHING: a crash is not an all-clear, and the previous run's squiggles
     * stay true until a real report replaces them.
     */
    private void finishSlither(boolean stopped) {
        java.nio.file.Path dir = slitherReportDir;
        slitherReportDir = null;
        SlitherReport.Result result = SlitherReport.read(
                dir == null ? null : dir.resolve("slither.json"), commandDir());
        deleteReportDir(dir);
        if (result.refusal() != null) {
            if (stopped) {
                // the STOP already reads STOPPED; a killed run has no report
                // by design and needs no second explanation
                return;
            }
            String why = slitherCompilerMissing
                    ? "SLITHER COULD NOT COMPILE — NO forge/solc ON PATH"
                    : result.refusal();
            onEdt(() -> {
                countLcd.setTextColor(RackStyle.LCD_TEXT);
                countLcd.setText("E:- W:-");
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText(why);
            });
            return;
        }
        String lcd = result.lcd();
        boolean clean = result.errors() == 0;
        onEdt(() -> {
            countLcd.setTextColor(clean ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
            countLcd.setText(lcd);
        });
        org.nmox.studio.rack.engine.DiagnosticsBus.publish("slither", result.problems());
    }
}
