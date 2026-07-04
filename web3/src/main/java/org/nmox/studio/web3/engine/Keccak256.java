package org.nmox.studio.web3.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Keccak-256 as Ethereum uses it: the Keccak-f[1600] permutation with
 * rate 1088 / capacity 512 and the <b>original Keccak padding</b>
 * ({@code pad10*1}, first pad byte {@code 0x01}) — NOT the finalized
 * SHA-3 padding ({@code 0x06}), which produces different digests.
 * Hand-rolled per the codebase idiom (no heavy dependency for one
 * primitive); it hashes public data — selectors, topics, signatures —
 * never key material, which this IDE does not touch by design.
 *
 * <p>Pinned against the well-known vectors (empty string, "abc", the
 * quick brown fox, the ERC-20 {@code Transfer} topic and
 * {@code transfer} selector) plus multi-block inputs cross-checked
 * against OpenSSL's independent KECCAK-256 — see {@code Keccak256Test}.
 */
public final class Keccak256 {

    private static final int RATE_BYTES = 136; // 1088-bit rate → 136-byte blocks

    /** The 24 round constants of Keccak-f[1600] (ι step). */
    private static final long[] ROUND_CONSTANTS = {
        0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
        0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
        0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
        0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
        0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
        0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    /** Rotation offsets (ρ step), indexed {@code x + 5y}. */
    private static final int[] ROTATIONS = {
        0, 1, 62, 28, 27,
        36, 44, 6, 55, 20,
        3, 10, 43, 25, 39,
        41, 45, 15, 21, 8,
        18, 2, 61, 56, 14
    };

    private Keccak256() {
    }

    /** The 32-byte Keccak-256 digest of the input. */
    public static byte[] hash(byte[] message) {
        long[] state = new long[25];

        // absorb all full rate-sized blocks
        int offset = 0;
        while (message.length - offset >= RATE_BYTES) {
            absorbBlock(state, message, offset);
            offset += RATE_BYTES;
        }

        // final block with pad10*1: 0x01 after the message, 0x80 on the
        // block's last byte. XOR-ing handles the one-byte-left edge where
        // both land on the same byte (0x81).
        byte[] block = new byte[RATE_BYTES];
        int remaining = message.length - offset;
        System.arraycopy(message, offset, block, 0, remaining);
        block[remaining] ^= (byte) 0x01;
        block[RATE_BYTES - 1] ^= (byte) 0x80;
        absorbBlock(state, block, 0);

        // squeeze: 32 bytes fit inside one rate, little-endian lanes
        byte[] out = new byte[32];
        for (int lane = 0; lane < 4; lane++) {
            long value = state[lane];
            for (int b = 0; b < 8; b++) {
                out[lane * 8 + b] = (byte) (value >>> (8 * b));
            }
        }
        return out;
    }

    /** The digest of the UTF-8 bytes of the text, as lowercase hex without prefix. */
    public static String hashHex(String utf8Text) {
        return Hex.toHex(hash(utf8Text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The 4-byte function selector of a canonical signature like
     * {@code transfer(address,uint256)} — the first four bytes of its
     * Keccak-256 digest.
     */
    public static byte[] selector(String functionSignature) {
        return Arrays.copyOf(hash(functionSignature.getBytes(StandardCharsets.UTF_8)), 4);
    }

    private static void absorbBlock(long[] state, byte[] data, int offset) {
        for (int lane = 0; lane < RATE_BYTES / 8; lane++) {
            long value = 0;
            for (int b = 7; b >= 0; b--) {
                value = (value << 8) | (data[offset + lane * 8 + b] & 0xFFL);
            }
            state[lane] ^= value;
        }
        keccakF(state);
    }

    /** The Keccak-f[1600] permutation, 24 rounds of θ, ρ+π, χ, ι. */
    private static void keccakF(long[] a) {
        long[] c = new long[5];
        long[] d = new long[5];
        long[] b = new long[25];
        for (int round = 0; round < 24; round++) {
            // θ
            for (int x = 0; x < 5; x++) {
                c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                d[x] = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    a[x + 5 * y] ^= d[x];
                }
            }
            // ρ and π
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int newX = y;
                    int newY = (2 * x + 3 * y) % 5;
                    b[newX + 5 * newY] = Long.rotateLeft(a[x + 5 * y], ROTATIONS[x + 5 * y]);
                }
            }
            // χ
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    a[x + 5 * y] = b[x + 5 * y]
                            ^ ((~b[(x + 1) % 5 + 5 * y]) & b[(x + 2) % 5 + 5 * y]);
                }
            }
            // ι
            a[0] ^= ROUND_CONSTANTS[round];
        }
    }
}
