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
@org.openide.util.NbBundle.Messages({
    "GettingStarted_projectLabel=Open a project",
    "GettingStarted_projectGesture=Open Folder…  ⌥⌘O",
    "GettingStarted_runLabel=Run something",
    "GettingStarted_runGesture=▶ (F6), or GO on a rack device  ⌘9",
    "GettingStarted_serveLabel=See a server go live",
    "GettingStarted_serveGesture=a serve device lights the ⇄ chip",
    "GettingStarted_kvasirLabel=Ask KVASIR about code",
    "GettingStarted_kvasirGesture=select code → right-click → Ask KVASIR",
    "GettingStarted_learnLabel=Try a learning space",
    "GettingStarted_learnGesture=New Learning Space…  ⇧⌘L",
    "GettingStarted_agentLabel=Point an agent at the IDE",
    "GettingStarted_agentGesture=Tools ▸ Agent Port (MCP)…",
    "GettingStarted_progress={0} of {1}"
})
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
            new Step("project", Bundle.GettingStarted_projectLabel(), Bundle.GettingStarted_projectGesture(),
                    Target.action("File", "org.nmox.studio.ui.actions.OpenFolderAction")),
            new Step("run", Bundle.GettingStarted_runLabel(), Bundle.GettingStarted_runGesture(),
                    Target.window("RackTopComponent")),
            new Step("serve", Bundle.GettingStarted_serveLabel(), Bundle.GettingStarted_serveGesture(),
                    Target.window("RackTopComponent")),
            new Step("kvasir", Bundle.GettingStarted_kvasirLabel(), Bundle.GettingStarted_kvasirGesture(),
                    Target.guide("#kvasir--explain-the-last-failure")),
            new Step("learn", Bundle.GettingStarted_learnLabel(), Bundle.GettingStarted_learnGesture(),
                    Target.action("File", "org.nmox.studio.ui.actions.NewLearningSpaceAction")),
            // v2.84.0: the Agent Port had eight releases and no place on the
            // first-run checklist; the door is this module's thin action over
            // the rack's, so the door gate can see it in the ui layer
            new Step("agent", Bundle.GettingStarted_agentLabel(), Bundle.GettingStarted_agentGesture(),
                    Target.action("Tools", "org.nmox.studio.ui.gettingstarted.PointAnAgentAction")));

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
        return Bundle.GettingStarted_progress(String.valueOf(done(done)), String.valueOf(STEPS.size()));
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
