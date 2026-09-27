package org.nmox.studio.tools.npm;

import java.util.List;
import org.nmox.studio.core.spi.LiveRuns;
import org.openide.util.NbBundle;

/**
 * What the ■ says: its tooltip, and the status line after a press
 * (ledger 89, v2.101.0).
 *
 * <p>This used to live in {@code core.spi.LiveRuns}, which assembled the
 * whole sentence in English — "Stop 3 running commands: …" — and handed it
 * to a Swing sink. A pure core has no bundle and no business having one, so
 * every translated build showed the ■'s tooltip in English while every
 * bundle in the product was complete. The house pattern is the cure and was
 * already written down: <b>the core returns the data, the consumer renders
 * it</b> — the law is the string that reaches the label.
 *
 * <p>The count is a real plural. Polish, Russian and Ukrainian inflect the
 * noun across 1 / 2–4 / 5+, so their patterns carry the four-branch
 * {@code choice} this codebase uses everywhere; Indonesian, Filipino,
 * Vietnamese and Chinese have no grammatical plural and say the same words
 * in every branch, on purpose. The separator between names stays a bare
 * ", " — punctuation is furniture, not words.
 */
final class StopRunText {

    private StopRunText() {
    }

    /**
     * This package keeps its English in a hand-written {@code Bundle.properties}
     * and reads it with {@link NbBundle#getMessage} — 44 call sites do it that
     * way. Following the local convention is not only tidiness here: the
     * annotation processor MERGES generated keys into that hand file at compile
     * time, so an incremental build that re-copies resources without
     * recompiling serves the hand file alone and the generated keys vanish.
     */
    private static String msg(String key, Object... args) {
        return args.length == 0 ? NbBundle.getMessage(StopRunText.class, key)
                : NbBundle.getMessage(StopRunText.class, key, args);
    }

    /** The ■'s tooltip: what a press would stop, and since when. */
    static String tooltip(List<LiveRuns.Run> live) {
        if (live.isEmpty()) {
            return msg("StopRunText_tooltipIdle");
        }
        String named = named(live);
        return live.size() == 1
                ? msg("StopRunText_tooltipOne", named)
                : msg("StopRunText_tooltipMany", live.size(), named);
    }

    /** The status line after a press: what was stopped, or that nothing was. */
    static String stopped(List<LiveRuns.Run> stopped) {
        if (stopped.isEmpty()) {
            return msg("StopRunText_stoppedNone");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stopped.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(stopped.get(i).label());
        }
        return msg("StopRunText_stopped", sb.toString());
    }

    /**
     * A press that found every live run already stopping (3.4): they are
     * still running, so "nothing is running" would be false.
     */
    static String alreadyStopping(List<LiveRuns.Run> live) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < live.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(live.get(i).label());
        }
        return msg("StopRunText_alreadyStopping", sb.toString());
    }

    /** The status line for one press of the ■: what it stopped, what is still stopping, or that nothing runs. */
    static String afterPress(List<LiveRuns.Run> stoppedNow, List<LiveRuns.Run> stillLive) {
        return stoppedNow.isEmpty() && !stillLive.isEmpty() ? alreadyStopping(stillLive) : stopped(stoppedNow);
    }

    /** Each run's label, carrying its start time where it has one. */
    private static String named(List<LiveRuns.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            LiveRuns.Run run = runs.get(i);
            if (LiveRuns.isStopping(run.id())) {
                // asked to stop and not exited yet (3.4): say so, not "since"
                sb.append(msg("StopRunText_runStopping", run.label()));
                continue;
            }
            String at = LiveRuns.sinceTime(run.id());
            sb.append(at.isEmpty() ? run.label() : msg("StopRunText_runSince", run.label(), at));
        }
        return sb.toString();
    }
}
