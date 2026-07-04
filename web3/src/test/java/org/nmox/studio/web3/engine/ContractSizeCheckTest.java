package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * EIP-170 (24,576 bytes of runtime code) and EIP-3860 (49,152 bytes of
 * initcode) verdicts, including the human message format the Oversight
 * size table shows verbatim.
 */
class ContractSizeCheckTest {

    private static ContractArtifact artifact(String name, String bytecode,
            String deployed) {
        return new ContractArtifact(name, "src/X.sol", List.of(), bytecode, deployed);
    }

    @Test
    @DisplayName("a small contract is under the limit with the documented message shape")
    void smallContract() {
        // 1,204 bytes of runtime code
        ContractSizeCheck.Verdict verdict = ContractSizeCheck.check(
                artifact("Counter", "0x", "0x" + "ab".repeat(1204)));
        assertThat(verdict.over()).isFalse();
        assertThat(verdict.sizeBytes()).isEqualTo(1204);
        assertThat(verdict.limitBytes()).isEqualTo(24576);
        assertThat(verdict.message()).isEqualTo("Counter: 1,204 / 24,576 bytes (4.9%)");
    }

    @Test
    @DisplayName("exactly at the EIP-170 limit is not over — one byte more is")
    void exactLimitBoundary() {
        ContractSizeCheck.Verdict at = ContractSizeCheck.check(
                artifact("AtLimit", "0x", "0x" + "00".repeat(24576)));
        assertThat(at.over()).isFalse();
        assertThat(at.pct()).isEqualTo(100.0);

        ContractSizeCheck.Verdict over = ContractSizeCheck.check(
                artifact("OverLimit", "0x", "0x" + "00".repeat(24577)));
        assertThat(over.over()).isTrue();
        assertThat(over.message())
                .isEqualTo("OverLimit: 24,577 / 24,576 bytes (100.0%) — over the EIP-170 limit");
    }

    @Test
    @DisplayName("initcode is checked against EIP-3860's 49,152 bytes")
    void initcodeCheck() {
        ContractSizeCheck.Verdict ok = ContractSizeCheck.checkInitcode(
                artifact("Big", "0x" + "00".repeat(40000), "0x"));
        assertThat(ok.over()).isFalse();
        assertThat(ok.limitBytes()).isEqualTo(49152);

        ContractSizeCheck.Verdict over = ContractSizeCheck.checkInitcode(
                artifact("Huge", "0x" + "00".repeat(49153), "0x"));
        assertThat(over.over()).isTrue();
        assertThat(over.message()).endsWith("— over the EIP-3860 limit");
    }

    @Test
    @DisplayName("an interface artifact (bytecode 0x) measures zero bytes, no drama")
    void emptyBytecode() {
        ContractSizeCheck.Verdict verdict = ContractSizeCheck.check(
                artifact("IThing", "0x", "0x"));
        assertThat(verdict.sizeBytes()).isZero();
        assertThat(verdict.over()).isFalse();
        assertThat(verdict.message()).isEqualTo("IThing: 0 / 24,576 bytes (0.0%)");
    }

    @Test
    @DisplayName("null or odd-length bytecode hex counts as zero, never throws")
    void malformedBytecode() {
        assertThatCode(() -> {
            assertThat(ContractSizeCheck.check(artifact("X", "0x", null)).sizeBytes()).isZero();
            assertThat(ContractSizeCheck.check(artifact("X", "0x", "0xabc")).sizeBytes()).isZero();
        }).doesNotThrowAnyException();
    }
}
