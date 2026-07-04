package org.nmox.studio.web3.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keccak-256 against pinned vectors. The short vectors are the
 * universally documented ones (they differ from SHA-3-256 precisely
 * because of the 0x01 vs 0x06 padding — if the padding were wrong,
 * every one of these would fail). The multi-block and padding-edge
 * vectors were generated with OpenSSL 3.6.2's independent KECCAK-256
 * implementation, which was first validated against all the documented
 * vectors below. Never adjust a pinned vector — fix the implementation.
 */
class Keccak256Test {

    // ---- the documented short vectors ---------------------------------

    @Test
    @DisplayName("keccak256(\"\") — the empty-input vector every Ethereum stack pins")
    void emptyInput() {
        assertThat(Keccak256.hashHex(""))
                .isEqualTo("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
    }

    @Test
    @DisplayName("keccak256(\"abc\")")
    void abc() {
        assertThat(Keccak256.hashHex("abc"))
                .isEqualTo("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45");
    }

    @Test
    @DisplayName("keccak256 of the quick brown fox")
    void quickBrownFox() {
        assertThat(Keccak256.hashHex("The quick brown fox jumps over the lazy dog"))
                .isEqualTo("4d741b6f1eb29cb2a9b9911c82f56fa8d73b04959d3d9d222895df6c0b28aa15");
    }

    @Test
    @DisplayName("the ERC-20 transfer selector: keccak256(\"transfer(address,uint256)\")[0..4] = a9059cbb")
    void transferSelector() {
        assertThat(Hex.toHex(Keccak256.selector("transfer(address,uint256)")))
                .isEqualTo("a9059cbb");
    }

    @Test
    @DisplayName("the ERC-20 Transfer event topic: keccak256(\"Transfer(address,address,uint256)\")")
    void transferEventTopic() {
        assertThat(Keccak256.hashHex("Transfer(address,address,uint256)"))
                .isEqualTo("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    }

    // ---- multi-block absorption (inputs past the 136-byte rate) --------
    // Expectations generated with OpenSSL 3.6.2 (`openssl dgst -keccak-256`),
    // an independent implementation validated against every vector above
    // before use. See the class comment.

    @Test
    @DisplayName("200 bytes of 'a' — 200 > 136 forces a second absorb block")
    void multiBlockInput() {
        assertThat(Keccak256.hashHex("a".repeat(200)))
                .isEqualTo("96ea54061def936c4be90b518992fdc6f12f535068a256229aca54267b4d084d");
    }

    @Test
    @DisplayName("exactly 136 bytes — a full block plus a pure-padding block")
    void exactRateBoundary() {
        assertThat(Keccak256.hashHex("a".repeat(136)))
                .isEqualTo("a6c4d403279fe3e0af03729caada8374b5ca54d8065329a3ebcaeb4b60aa386e");
    }

    @Test
    @DisplayName("135 bytes — one byte of padding room, the 0x81 both-pad-bits edge")
    void singlePaddingByteEdge() {
        assertThat(Keccak256.hashHex("a".repeat(135)))
                .isEqualTo("34367dc248bbd832f4e3e69dfaac2f92638bd0bbd18f2912ba4ef454919cf446");
    }

    // ---- API shape -------------------------------------------------------

    @Test
    @DisplayName("hash returns 32 bytes; selector returns exactly the first 4")
    void outputShapes() {
        byte[] digest = Keccak256.hash("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(digest).hasSize(32);
        assertThat(Keccak256.selector("abc"))
                .hasSize(4)
                .isEqualTo(Arrays.copyOf(digest, 4));
    }

    @Test
    @DisplayName("hashHex hashes the UTF-8 bytes, not the UTF-16 chars")
    void utf8NotUtf16() {
        String snowman = "☃"; // ☃, three UTF-8 bytes
        assertThat(Keccak256.hashHex(snowman))
                .isEqualTo(Hex.toHex(Keccak256.hash(snowman.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("hashing is repeatable and input arrays are not mutated")
    void pureFunction() {
        byte[] input = "pure".getBytes(StandardCharsets.UTF_8);
        byte[] copy = input.clone();
        byte[] first = Keccak256.hash(input);
        byte[] second = Keccak256.hash(input);
        assertThat(first).isEqualTo(second);
        assertThat(input).isEqualTo(copy);
    }
}
