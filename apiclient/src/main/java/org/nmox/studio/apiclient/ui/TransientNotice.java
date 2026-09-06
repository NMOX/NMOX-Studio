package org.nmox.studio.apiclient.ui;

import java.awt.Color;

/**
 * The response strip's two kinds of text (v2.85.0): the VERDICT of the
 * last send ("200 · 15ms · 60 B", green or red) and a passing NOTICE
 * ("curl command copied"). The API Studio walk found a notice ERASING
 * the verdict — a copy button beside the strip is where the eye is, so
 * the notice belongs there (the v2.34.4 feedback-lands-where-typed law),
 * but the verdict is what the user glances back for, so it must come
 * back. Pure: the window arms a timer and asks {@link #expire()}.
 */
final class TransientNotice {

    /** What the strip should show right now. */
    record Shown(String text, Color color) {
    }

    static final Color NOTICE_GRAY = Color.GRAY;

    private Shown verdict = new Shown(" ", NOTICE_GRAY);
    private Shown notice;
    private long generation;

    /** A send's verdict (or "Sending…", "Cancelled"): shown, remembered, and any notice dropped. */
    Shown verdict(String text, Color color) {
        verdict = new Shown(text, color);
        notice = null;
        generation++;
        return verdict;
    }

    /** A passing notice: shown now; the caller arms a timer and calls {@link #expire()} with the returned generation. */
    long notice(String text) {
        notice = new Shown(text, NOTICE_GRAY);
        return ++generation;
    }

    /**
     * The notice of {@code gen} expires: the verdict comes back — unless a
     * newer verdict or notice already replaced it (then nothing changes).
     * Returns what to show now.
     */
    Shown expire(long gen) {
        if (gen == generation && notice != null) {
            notice = null;
        }
        return shown();
    }

    Shown shown() {
        return notice != null ? notice : verdict;
    }

    /** The strip forgets everything (a re-aim, a cleared response). */
    Shown clear() {
        verdict = new Shown(" ", NOTICE_GRAY);
        notice = null;
        generation++;
        return verdict;
    }
}
