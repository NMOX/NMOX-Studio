package org.nmox.studio.ui.actions;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.ContractKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Contract Kit dialog's pure helpers: the outcome report (✓ for
 * files the scaffold changed, – for files it honestly left alone, with
 * the non-"written" status spelled out) and the PATH probe behind the
 * missing-tool hint.
 */
class ContractKitActionTest {

    @Test
    @DisplayName("The report marks written files ✓ with no status suffix")
    void reportMarksWrittenFiles() {
        String report = ContractKitAction.renderReport(List.of(
                new ContractKit.Outcome("foundry.toml", "written", true)));

        assertThat(report).isEqualTo("  ✓ foundry.toml\n");
    }

    @Test
    @DisplayName("The report marks untouched files – and says why in parentheses")
    void reportExplainsUntouchedFiles() {
        String report = ContractKitAction.renderReport(List.of(
                new ContractKit.Outcome("src/MyContract.sol", "exists, untouched", false)));

        assertThat(report)
                .isEqualTo("  – src/MyContract.sol  (exists, untouched)\n");
    }

    @Test
    @DisplayName("A mixed scaffold renders one line per outcome, in order")
    void reportKeepsOutcomeOrder() {
        String report = ContractKitAction.renderReport(List.of(
                new ContractKit.Outcome("aiken.toml", "written", true),
                new ContractKit.Outcome("README.md", "differs, wrote .suggested", true),
                new ContractKit.Outcome("lib/vault.ak", "exists, untouched", false)));

        assertThat(report).isEqualTo(
                "  ✓ aiken.toml\n"
                + "  ✓ README.md  (differs, wrote .suggested)\n"
                + "  – lib/vault.ak  (exists, untouched)\n");
    }

    @Test
    @DisplayName("An empty outcome list renders an empty report, not a crash")
    void reportSurvivesEmptyOutcomes() {
        assertThat(ContractKitAction.renderReport(List.of())).isEmpty();
    }

    @Test
    @DisplayName("A tool that is nowhere on PATH is honestly reported absent")
    void toolProbeReportsAbsence() {
        // ToolLocator returns the bare name unresolved — that IS the miss
        assertThat(ContractKitAction.toolOnPath(
                "definitely-not-a-real-toolchain-9000")).isFalse();
    }
}
