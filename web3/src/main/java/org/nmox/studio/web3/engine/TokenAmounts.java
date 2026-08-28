package org.nmox.studio.web3.engine;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Exact raw ↔ human conversion for token amounts (v2.44.0): an ERC-20
 * balance arrives as an integer scaled by the token's own
 * {@code decimals()}, and showing a user {@code 1500000000000000000}
 * when the token means {@code 1.5} is a lie of scale. Everything here
 * is exact {@link BigDecimal} arithmetic — no floating point, no
 * rounding: a human amount with more fractional digits than the token
 * has decimals is REFUSED, never silently truncated, because a
 * truncated transfer amount is money.
 */
public final class TokenAmounts {

    private TokenAmounts() {
    }

    /**
     * The human rendering of a raw amount: exact, trailing fractional
     * zeros trimmed ({@code 1.500000} → {@code 1.5}), whole numbers
     * without a decimal point. Zero decimals returns the integer as-is.
     */
    public static String toHuman(BigInteger raw, int decimals) {
        if (decimals < 0 || decimals > 77) {
            throw new IllegalArgumentException("decimals out of range: " + decimals);
        }
        BigDecimal scaled = new BigDecimal(raw, decimals);
        String plain = scaled.stripTrailingZeros().toPlainString();
        // stripTrailingZeros turns 0 into 0E-18 territory avoided by
        // toPlainString, but 0.000 strips to 0 — keep that
        return plain;
    }

    /**
     * Parses a human amount back to the raw integer. Refusals speak:
     * junk, negatives, and more fractional digits than the token's
     * decimals all throw with the reason — a transfer form must never
     * guess at money.
     */
    public static BigInteger toRaw(String human, int decimals) {
        if (decimals < 0 || decimals > 77) {
            throw new IllegalArgumentException("decimals out of range: " + decimals);
        }
        String text = human == null ? "" : human.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Amount is empty");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Not a number: " + text);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (Math.max(0, value.stripTrailingZeros().scale()) > decimals) {
            throw new IllegalArgumentException("Too many decimal places — this "
                    + "token has " + decimals + " decimals");
        }
        return value.movePointRight(decimals).toBigIntegerExact();
    }
}
