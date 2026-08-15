package org.nmox.studio.ui.browser.fx;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.ui.browser.devtools.Keyframes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The timeline strip's pure editing rules (v2.12.0): stops clamp to
 * 0..100, a drag can never pass or merge a neighboring stop (a drag
 * must not silently delete a keyframe), the frame export regroups
 * tracks by percent, and a load round-trips a Keyframes frame list.
 */
class TimelineStripModelTest {

    @Test
    @DisplayName("a moved stop clamps strictly between its neighbors — never passes, never merges")
    void moveClampsBetweenNeighbors() {
        TimelineStrip.Model m = new TimelineStrip.Model();
        m.setStop("opacity", 0, "0");
        m.setStop("opacity", 50, "0.5");
        m.setStop("opacity", 100, "1");
        assertThat(m.moveStop("opacity", 50, 100)).isEqualTo(99);
        assertThat(m.moveStop("opacity", 99, -20)).isEqualTo(1);
        assertThat(m.stops("opacity")).containsKeys(0, 1, 100);
        assertThat(m.stops("opacity")).hasSize(3);
        assertThat(m.moveStop("opacity", 77, 10)).isEqualTo(-1);
    }

    @Test
    @DisplayName("frames regroup tracks by percent, and a load round-trips")
    void framesRegroupAndRoundTrip() {
        TimelineStrip.Model m = new TimelineStrip.Model();
        m.setStop("transform", 0, "translateX(0)");
        m.setStop("transform", 100, "translateX(40px)");
        m.setStop("opacity", 0, "0");
        List<Keyframes.Frame> frames = m.frames();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).percent()).isZero();
        assertThat(frames.get(0).props()).containsEntry("transform", "translateX(0)")
                .containsEntry("opacity", "0");
        assertThat(frames.get(1).props()).containsOnly(
                Map.entry("transform", "translateX(40px)"));

        TimelineStrip.Model n = new TimelineStrip.Model();
        n.load(frames);
        assertThat(n.frames()).isEqualTo(frames);
        assertThat(n.properties()).contains("transform", "opacity");
    }

    @Test
    @DisplayName("stops clamp to 0..100 on set; remove is total")
    void clampAndRemove() {
        TimelineStrip.Model m = new TimelineStrip.Model();
        m.setStop("opacity", 140, "1");
        m.setStop("opacity", -3, "0");
        assertThat(m.stops("opacity")).containsOnlyKeys(0, 100);
        m.removeStop("opacity", 100);
        assertThat(m.stops("opacity")).containsOnlyKeys(0);
        m.removeTrack("opacity");
        assertThat(m.properties()).isEmpty();
    }
}
