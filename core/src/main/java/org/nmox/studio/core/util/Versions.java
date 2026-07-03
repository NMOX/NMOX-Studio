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

    /** Numeric dotted-version compare: negative if a &lt; b, 0 if equal. */
    public static int compare(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? Integer.parseInt(as[i]) : 0;
            int bi = i < bs.length ? Integer.parseInt(bs[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    /** True when the running version is stamped (a release build, not "1.0"). */
    public static boolean isStamped(String version) {
        return version != null && !version.equals("1.0");
    }
}
