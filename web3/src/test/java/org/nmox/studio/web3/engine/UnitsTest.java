package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wei ↔ display: unit choice, BigDecimal exactness (no doubles
 * anywhere), and the >18-decimals refusal.
 */
class UnitsTest {

    @Test
    @DisplayName("1.5 ETH worth of wei formats as 1.5 ETH")
    void ethFormatting() {
        assertThat(Units.formatWei(new BigInteger("1500000000000000000")))
                .isEqualTo("1.5 ETH");
    }

    @Test
    @DisplayName("whole ETH amounts drop their decimals: 2 ETH, not 2.000000")
    void wholeEth() {
        assertThat(Units.formatWei(new BigInteger("2000000000000000000")))
                .isEqualTo("2 ETH");
    }

    @Test
    @DisplayName("ETH keeps at most 6 decimals, rounded half-up")
    void ethRounding() {
        assertThat(Units.formatWei(new BigInteger("1234567890123456789")))
                .isEqualTo("1.234568 ETH");
    }

    @Test
    @DisplayName("gas-price territory formats in gwei")
    void gweiFormatting() {
        assertThat(Units.formatWei(new BigInteger("2000000000"))).isEqualTo("2 gwei");
        assertThat(Units.formatWei(new BigInteger("1500000000"))).isEqualTo("1.5 gwei");
    }

    @Test
    @DisplayName("small values stay in wei, with grouping")
    void weiFormatting() {
        assertThat(Units.formatWei(BigInteger.ZERO)).isEqualTo("0 wei");
        assertThat(Units.formatWei(BigInteger.valueOf(21000))).isEqualTo("21,000 wei");
        assertThat(Units.formatWei(null)).isEqualTo("0 wei");
    }

    @Test
    @DisplayName("the unit thresholds: 0.001 ETH is the ETH floor, 1 gwei the gwei floor")
    void unitThresholds() {
        assertThat(Units.formatWei(new BigInteger("1000000000000000")))
                .isEqualTo("0.001 ETH");
        assertThat(Units.formatWei(new BigInteger("999999999999999")))
                .endsWith(" gwei");
        assertThat(Units.formatWei(new BigInteger("1000000000"))).isEqualTo("1 gwei");
        assertThat(Units.formatWei(new BigInteger("999999999"))).isEqualTo("999,999,999 wei");
    }

    @Test
    @DisplayName("negative balances keep their sign in every unit")
    void negativeValues() {
        assertThat(Units.formatWei(new BigInteger("-1500000000000000000")))
                .isEqualTo("-1.5 ETH");
        assertThat(Units.formatWei(BigInteger.valueOf(-5))).isEqualTo("-5 wei");
    }

    // ---- parseEth ---------------------------------------------------------

    @Test
    @DisplayName("parseEth: 1.5 ETH is exactly 1500000000000000000 wei")
    void parseSimple() {
        assertThat(Units.parseEth("1.5")).isEqualTo(new BigInteger("1500000000000000000"));
        assertThat(Units.parseEth("0")).isEqualTo(BigInteger.ZERO);
        assertThat(Units.parseEth(" 0.001 ")).isEqualTo(new BigInteger("1000000000000000"));
    }

    @Test
    @DisplayName("parseEth round-trips the full 18 decimals without loss")
    void parseFullPrecision() {
        assertThat(Units.parseEth("0.000000000000000001")).isEqualTo(BigInteger.ONE);
        assertThat(Units.parseEth("1.000000000000000001"))
                .isEqualTo(new BigInteger("1000000000000000001"));
    }

    @Test
    @DisplayName("more than 18 decimals is refused — wei is the smallest unit")
    void tooManyDecimals() {
        assertThatThrownBy(() -> Units.parseEth("0.0000000000000000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18 decimal places");
    }

    @Test
    @DisplayName("negative amounts are refused with a human sentence")
    void negativeRefused() {
        assertThatThrownBy(() -> Units.parseEth("-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("non-numbers and blanks are refused with the 0.1 hint")
    void nonNumbersRefused() {
        assertThatThrownBy(() -> Units.parseEth("a lot"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.1");
        assertThatThrownBy(() -> Units.parseEth(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Units.parseEth(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("formatGas groups thousands: 21,000 gas")
    void gasFormatting() {
        assertThat(Units.formatGas(21_000)).isEqualTo("21,000 gas");
        assertThat(Units.formatGas(0)).isEqualTo("0 gas");
        assertThat(Units.formatGas(30_000_000)).isEqualTo("30,000,000 gas");
    }
}
