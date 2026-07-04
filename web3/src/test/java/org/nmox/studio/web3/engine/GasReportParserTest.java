package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The forge gas-report table parser — box-drawing and plain-pipe
 * variants — and the .gas-snapshot line parser. The fixtures mirror
 * real forge output shapes; the bias is skip-when-unsure.
 */
class GasReportParserTest {

    private static final String BOX_REPORT = """
            ╭----------------------------------+-----------------+-------+--------+-------+---------╮
            | src/Counter.sol:Counter contract |                 |       |        |       |         |
            +==================================================================================+
            | Deployment Cost                  | Deployment Size |       |        |       |         |
            |----------------------------------+-----------------+-------+--------+-------+---------|
            | 106715                           | 277             |       |        |       |         |
            |----------------------------------+-----------------+-------+--------+-------+---------|
            | Function Name                    | min             | avg   | median | max   | # calls |
            |----------------------------------+-----------------+-------+--------+-------+---------|
            | increment                        | 43404           | 43404 | 43404  | 43404 | 1       |
            |----------------------------------+-----------------+-------+--------+-------+---------|
            | number                           | 2325            | 2325  | 2325   | 2325  | 2       |
            ╰----------------------------------+-----------------+-------+--------+-------+---------╯
            """;

    private static final String UNICODE_BOX_REPORT = """
            ╭────────────────────────┬─────────────────┬───────┬────────┬───────┬─────────╮
            │ src/Token.sol:Token contract │           │       │        │       │         │
            ╞════════════════════════╪═════════════════╪═══════╪════════╪═══════╪═════════╡
            │ Deployment Cost        │ Deployment Size │       │        │       │         │
            ├────────────────────────┼─────────────────┼───────┼────────┼───────┼─────────┤
            │ 641221                 │ 3554            │       │        │       │         │
            ├────────────────────────┼─────────────────┼───────┼────────┼───────┼─────────┤
            │ Function Name          │ min             │ avg   │ median │ max   │ # calls │
            ├────────────────────────┼─────────────────┼───────┼────────┼───────┼─────────┤
            │ transfer               │ 29934           │ 34620 │ 34620  │ 39306 │ 2       │
            ╰────────────────────────┴─────────────────┴───────┴────────┴───────┴─────────╯
            """;

    @Test
    @DisplayName("a plain-pipe report parses its function rows under the right contract")
    void plainPipeReport() {
        List<GasReportParser.FunctionGas> rows = GasReportParser.parseGasReport(BOX_REPORT);
        assertThat(rows).containsExactly(
                new GasReportParser.FunctionGas("Counter", "increment",
                        43404, 43404, 43404, 43404, 1),
                new GasReportParser.FunctionGas("Counter", "number",
                        2325, 2325, 2325, 2325, 2));
    }

    @Test
    @DisplayName("the box-drawing (│) variant parses identically")
    void boxDrawingReport() {
        List<GasReportParser.FunctionGas> rows =
                GasReportParser.parseGasReport(UNICODE_BOX_REPORT);
        assertThat(rows).containsExactly(
                new GasReportParser.FunctionGas("Token", "transfer",
                        29934, 34620, 34620, 39306, 2));
    }

    @Test
    @DisplayName("two contract tables in one output keep their rows apart")
    void twoContracts() {
        List<GasReportParser.FunctionGas> rows =
                GasReportParser.parseGasReport(BOX_REPORT + "\n" + UNICODE_BOX_REPORT);
        assertThat(rows).extracting(GasReportParser.FunctionGas::contract)
                .containsExactly("Counter", "Counter", "Token");
    }

    @Test
    @DisplayName("deployment-cost value rows (numeric first cell) are not function rows")
    void deploymentCostRowsSkipped() {
        assertThat(GasReportParser.parseGasReport(BOX_REPORT))
                .extracting(GasReportParser.FunctionGas::function)
                .doesNotContain("106715", "Deployment Cost");
    }

    @Test
    @DisplayName("rows before any contract header are skipped — unsure means skip")
    void rowsWithoutContractSkipped() {
        String orphan = "| doThing | 1 | 2 | 2 | 3 | 4 |";
        assertThat(GasReportParser.parseGasReport(orphan)).isEmpty();
    }

    @Test
    @DisplayName("prose, blank lines, and test summaries around the table don't confuse it")
    void surroundingNoise() {
        String noisy = "Ran 2 tests for test/Counter.t.sol:CounterTest\n"
                + "[PASS] test_Increment() (gas: 31303)\n"
                + BOX_REPORT
                + "\nSuite result: ok. 2 passed; 0 failed; 0 skipped\n";
        assertThat(GasReportParser.parseGasReport(noisy)).hasSize(2);
    }

    @Test
    @DisplayName("null and empty output parse to no rows, never throw")
    void degenerateInput() {
        assertThat(GasReportParser.parseGasReport(null)).isEmpty();
        assertThat(GasReportParser.parseGasReport("")).isEmpty();
        assertThat(GasReportParser.parseGasReport("total garbage ╭│╰ everywhere")).isEmpty();
    }

    @Test
    @DisplayName("numbers with comma or underscore grouping still parse")
    void groupedNumbers() {
        String report = "| src/Big.sol:Big contract |\n"
                + "| mint | 1,234 | 2_000 | 2000 | 3000 | 10 |";
        assertThat(GasReportParser.parseGasReport(report)).containsExactly(
                new GasReportParser.FunctionGas("Big", "mint", 1234, 2000, 2000, 3000, 10));
    }

    // ---- .gas-snapshot -----------------------------------------------------

    @Test
    @DisplayName(".gas-snapshot lines parse test name and gas")
    void snapshotLines() {
        String snapshot = """
                CounterTest:test_Increment() (gas: 31303)
                CounterTest:testFuzz_SetNumber(uint256) (runs: 256, μ: 30822, ~: 31288)
                CounterTest:test_Decrement() (gas: 31351)
                """;
        assertThat(GasReportParser.parseSnapshot(snapshot)).containsExactly(
                new GasReportParser.SnapshotEntry("CounterTest:test_Increment()", 31303),
                new GasReportParser.SnapshotEntry("CounterTest:test_Decrement()", 31351));
    }

    @Test
    @DisplayName("snapshot garbage — no marker, no number, empty — is skipped")
    void snapshotGarbage() {
        assertThat(GasReportParser.parseSnapshot(null)).isEmpty();
        assertThat(GasReportParser.parseSnapshot("")).isEmpty();
        assertThat(GasReportParser.parseSnapshot("(gas: 5)")).isEmpty(); // no test name
        assertThat(GasReportParser.parseSnapshot("test_X() (gas: lots)")).isEmpty();
        assertThat(GasReportParser.parseSnapshot("test_X() gas: 5")).isEmpty();
    }
}
