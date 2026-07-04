package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared display rules: the Watch value heuristic (value/amount
 * uint256 → ETH units), address shortening, address validation for the
 * attach dialog, and the address book's age column.
 */
class DisplayValuesTest {

    // ---- the wei display rule ----

    @Test
    @DisplayName("a uint256 named value renders through Units")
    void valueRendersAsEth() {
        assertThat(DisplayValues.display("value", "uint256", "1500000000000000000"))
                .isEqualTo("1.5 ETH");
    }

    @Test
    @DisplayName("amount (any case) gets the same treatment")
    void amountRendersAsEth() {
        assertThat(DisplayValues.display("Amount", "uint256", "2000000000"))
                .isEqualTo("2 gwei");
    }

    @Test
    @DisplayName("bare uint canonicalizes to uint256 and qualifies")
    void bareUintQualifies() {
        assertThat(DisplayValues.display("value", "uint", "1500000000000000000"))
                .isEqualTo("1.5 ETH");
    }

    @Test
    @DisplayName("other names, other widths, and arrays stay raw")
    void nonWeiStaysRaw() {
        assertThat(DisplayValues.display("newNumber", "uint256", "5")).isEqualTo("5");
        assertThat(DisplayValues.display("value", "uint8", "3")).isEqualTo("3");
        assertThat(DisplayValues.display("value", "uint256[]", "[1, 2]"))
                .isEqualTo("[1, 2]");
        assertThat(DisplayValues.display(null, "uint256", "9")).isEqualTo("9");
    }

    @Test
    @DisplayName("a value that isn't decimal stays raw — never a throw")
    void unparseableValueStaysRaw() {
        assertThat(DisplayValues.display("value", "uint256", "not-a-number"))
                .isEqualTo("not-a-number");
    }

    @Test
    @DisplayName("address parameters shorten; null values become empty")
    void addressesShorten() {
        assertThat(DisplayValues.display("from", "address",
                "0x70997970c51812dc3a010c7d01b50e0d17dc79c8"))
                .isEqualTo("0x709979…79c8");
        assertThat(DisplayValues.display("value", "uint256", null)).isEmpty();
    }

    @Test
    @DisplayName("isWeiName is exactly value/amount, case-insensitive")
    void weiNames() {
        assertThat(DisplayValues.isWeiName("value")).isTrue();
        assertThat(DisplayValues.isWeiName("AMOUNT")).isTrue();
        assertThat(DisplayValues.isWeiName("valueWei")).isFalse();
        assertThat(DisplayValues.isWeiName(null)).isFalse();
    }

    // ---- shortAddress ----

    @Test
    @DisplayName("shortAddress keeps 0x + 6 digits, ellipsis, last 4")
    void shortAddressShape() {
        assertThat(DisplayValues.shortAddress(
                "0x5FbDB2315678afecb367f032d93F642f64180aa3"))
                .isEqualTo("0x5FbDB2…0aa3");
    }

    @Test
    @DisplayName("short strings and null pass through unharmed")
    void shortAddressEdges() {
        assertThat(DisplayValues.shortAddress("0x1234")).isEqualTo("0x1234");
        assertThat(DisplayValues.shortAddress(null)).isEmpty();
    }

    // ---- isAddress (the attach dialog's gate) ----

    @Test
    @DisplayName("isAddress accepts 20-byte hex with or without 0x, any case")
    void validAddresses() {
        assertThat(DisplayValues.isAddress(
                "0x5FbDB2315678afecb367f032d93F642f64180aa3")).isTrue();
        assertThat(DisplayValues.isAddress(
                "5fbdb2315678afecb367f032d93f642f64180aa3")).isTrue();
        assertThat(DisplayValues.isAddress(
                "  0x5FbDB2315678afecb367f032d93F642f64180aa3  ")).isTrue();
    }

    @Test
    @DisplayName("isAddress rejects wrong lengths, non-hex, and null")
    void invalidAddresses() {
        assertThat(DisplayValues.isAddress("0x1234")).isFalse();
        assertThat(DisplayValues.isAddress(
                "0xZZbDB2315678afecb367f032d93F642f64180aa3")).isFalse();
        assertThat(DisplayValues.isAddress(null)).isFalse();
        assertThat(DisplayValues.isAddress("")).isFalse();
    }

    // ---- age ----

    @Test
    @DisplayName("age buckets: just now, minutes, hours, days")
    void ageBuckets() {
        long now = 1_000_000_000_000L;

        assertThat(DisplayValues.age(now - 30_000, now)).isEqualTo("just now");
        assertThat(DisplayValues.age(now - 5 * 60_000, now)).isEqualTo("5 min ago");
        assertThat(DisplayValues.age(now - 3 * 3_600_000, now)).isEqualTo("3 h ago");
        assertThat(DisplayValues.age(now - 49 * 3_600_000, now)).isEqualTo("2 d ago");
    }

    @Test
    @DisplayName("a timestamp from the future reads as just now, not negative math")
    void futureTimestamp() {
        assertThat(DisplayValues.age(2_000, 1_000)).isEqualTo("just now");
    }
}
