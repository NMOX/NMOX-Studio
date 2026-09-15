package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.projectstudio.EnvironmentDoctor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PURITY's slither lane (ledger 12, v2.155.0): the Solidity static analyzer
 * as a rack lane. The laws it holds — a missing slither greys with the
 * Doctor's own install hint and spawns nothing; Workspace Trust is asked
 * BEFORE the executor sees the argv and a decline leaves no scratch state;
 * the report's detector impact decides error versus warning; findings ride
 * the diagnostics bus under "slither".
 *
 * <p>The JSON fixture {@code slither-vault.json} is a REAL capture: slither
 * 0.11.6 ({@code slither . --json <file>}) run on a two-function Foundry
 * vault with a reentrancy bug (withdraw sends before zeroing the balance).
 * The only edit is the capture machine's absolute project path, rewritten
 * to {@code /home/dev/vault} so the fixture carries no local path — which
 * also makes the parser take its relative-path branch, the one a report
 * produced on another machine needs.
 */
class SlitherLaneTest {

    @TempDir
    Path dir;

    private final Predicate<File> originalTrust = CommandDevice.trustCheck;
    private final Predicate<String> originalProbe = LintDevice.toolProbe;
    private Rack rack;

    @BeforeEach
    void foundryProject() throws IOException {
        Files.writeString(dir.resolve("foundry.toml"), "[profile.default]\nsrc = \"src\"\n");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/Vault.sol"), vaultSource());
        rack = new Rack();
        rack.setProjectDir(dir.toFile());
    }

    @AfterEach
    void restore() {
        CommandDevice.trustCheck = originalTrust;
        LintDevice.toolProbe = originalProbe;
        rack.shutdown();
    }

    private LintDevice mounted(Map<String, String> state) {
        LintDevice lint = new LintDevice();
        rack.addDevice(lint);
        lint.applyState(state);
        return lint;
    }

