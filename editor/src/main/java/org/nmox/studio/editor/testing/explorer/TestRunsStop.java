package org.nmox.studio.editor.testing.explorer;

import org.nmox.studio.core.spi.LiveRuns;

/**
 * The Tests window's own Stop (v2.73.0): only the focused-test runs —
 * RunFocusedTestAction registers each under {@code focused-test:} — never
 * the dev server or an install the toolbar ■ would take down with them.
 * Pure over {@link LiveRuns} so the selection rule is a unit test.
 */
final class TestRunsStop {

    static final String PREFIX = "focused-test:";

    private TestRunsStop() {
    }

    static boolean anyLive() {
        return anyLive(LiveRuns.live());
    }

    static boolean anyLive(java.util.List<LiveRuns.Run> live) {
        for (LiveRuns.Run r : live) {
            if (r.id().startsWith(PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /** Stops every live focused-test run; returns how many. */
    static int stopAll() {
        int n = 0;
        for (LiveRuns.Run r : LiveRuns.live()) {
            if (r.id().startsWith(PREFIX) && LiveRuns.stop(r.id()) != null) {
                n++;
            }
        }
        return n;
    }
}
