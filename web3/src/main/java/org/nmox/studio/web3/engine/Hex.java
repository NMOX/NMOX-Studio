package org.nmox.studio.web3.engine;

/**
 * Hex encoding the way the EVM world writes it: lowercase, 0x-tolerant
 * on input, explicit about the prefix on output. Recon confirmed no
 * existing hex utility in the codebase — this is the one.
 */
public final class Hex {

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Hex() {
    }

    /** Lowercase hex, no prefix. An empty array encodes to {@code ""}. */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(DIGITS[(b >> 4) & 0xF]).append(DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    /** Lowercase hex with the {@code 0x} prefix. */
    public static String toHex0x(byte[] bytes) {
        return "0x" + toHex(bytes);
    }

    /**
     * Decodes hex, with or without a {@code 0x}/{@code 0X} prefix,
     * upper- or lowercase digits. {@code ""} and {@code "0x"} decode to
     * an empty array.
     *
     * @throws IllegalArgumentException on odd length or a non-hex
     *         character, with a message that says what was wrong
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Expected hex data, got nothing.");
        }
        String digits = strip0x(hex.trim());
        if (digits.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Hex data has an odd number of digits (" + digits.length()
                    + ") — every byte needs two.");
        }
        byte[] out = new byte[digits.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = digitValue(digits.charAt(i * 2));
            int lo = digitValue(digits.charAt(i * 2 + 1));
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Not a hex digit: '"
                        + digits.charAt(hi < 0 ? i * 2 : i * 2 + 1) + "' at position "
                        + (hi < 0 ? i * 2 : i * 2 + 1) + ".");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Removes a leading {@code 0x}/{@code 0X} if present; null-safe ({@code null} → {@code ""}). */
    public static String strip0x(String hex) {
        if (hex == null) {
            return "";
        }
        if (hex.length() >= 2 && hex.charAt(0) == '0'
                && (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
            return hex.substring(2);
        }
        return hex;
    }

    private static int digitValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }
}
