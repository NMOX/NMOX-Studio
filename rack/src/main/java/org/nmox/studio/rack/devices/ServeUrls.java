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
     */
    public static String firstLocalUrl(String line) {
        Matcher m = LOCAL_URL.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}
