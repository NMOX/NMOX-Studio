package org.nmox.studio.ui.gettingstarted;

import java.util.List;
import java.util.Set;

/**
 * Getting Started, the pure half: the five first gestures a new install
 * earns its keep by, and the arithmetic of a checklist that ticks itself
 * from records the product already keeps — never a survey, never a
 * network call. A step, once done, stays done (the store merges, it does
 * not recompute), and the column disappears when all five are ticked or
 * when the user hides it.
 */
public final class GettingStarted {

    /** One step: a stable key, what the user does, where the gesture lives. */
    public record Step(String key, String label, String gesture) {
    }

    /** The five, in the order a first session naturally takes them. */
    public static final List<Step> STEPS = List.of(
            new Step("project", "Open a project", "Open Folder…  ⌥⌘O"),
            new Step("run", "Run something in the rack", "Task Rack  ⌘9 — press GO on a device"),
            new Step("serve", "See a server go live", "a serve device lights the ⇄ chip"),
            new Step("oracle", "Ask ORACLE about code", "select code → right-click → Ask ORACLE"),
            new Step("learn", "Try a learning space", "New Learning Space…  ⇧⌘L"));

    private GettingStarted() {
    }

    /** Steps done, counted against the five. */
    public static int done(Set<String> done) {
        int n = 0;
        for (Step s : STEPS) {
            if (done.contains(s.key())) {
                n++;
            }
        }
        return n;
    }

    /** "2 of 5" — the heading's count. */
    public static String progress(Set<String> done) {
        return done(done) + " of " + STEPS.size();
    }

    /** Whether every step is ticked. */
    public static boolean allDone(Set<String> done) {
        return done(done) == STEPS.size();
    }

    /** The first step not yet done, or null when all are. */
    public static Step next(Set<String> done) {
        for (Step s : STEPS) {
            if (!done.contains(s.key())) {
                return s;
            }
        }
        return null;
    }

    /** Whether the column shows: not hidden, and something left to do. */
    public static boolean visible(Set<String> done, boolean hidden) {
        return !hidden && !allDone(done);
    }
}
