package org.nmox.studio.rack.devices;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The testing-rigor contract: VERITAS names its failures and re-runs
 * exactly those; coverage and throughput become gates that close the
 * OK jack, never silently pass.
 */
class TestingRigorTest {

    @Test
    @DisplayName("Failure lines are recognized across runner families")
    void failureNamesParse() {
        assertThat(TestDevice.failedTestName("  ✕ renders the header (23 ms)"))
                .isEqualTo("renders the header");
        assertThat(TestDevice.failedTestName("FAILED tests/test_api.py::test_login"))
                .isEqualTo("tests/test_api.py::test_login");
        assertThat(TestDevice.failedTestName("test parser::handles_empty ... FAILED"))
                .isEqualTo("parser::handles_empty");
        assertThat(TestDevice.failedTestName("--- FAIL: TestCheckout (0.03s)"))
                .isEqualTo("TestCheckout");
        assertThat(TestDevice.failedTestName("  ✓ renders the footer")).isNull();
        assertThat(TestDevice.failedTestName("ordinary output")).isNull();

        // node:test speaks TAP — "not ok N - name", with an optional
        // trailing "# ..." directive that is metadata, not the name
        // (v1.252.0: a `node --test` project showed NO failure names)
        assertThat(TestDevice.failedTestName("not ok 4 - SAVE10 takes ten percent off"))
                .isEqualTo("SAVE10 takes ten percent off");
        assertThat(TestDevice.failedTestName("not ok 2 - flaky case # TODO"))
                .isEqualTo("flaky case");
        assertThat(TestDevice.failedTestName("ok 3 - total multiplies price by qty"))
                .as("a passing TAP line is not a failure").isNull();
    }

    @Test
    @DisplayName("TAP summary counts drive the tally (node:test says pass/fail, not passed/failed)")
    void tapCountsParse() {
        // the v1.252.0 find: node --test prints "# pass 3" / "# fail 1",
        // which the word-based patterns never matched — VERITAS showed
        // P:0 F:0 on a real failing suite while the verdict said FAIL
        assertThat(TestDevice.tallyFrom("# pass 3")).containsExactly(3, -1);
        assertThat(TestDevice.tallyFrom("# fail 1")).containsExactly(-1, 1);
        assertThat(TestDevice.tallyFrom("Tests:  2 failed, 5 passed"))
                .as("the jest-style words still work").containsExactly(5, 2);
        assertThat(TestDevice.tallyFrom("# duration_ms 61.7")).containsExactly(-1, -1);
    }

    @Test
    @DisplayName("Coverage summaries are read from istanbul, pytest-cov, and go")
    void coverageParses() {
        assertThat(TestDevice.coveragePercent("Lines        : 85.71% ( 42/49 )"))
                .isEqualTo(85.71);
        assertThat(TestDevice.coveragePercent("TOTAL     120     18    85%"))
                .isEqualTo(85.0);
        assertThat(TestDevice.coveragePercent("ok  \tapp/api\t0.5s\tcoverage: 91.2% of statements"))
                .isEqualTo(91.2);
        assertThat(TestDevice.coveragePercent("no coverage here")).isEqualTo(-1);
    }

    @Test
    @DisplayName("Re-run failed builds the right filter per runner")
    void rerunCommands() {
        assertThat(TestDevice.rerunFailedCommand("node", List.of("SAVE10 takes ten percent off")))
                .as("node:test filters by name regex")
                .containsExactly("node", "--test", "--test-name-pattern",
                        "SAVE10 takes ten percent off");
        assertThat(TestDevice.rerunFailedCommand("jest", List.of("a", "b")))
                .containsExactly("npx", "jest", "-t", "a|b");
        assertThat(TestDevice.rerunFailedCommand("vitest", List.of("x")))
                .containsExactly("npx", "vitest", "run", "-t", "x");
        assertThat(TestDevice.rerunFailedCommand("pytest",
                List.of("tests/test_api.py::test_login")))
                .containsExactly("python3", "-m", "pytest", "tests/test_api.py::test_login");
        assertThat(TestDevice.rerunFailedCommand("cargo", List.of("parser::handles_empty")))
                .containsExactly("cargo", "test", "parser::handles_empty");
        assertThat(TestDevice.rerunFailedCommand("go", List.of("TestA", "TestB")))
                .containsExactly("go", "test", "./...", "-run", "TestA|TestB");
        assertThat(TestDevice.rerunFailedCommand("mvn", List.of("t"))).isNull();
        assertThat(TestDevice.rerunFailedCommand("jest", List.of())).isNull();
    }

    @Test
    @DisplayName("VERITAS coverage floor gates a clean exit; unmeasured never gates")
    void coverageFloorGates() {
        TestDevice veritas = new TestDevice();
        veritas.applyState(Map.of("covMin", "4")); // "80"
        assertThat(veritas.coverageMinimum()).isEqualTo(80);

        assertThat(veritas.overallSuccess(0))
                .as("floor set but nothing measured: pass").isTrue();
        veritas.onLine("Lines : 62.0% ( 62/100 )");
        assertThat(veritas.overallSuccess(0)).as("62 < 80: gate closed").isFalse();
        veritas.onLine("Lines : 91.0% ( 91/100 )");
        assertThat(veritas.overallSuccess(0)).as("91 >= 80: pass").isTrue();
        assertThat(veritas.overallSuccess(1)).as("exit 1 always fails").isFalse();
    }

    @Test
    @DisplayName("GAUNTLET throughput floor gates a clean exit")
    void benchFloorGates() {
        BenchDevice gauntlet = new BenchDevice();
        assertThat(gauntlet.overallSuccess(0)).as("floor off").isTrue();

        gauntlet.applyState(Map.of("min", "3")); // "1k"
        gauntlet.onLine("2k requests in 10.0s, 4 MB read");   // 200 r/s
        assertThat(gauntlet.overallSuccess(0)).as("200 < 1000").isFalse();
        gauntlet.onLine("120k requests in 10.0s, 24 MB read"); // 12000 r/s
        assertThat(gauntlet.overallSuccess(0)).as("12000 >= 1000").isTrue();
    }
}
