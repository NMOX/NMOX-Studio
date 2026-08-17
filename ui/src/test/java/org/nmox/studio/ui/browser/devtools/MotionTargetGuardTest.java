package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Motion pane's target law (v2.15.0 arc review): the preview
 * belongs to the element that started it. The bug these pin: Play on
 * A, select B, Stop cleared B while A kept animating; and a retarget
 * left A's inline {@code animation:} armed for resurrection by the
 * next same-named injection.
 */
class MotionTargetGuardTest {

    @Test
    @DisplayName("retargeting to a DIFFERENT element names the old one for clearing")
    void retargetNamesStaleElement() {
        MotionTargetGuard guard = new MotionTargetGuard();
        assertThat(guard.retarget(java.util.List.of(0,1))).as("first play: nothing stale").isNull();
        assertThat(guard.retarget(java.util.List.of(0,1))).as("same element: nothing stale").isNull();
        assertThat(guard.retarget(java.util.List.of(0,2)))
                .as("moved to another element: the OLD one must be cleared")
                .isEqualTo(java.util.List.of(0,1));
        assertThat(guard.retarget(java.util.List.of(0,1)))
                .as("moving back names the intermediate target")
                .isEqualTo(java.util.List.of(0,2));
    }

    @Test
    @DisplayName("Stop clears what is PLAYING, not what is selected")
    void stopUsesRememberedTarget() {
        MotionTargetGuard guard = new MotionTargetGuard();
        guard.retarget(java.util.List.of(0,1));
        // the user has since selected a different element in the DOM tab
        assertThat(guard.stopTarget(java.util.List.of(0,9)))
                .as("the animating element wins over the selection")
                .isEqualTo(java.util.List.of(0,1));
        // guard forgot: a second Stop falls back to the selection
        assertThat(guard.stopTarget(java.util.List.of(0,9))).isEqualTo(java.util.List.of(0,9));
        // and with no selection either, there is nothing to clear
        assertThat(guard.stopTarget(null)).isNull();
    }

    @Test
    @DisplayName("clear() forgets — a page reset must not clear an element of the NEW page")
    void clearForgets() {
        MotionTargetGuard guard = new MotionTargetGuard();
        guard.retarget(java.util.List.of(0,1));
        guard.clear();
        assertThat(guard.stopTarget(null)).isNull();
        assertThat(guard.retarget(java.util.List.of(0,1))).isNull();
    }
}
