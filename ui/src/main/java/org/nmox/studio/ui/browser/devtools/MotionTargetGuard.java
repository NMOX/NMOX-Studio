package org.nmox.studio.ui.browser.devtools;

/**
 * The Motion pane's target bookkeeping (v2.15.0 arc review): the
 * preview belongs to the element that STARTED it, not to whatever the
 * DOM tab happens to select later — the v1.172.0 law, third
 * appearance. Without this, Play on element A, select B, Stop sent
 * {@code animation: none} to B while A kept its inline
 * {@code animation:} property; the next Play with the same name
 * resurrected A's animation alongside B's, and every retarget left
 * another element armed.
 *
 * <p>Pure state machine so the rules are plain unit tests; the panel
 * wires it at exactly three sites (Play, Scrub, Stop — gate-pinned).
 * Thread-safe because Play runs on the EDT while the strip's drag
 * callbacks may re-enter through repaints.
 */
public final class MotionTargetGuard {

    private java.util.List<Integer> appliedPath;

    /**
     * About to apply the preview to {@code newPath}: returns the OLD
     * path whose inline animation must be cleared first, or null when
     * nothing was applied elsewhere (first play, or same element).
     * Remembers {@code newPath} as the applied target.
     */
    public synchronized java.util.List<Integer> retarget(java.util.List<Integer> newPath) {
        java.util.List<Integer> previous = appliedPath;
        appliedPath = newPath;
        if (previous == null || previous.equals(newPath)) {
            return null;
        }
        return previous;
    }

    /**
     * Stop: the path whose animation must be cleared — the REMEMBERED
     * target when one exists (Stop stops what is playing, not what is
     * selected), else {@code selectionFallback} (may be null: nothing
     * to clear). Forgets the target either way.
     */
    public synchronized java.util.List<Integer> stopTarget(java.util.List<Integer> selectionFallback) {
        java.util.List<Integer> target = appliedPath != null ? appliedPath : selectionFallback;
        appliedPath = null;
        return target;
    }

    /** The page was reset (navigation, preview cleared): forget. */
    public synchronized void clear() {
        appliedPath = null;
    }
}
