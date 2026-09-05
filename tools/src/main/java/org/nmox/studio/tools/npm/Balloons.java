package org.nmox.studio.tools.npm;

import java.util.HashMap;
import java.util.Map;
import org.openide.awt.Notification;

/**
 * At most one live balloon per key (v2.74.0, the long shift's review):
 * every refused Run raised a fresh balloon, so three presses stacked
 * three bell entries for one wall. A new balloon under a key clears the
 * previous one first. Pure bookkeeping over the platform's
 * {@link Notification} handles; the test drives it with fakes.
 */
final class Balloons {

    private static final Map<String, Notification> LIVE = new HashMap<>();

    private Balloons() {
    }

    /** Clears the key's previous balloon (if any) and remembers the new one. */
    static synchronized void replace(String key, Notification fresh) {
        Notification previous = LIVE.put(key, fresh);
        if (previous != null && previous != fresh) {
            previous.clear();
        }
    }

    static synchronized int liveCount() {
        return LIVE.size();
    }

    static synchronized void clearForTest() {
        LIVE.clear();
    }
}
