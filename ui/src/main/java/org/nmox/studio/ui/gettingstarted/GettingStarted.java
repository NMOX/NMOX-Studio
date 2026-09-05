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
    /**
     * Where a click on a step takes you (v2.69.9 — David's walk found every
     * step a dud: the rows were labels with a tooltip). A step is a door,
     * not a checkbox: clicking it opens the gesture's own surface, and the
     * tick still comes from the record the gesture leaves behind.
     */
    public record Target(Kind kind, String category, String id) {
        public enum Kind { ACTION, WINDOW, GUIDE }

        public static Target action(String category, String id) {
            return new Target(Kind.ACTION, category, id);
        }

        public static Target window(String topComponentId) {
            return new Target(Kind.WINDOW, null, topComponentId);
        }

        /** The user guide at an anchor, plus the gesture on the status line — for steps with no single action. */
        public static Target guide(String anchor) {
            return new Target(Kind.GUIDE, null, anchor);
        }
    }

    public record Step(String key, String label, String gesture, Target target) {
    }

    /** The five, in the order a first session naturally takes them. */
    public static final List<Step> STEPS = List.of(
            new Step("project", "Open a project", "Open Folder…  ⌥⌘O",
                    Target.action("File", "org.nmox.studio.ui.actions.OpenFolderAction")),
            new Step("run", "Run something", "▶ (F6), or GO on a rack device  ⌘9",
                    Target.window("RackTopComponent")),
            new Step("serve", "See a server go live", "a serve device lights the ⇄ chip",
                    Target.window("RackTopComponent")),
            new Step("oracle", "Ask ORACLE about code", "select code → right-click → Ask ORACLE",
                    Target.guide("#oracle--explain-the-last-failure")),
            new Step("learn", "Try a learning space", "New Learning Space…  ⇧⌘L",
                    Target.action("File", "org.nmox.studio.ui.actions.NewLearningSpaceAction")));

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
