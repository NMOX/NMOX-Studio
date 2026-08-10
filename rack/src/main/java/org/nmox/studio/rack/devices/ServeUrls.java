package org.nmox.studio.rack.devices;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one URL-from-stdout scan the serve devices share: the first local
 * address a dev server prints ("Local: http://localhost:5173/",
 * "started server on http://127.0.0.1:3000"). Extracted byte-identical
 * from SURGE/HALO/NEXUS/PHOENIX, which carried the same pattern four
 * times. ARTISAN deliberately keeps its own variant (it must also stop
 * at {@code ]} — artisan brackets its URL), and ANVIL parses a
 * "Listening on host:port" banner instead; neither is this scan.
 */
public final class ServeUrls {

    /**
     * A local URL printed by the server (vite "Local:", CRA, serve...).
     * Control characters are excluded from the tail (v1.216.0): an
     * imperfect ANSI strip upstream can leave a stray ESC byte glued to
     * the URL, and a permissive char class swallowed it into the capture
     * — the registered URL then carried an invisible escape character.
     */
    private static final Pattern LOCAL_URL =
            Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"'\\x00-\\x1f]*)");

    private ServeUrls() {
    }

    /** The first local URL on the line, or null when it carries none. */
    /**
     * Public since v1.212.0: the IDE-native Run lane (tools module) needs
     * the same parse the serve devices use, so that pressing F6 announces
     * its dev server exactly like pressing GO on VELOCITY does.
     *
     * <p>Arrow-target URLs are skipped (v1.262.0, ledger 63): a URL
     * immediately preceded by {@code ->} (or {@code →}) is a mapping
     * DESTINATION, not a serving. Pinned by live capture rather than
     * folklore — http-proxy-middleware v2 (the CRA-era stack) prints
     * {@code [HPM] Proxy created: /  -> http://localhost:3001} BEFORE
     * the server's own banner, so the backend target used to win the
     * one-shot v1.212.0 auto-open; HPM v4 and webpack-dev-server 5
     * print no such line (measured 2026-08-04, exact outputs in the
     * ledger-63 close). No banner in the corpus (vite {@code Local:},
     * wds {@code Loopback:}, CRA, {@code started server on}, artisan,
     * {@code php -S}) puts an arrow before its own URL — arrows point
     * at destinations. Skipping returns null on a pure proxy line, and
     * the real banner registers on a later line, which is the correct
     * order.
     */
    /**
     * The port a server's own startup banner announces, or
     * {@code fallback} when the line names none (v1.320.0).
     *
     * <p>Exists because the fixed-port lanes became probed-port lanes:
     * once the STATIC lane may bind 8001 instead of 8000, announcing
     * the old constant would be a serving-truth violation — the ⇄ chip
     * would point at a port nothing listens on. The truth is IN the
     * banner: python prints {@code "Serving HTTP on :: port 8001"},
     * php prints {@code "(http://127.0.0.1:8001) started"}. Read it.
     */
    public static int bannerPort(String line, int fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\bport (\\d{2,5})\\b").matcher(line);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = java.util.regex.Pattern
                .compile("://[^\\s/]*:(\\d{2,5})").matcher(line);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return fallback;
    }

    public static String firstLocalUrl(String line) {
        Matcher m = LOCAL_URL.matcher(line);
        while (m.find()) {
            if (!arrowPrecedes(line, m.start())) {
                return m.group(1);
            }
        }
        return null;
    }

    /** True when the text before {@code start} ends with an arrow token. */
    private static boolean arrowPrecedes(String line, int start) {
        int i = start;
        while (i > 0 && line.charAt(i - 1) == ' ') {
            i--;
        }
        return (i >= 2 && line.charAt(i - 1) == '>' && line.charAt(i - 2) == '-')
                || (i >= 1 && line.charAt(i - 1) == '→');
    }
}
