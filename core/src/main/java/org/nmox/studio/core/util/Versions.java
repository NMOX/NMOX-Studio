package org.nmox.studio.core.util;

/**
 * Honest version arithmetic for the update check: parse the version out
 * of the branded product name and compare dotted versions numerically —
 * 1.9.0 is older than 1.24.0, which string comparison gets wrong.
 */
public final class Versions {

    private Versions() {
    }

    /**
     * Extracts "1.24.0" from strings like "NMOX Studio 1.24.0" or
     * "v1.24.0"; null when there is no x.y[.z] version to find (the
     * dev-build "NMOX Studio 1.0" yields "1.0", which callers treat as
     * unstamped).
     */
    public static String extract(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)+)").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Numeric dotted-version compare: negative if a &lt; b, 0 if equal.
     * Segments are read as their leading digits only, so a raw or suffixed
     * version ("1.24.0-rc1", "2.0.0+build5") compares by its numeric spine
     * instead of throwing — suffixes carry no ordering here by design
     * (ledger 58; internal callers always pass extract()-normalized input,
     * this hardens the public seam). Absurdly long runs of digits clamp
     * rather than overflow.
     */
    public static int compare(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            long ai = i < as.length ? numericPrefix(as[i]) : 0;
            long bi = i < bs.length ? numericPrefix(bs[i]) : 0;
            if (ai != bi) {
                return Long.compare(ai, bi);
            }
        }
        return 0;
    }

    /** The leading digits of a segment as a long; blank/non-numeric → 0, 18+ digits clamp. */
    static long numericPrefix(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        // 18 digits always fit a long; anything longer is no real version
        String digits = segment.substring(0, Math.min(end, 18));
        return Long.parseLong(digits);
    }

    /** True when the running version is stamped (a release build, not "1.0"). */
    public static boolean isStamped(String version) {
        return version != null && !version.equals("1.0");
    }
}
