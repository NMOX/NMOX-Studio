package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.util.Locale;
import org.nmox.studio.web3.model.AbiEntry;

/**
 * Small display rules shared by the Watch feed, the deployment address
 * book, and Quick Search labels — pure so every one of them is pinned
 * by a test instead of living inside a renderer.
 */
public final class DisplayValues {

    private DisplayValues() {
    }

    /**
     * The Watch pane's value rule: a {@code uint256} parameter named
     * {@code value} or {@code amount} is almost certainly wei, so it
     * shows through {@link Units#formatWei} ("1.5 ETH"); an
     * {@code address} shows shortened; everything else stays raw.
     * A value that doesn't parse stays raw too — never a throw.
     */
    public static String display(String paramName, String type, String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String canonical = AbiEntry.canonicalType(type == null ? "" : type);
        if (canonical.equals("address")) {
            return shortAddress(rawValue);
        }
        if (isWeiName(paramName) && canonical.equals("uint256")) {
            try {
                return Units.formatWei(new BigInteger(rawValue.trim()));
            } catch (NumberFormatException notDecimal) {
                return rawValue;
            }
        }
        return rawValue;
    }

    /** True for the parameter names the wei display rule applies to. */
    static boolean isWeiName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("value") || lower.equals("amount");
    }

    /** {@code 0xd8dA6BF2…6045} — first six hex digits, ellipsis, last four. */
    public static String shortAddress(String address) {
        if (address == null) {
            return "";
        }
        String trimmed = address.trim();
        if (trimmed.length() <= 12) {
            return trimmed;
        }
        return trimmed.substring(0, 8) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    /** True for a 20-byte 0x-hex address (checksum-tolerant). */
    public static boolean isAddress(String text) {
        if (text == null) {
            return false;
        }
        String digits = Hex.strip0x(text.trim());
        if (digits.length() != 40) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** "just now" / "5 min ago" / "3 h ago" / "2 d ago" — the address book's age column. */
    public static String age(long thenMillis, long nowMillis) {
        long seconds = (nowMillis - thenMillis) / 1000;
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        return (hours / 24) + " d ago";
    }
}
