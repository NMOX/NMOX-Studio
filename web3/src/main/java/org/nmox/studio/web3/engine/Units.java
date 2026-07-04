package org.nmox.studio.web3.engine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Wei ↔ display conversions. BigInteger/BigDecimal all the way — a
 * double cannot hold 18 decimals of ETH honestly.
 */
public final class Units {

    private static final BigInteger WEI_PER_ETH = BigInteger.TEN.pow(18);
    private static final BigInteger WEI_PER_GWEI = BigInteger.TEN.pow(9);

    /** Below a thousandth of an ETH, ETH display would be all zeros. */
    private static final BigInteger ETH_FLOOR = BigInteger.TEN.pow(15);

    private Units() {
    }

    /**
     * Picks the sensible unit: {@code 1500000000000000000} →
     * {@code "1.5 ETH"}, {@code 2000000000} → {@code "2 gwei"}, small
     * values stay in wei. ETH keeps up to 6 decimals, gwei up to 3
     * (half-up, trailing zeros trimmed).
     */
    public static String formatWei(BigInteger wei) {
        if (wei == null) {
            return "0 wei";
        }
        BigInteger magnitude = wei.abs();
        if (magnitude.compareTo(ETH_FLOOR) >= 0) {
            return scaled(wei, WEI_PER_ETH, 6) + " ETH";
        }
        if (magnitude.compareTo(WEI_PER_GWEI) >= 0) {
            return scaled(wei, WEI_PER_GWEI, 3) + " gwei";
        }
        return String.format(Locale.ROOT, "%,d wei", wei);
    }

    /**
     * Parses a human ETH amount ({@code "1.5"}, {@code "0.001"}) to
     * wei.
     *
     * @throws IllegalArgumentException with a human message on
     *         non-numbers, negative amounts, and more than 18 decimal
     *         places (which would be a fraction of a wei)
     */
    public static BigInteger parseEth(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter an amount in ETH, like 0.1.");
        }
        BigDecimal eth;
        try {
            eth = new BigDecimal(text.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "'" + text.trim() + "' is not a number — enter an amount in ETH, like 0.1.");
        }
        if (eth.signum() < 0) {
            throw new IllegalArgumentException("An amount can't be negative.");
        }
        try {
            return eth.movePointRight(18).toBigIntegerExact();
        } catch (ArithmeticException fractionOfAWei) {
            throw new IllegalArgumentException(
                    "ETH amounts can't have more than 18 decimal places — wei is the smallest unit.");
        }
    }

    /** {@code 21000} → {@code "21,000 gas"}. */
    public static String formatGas(long gas) {
        return String.format(Locale.ROOT, "%,d gas", gas);
    }

    private static String scaled(BigInteger wei, BigInteger unit, int decimals) {
        BigDecimal value = new BigDecimal(wei)
                .divide(new BigDecimal(unit), decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return value.toPlainString();
    }
}
