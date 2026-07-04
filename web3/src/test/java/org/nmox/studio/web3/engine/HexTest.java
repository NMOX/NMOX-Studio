package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every hex string in this module — selectors, calldata, addresses,
 * bytecode — flows through this one class, so its 0x-tolerance and its
 * error messages are pinned down hard.
 */
class HexTest {

    @Test
    @DisplayName("toHex is lowercase and unprefixed; toHex0x adds the 0x")
    void encodeBasics() {
        byte[] bytes = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        assertThat(Hex.toHex(bytes)).isEqualTo("deadbeef");
        assertThat(Hex.toHex0x(bytes)).isEqualTo("0xdeadbeef");
    }

    @Test
    @DisplayName("an empty array encodes to the empty string (and 0x)")
    void encodeEmpty() {
        assertThat(Hex.toHex(new byte[0])).isEmpty();
        assertThat(Hex.toHex0x(new byte[0])).isEqualTo("0x");
    }

    @Test
    @DisplayName("fromHex accepts 0x-prefixed, 0X-prefixed, bare, upper and lower case")
    void decodeTolerance() {
        byte[] expected = {(byte) 0xDE, (byte) 0xAD};
        assertThat(Hex.fromHex("dead")).isEqualTo(expected);
        assertThat(Hex.fromHex("0xdead")).isEqualTo(expected);
        assertThat(Hex.fromHex("0XDEAD")).isEqualTo(expected);
        assertThat(Hex.fromHex("DeAd")).isEqualTo(expected);
    }

    @Test
    @DisplayName("'' and '0x' decode to an empty array")
    void decodeEmpty() {
        assertThat(Hex.fromHex("")).isEmpty();
        assertThat(Hex.fromHex("0x")).isEmpty();
    }

    @Test
    @DisplayName("round trip: bytes → hex → bytes over all 256 values")
    void roundTrip() {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        assertThat(Hex.fromHex(Hex.toHex(all))).isEqualTo(all);
        assertThat(Hex.fromHex(Hex.toHex0x(all))).isEqualTo(all);
    }

    @Test
    @DisplayName("odd-length hex is rejected with a message that says so")
    void oddLengthRejected() {
        assertThatThrownBy(() -> Hex.fromHex("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("odd number of digits");
        assertThatThrownBy(() -> Hex.fromHex("0xabc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("odd number of digits");
    }

    @Test
    @DisplayName("a non-hex character is rejected, naming the character")
    void nonHexRejected() {
        assertThatThrownBy(() -> Hex.fromHex("zz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'z'");
        assertThatThrownBy(() -> Hex.fromHex("0xa g4"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null input is rejected, not an NPE")
    void nullRejected() {
        assertThatThrownBy(() -> Hex.fromHex(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing");
    }

    @Test
    @DisplayName("strip0x removes the prefix and only the prefix; null-safe")
    void strip0xBehavior() {
        assertThat(Hex.strip0x("0xdead")).isEqualTo("dead");
        assertThat(Hex.strip0x("0Xdead")).isEqualTo("dead");
        assertThat(Hex.strip0x("dead")).isEqualTo("dead");
        assertThat(Hex.strip0x("0x")).isEmpty();
        assertThat(Hex.strip0x("0")).isEqualTo("0");
        assertThat(Hex.strip0x(null)).isEmpty();
    }
}