    private static void settle() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    private static String fixture() throws IOException {
        try (InputStream in = SlitherLaneTest.class.getResourceAsStream("slither-vault.json")) {
            assertThat(in).as("the captured slither report ships as a test resource").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The contract the capture ran on, line for line (the report's line numbers point here). */
    private static String vaultSource() {
        return """
                // SPDX-License-Identifier: MIT
                pragma solidity ^0.8.20;

                contract Vault {
                    mapping(address => uint256) public balances;

                    function deposit() external payable {
                        balances[msg.sender] += msg.value;
                    }

                    function withdraw() external {
                        uint256 amount = balances[msg.sender];
                        (bool ok, ) = msg.sender.call{value: amount}("");
                        require(ok, "send failed");
                        balances[msg.sender] = 0;
                    }
                }
                """;
    }

    // ---------------- lane selection ----------------

    @Test
    @DisplayName("AUTO on a Foundry project runs slither; FIX adds nothing (slither has no autofix)")
    void autoOnFoundryIsSlither() {
        assertThat(mounted(Map.of()).buildCommand()).containsExactly("slither", ".");
        assertThat(mounted(Map.of("fix", "true")).buildCommand())
                .as("a FIX switch must not invent a flag slither does not have")
                .containsExactly("slither", ".");
    }

    @Test
    @DisplayName("slither is APPENDED at LINTER position 8 — every older position still means what it meant")
    void slitherIsAppendedNotInserted() throws IOException {
        // a plain Node project, so AUTO cannot be what answers
        Files.delete(dir.resolve("foundry.toml"));
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(mounted(Map.of("linter", "8")).buildCommand()).containsExactly("slither", ".");
        assertThat(mounted(Map.of("linter", "0")).buildCommand()).containsExactly("npx", "eslint", ".");
        assertThat(mounted(Map.of("linter", "2")).buildCommand())
                .containsExactly("npx", "@biomejs/biome", "lint", ".");
        assertThat(mounted(Map.of("linter", "7")).buildCommand()).containsExactly("golangci-lint", "run");
        assertThat(mounted(Map.of()).buildCommand())
                .as("AUTO outside Foundry is untouched").containsExactly("npx", "eslint", ".");
    }

    @Test
    @DisplayName("the launched argv adds this run's own --json report file to the typed command")
    void launchArgvCarriesTheReportFile() {
        Path report = dir.resolve("scratch/slither.json");
        assertThat(mounted(Map.of()).slitherLaunchCommand(report))
                .containsExactly("slither", ".", "--json", report.toString());
    }

    // ---------------- the gates ----------------

    @Test
    @DisplayName("no slither on PATH: greys with the Doctor's exact hint and spawns nothing — trust is never even asked")
    void missingToolGreysWithTheDoctorHint() throws Exception {
        LintDevice.toolProbe = tool -> false;
        AtomicBoolean trustAsked = new AtomicBoolean();
        CommandDevice.trustCheck = f -> {
            trustAsked.set(true);
            return false;
        };
        LintDevice lint = mounted(Map.of());
        lint.primaryAction();
        settle();

        String doctorHint = EnvironmentDoctor.checklist().stream()
                .filter(row -> "slither".equals(row[0]))
                .map(row -> row[2])
                .findFirst().orElseThrow();
        assertThat(SlitherReport.INSTALL_HINT).as("one install hint, the Doctor's").isEqualTo(doctorHint);
        assertThat(lint.statusTextForTest()).contains("NO SLITHER ON PATH").contains(doctorHint);
        assertThat(trustAsked).as("a missing tool must stop before the spawn path").isFalse();
        assertThat(lint.isLive()).isFalse();
        assertThat(lint.slitherReportDirForTest()).isNull();
    }

    @Test
    @DisplayName("Keep Safe: trust is asked before any process exists, and the decline leaves no report directory behind")
    void trustDeclineSpawnsNothingAndLeavesNoState() throws Exception {
        LintDevice.toolProbe = tool -> true;
        AtomicReference<LintDevice> device = new AtomicReference<>();
        AtomicBoolean liveWhenAsked = new AtomicBoolean(true);
        AtomicReference<Path> dirWhenAsked = new AtomicReference<>();
        CommandDevice.trustCheck = f -> {
            liveWhenAsked.set(device.get().isLive());
            dirWhenAsked.set(device.get().slitherReportDirForTest());
            return false; // the Keep Safe answer
        };
        LintDevice lint = mounted(Map.of());
        device.set(lint);
        lint.primaryAction();
        settle();

        assertThat(liveWhenAsked)
                .as("the trust question must come BEFORE the executor is handed anything")
                .isFalse();
        assertThat(lint.statusTextForTest()).isEqualTo("UNTRUSTED WORKSPACE — EXECUTION REFUSED");
        assertThat(lint.isLive()).isFalse();
        assertThat(lint.slitherReportDirForTest()).as("no armed report after a decline").isNull();
        assertThat(dirWhenAsked.get()).as("the run had a scratch directory while asking").isNotNull();
        assertThat(Files.exists(dirWhenAsked.get()))
                .as("a declined run must not leave its scratch directory behind").isFalse();
    }

    // ---------------- the report ----------------

    @Test
    @DisplayName("the real capture maps: High reentrancy is an error at line 11, Informational findings are warnings")
    void realCaptureMapsImpactToSeverity() throws IOException {
        SlitherReport.Result result = SlitherReport.parse(fixture(), dir.toFile());

        assertThat(result.refusal()).isNull();
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.warnings()).isEqualTo(2);
        assertThat(result.lcd()).isEqualTo("E:1 W:2");
        List<DiagnosticsBus.Problem> problems = result.problems();
        assertThat(problems).hasSize(3);
        File vault = dir.resolve("src/Vault.sol").toFile();
        assertThat(problems).allSatisfy(p -> assertThat(p.file()).isEqualTo(vault));

        DiagnosticsBus.Problem reentrancy = problems.get(0);
        assertThat(reentrancy.error()).isTrue();
        assertThat(reentrancy.line()).isEqualTo(11);
        assertThat(reentrancy.message())
                .isEqualTo("reentrancy-eth (High): Reentrancy in Vault.withdraw() (src/Vault.sol#11-16)");

        assertThat(problems.get(1).message()).startsWith("solc-version (Informational): ");
        assertThat(problems.get(1).line()).isEqualTo(2);
        assertThat(problems.get(1).error()).isFalse();
        assertThat(problems.get(2).message()).startsWith("low-level-calls (Informational): ");
        assertThat(problems.get(2).error()).isFalse();
    }

    @Test
    @DisplayName("the impact ladder: High and Medium are errors; Low, Informational, Optimization and unknown labels are warnings")
    void impactLadder() {
        assertThat(SlitherReport.isErrorImpact("High")).isTrue();
        assertThat(SlitherReport.isErrorImpact("Medium")).isTrue();
        assertThat(SlitherReport.isErrorImpact("medium")).isTrue();
        assertThat(SlitherReport.isErrorImpact("Low")).isFalse();
        assertThat(SlitherReport.isErrorImpact("Informational")).isFalse();
        assertThat(SlitherReport.isErrorImpact("Optimization")).isFalse();
        assertThat(SlitherReport.isErrorImpact("Catastrophic")).isFalse();
        assertThat(SlitherReport.isErrorImpact(null)).isFalse();
    }

    @Test
    @DisplayName("a finding's first project element wins over a dependency element; unresolvable findings still count")
    void dependencyElementsYieldToProjectSource() {
        String json = """
                {"success": true, "error": null, "results": {"detectors": [
                  {"check": "arbitrary-send-eth", "impact": "Medium", "description": "sends eth\\nmore",
                   "elements": [
                     {"source_mapping": {"filename_relative": "lib/Dep.sol", "is_dependency": true, "lines": [3]}},
                     {"source_mapping": {"filename_relative": "src/Vault.sol", "is_dependency": false, "lines": [13, 14]}}
                   ]},
                  {"check": "pragma", "impact": "Low", "description": "nowhere",
                   "elements": [{"source_mapping": {"filename_relative": "src/Gone.sol", "lines": [1]}}]}
                ]}}
                """;
        SlitherReport.Result result = SlitherReport.parse(json, dir.toFile());
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.warnings()).as("a finding with no file on disk is still counted").isEqualTo(1);
        assertThat(result.problems()).singleElement().satisfies(p -> {
            assertThat(p.file()).isEqualTo(dir.resolve("src/Vault.sol").toFile());
            assertThat(p.line()).isEqualTo(13);
            assertThat(p.message()).isEqualTo("arbitrary-send-eth (Medium): sends eth");
        });
    }

    @Test
    @DisplayName("refusals speak and publish nothing: slither's own failure, non-JSON, a missing report, an oversize report")
    void refusals() throws IOException {
        SlitherReport.Result failed = SlitherReport.parse(
                "{\"success\": false, \"error\": \"Invalid compilation: forge build failed\\ntrace\", \"results\": {}}",
                dir.toFile());
        assertThat(failed.refusal()).isEqualTo("SLITHER FAILED — Invalid compilation: forge build failed");
        assertThat(failed.problems()).isEmpty();

        assertThat(SlitherReport.parse("Traceback (most recent call last):", dir.toFile()).refusal())
                .startsWith("SLITHER REPORT IS NOT JSON");
        assertThat(SlitherReport.read(dir.resolve("absent.json"), dir.toFile()).refusal())
                .startsWith("NO SLITHER REPORT");

        Path huge = dir.resolve("huge.json");
        byte[] over = new byte[(int) SlitherReport.MAX_REPORT_BYTES + 1];
        java.util.Arrays.fill(over, (byte) ' ');
        Files.write(huge, over);
        assertThat(SlitherReport.read(huge, dir.toFile()).refusal())
                .as("past the ceiling the report is refused, never truncated into a parse")
                .isEqualTo("SLITHER REPORT OVER 8 MB — NOT PARSED");
    }

    @Test
    @DisplayName("a clean report publishes an empty batch — the all-clear that clears old squiggles")
    void cleanReportIsAnAllClear() {
        SlitherReport.Result clean = SlitherReport.parse(
                "{\"success\": true, \"error\": null, \"results\": {}}", dir.toFile());
        assertThat(clean.refusal()).isNull();
        assertThat(clean.problems()).isEmpty();
        assertThat(clean.lcd()).isEqualTo("E:0 W:0");
    }

    // ---------------- the finish: bus, LCD, scratch cleanup ----------------

    @Test
    @DisplayName("onFinished reads the run's report, publishes under slither, paints E/W, and deletes the scratch directory")
    void finishPublishesAndCleansUp() throws Exception {
        List<List<DiagnosticsBus.Problem>> slitherBatches = new CopyOnWriteArrayList<>();
        DiagnosticsBus.Listener listener = (tool, problems) -> {
            if ("slither".equals(tool)) {
                slitherBatches.add(problems);
            }
        };
        LintDevice lint = mounted(Map.of());
        lint.beginParseForTest();
        Path scratch = Files.createTempDirectory(dir, "run-");
        Files.writeString(scratch.resolve("slither.json"), fixture());
        lint.armSlitherReportForTest(scratch);
        DiagnosticsBus.addListener(listener);
        try {
            slitherBatches.clear(); // drop the late-subscriber replay of earlier tests
            lint.onFinished(255); // slither exits 255 when it has findings (measured)
            settle();
        } finally {
            DiagnosticsBus.removeListener(listener);
        }

        assertThat(slitherBatches).singleElement().satisfies(batch -> assertThat(batch).hasSize(3));
        assertThat(lint.lcdTextForTest()).isEqualTo("E:1 W:2");
        assertThat(Files.exists(scratch)).as("the report's scratch directory is removed").isFalse();
        assertThat(lint.slitherReportDirForTest()).isNull();
    }

    @Test
    @DisplayName("no report after a missing-compiler traceback: the LCD names the wall and nothing is published")
    void compilerWallExplainsTheMissingReport() throws Exception {
        List<String> tools = new CopyOnWriteArrayList<>();
        DiagnosticsBus.Listener listener = (tool, problems) -> tools.add(tool);
        LintDevice lint = mounted(Map.of());
        lint.beginParseForTest();
        lint.armSlitherReportForTest(Files.createTempDirectory(dir, "run-"));
        lint.onLine("FileNotFoundError: [Errno 2] No such file or directory: 'forge'");
        DiagnosticsBus.addListener(listener);
        try {
            tools.clear();
            lint.onFinished(1);
            settle();
        } finally {
            DiagnosticsBus.removeListener(listener);
        }
        assertThat(lint.statusTextForTest()).isEqualTo("SLITHER COULD NOT COMPILE — NO forge/solc ON PATH");
        assertThat(tools).as("a failed run is not an all-clear").doesNotContain("slither");
    }
}
