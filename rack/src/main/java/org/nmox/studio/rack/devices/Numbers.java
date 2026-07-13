package org.nmox.studio.rack.devices;

/**
 * Tool output is untrusted input: a regex-captured {@code \d+} can
 * exceed int range, and {@code Integer.parseInt} would throw inside an
 * output listener. CommandExecutor's safeAccept contains the throw, but
 * the rest of that line's parse (LCD counts, the diagnostic itself) is
 * silently lost — so device parsers clamp instead of trusting.
 */
final class Numbers {

    private Numbers() {
    }

    /** The captured digits as an int: clamped when huge, 0 when unparseable. */
    static int intOrZero(String digits) {
        try {
            long v = Long.parseLong(digits);
            return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
