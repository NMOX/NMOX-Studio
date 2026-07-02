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
