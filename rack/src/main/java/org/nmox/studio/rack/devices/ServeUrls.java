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
final class ServeUrls {

    /** A local URL printed by the server (vite "Local:", CRA, serve...). */
    private static final Pattern LOCAL_URL =
            Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"']*)");

    private ServeUrls() {
    }

    /** The first local URL on the line, or null when it carries none. */
    static String firstLocalUrl(String line) {
        Matcher m = LOCAL_URL.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}
