package org.nmox.studio.rack.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shutdown reaper must track exactly the racks that are still alive:
 * a per-instance shutdown hook would pin every rack a test suite ever
 * constructed until JVM exit. Counts are asserted as deltas because other
 * tests in the same JVM may hold live racks of their own.
 */
class RackReaperTest {

    @Test
    @DisplayName("shutdown() unregisters the rack from the shutdown reaper")
    void shutdownUnregistersFromReaper() {
        int before = Rack.reaperTrackedCount();
        List<Rack> racks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            racks.add(new Rack());
        }
        assertThat(Rack.reaperTrackedCount()).isEqualTo(before + 5);
        for (Rack rack : racks) {
            rack.shutdown();
        }
        assertThat(Rack.reaperTrackedCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("A live rack is tracked; a shut-down rack is not")
    void reaperTracksOnlyLiveRacks() {
        Rack rack = new Rack();
        assertThat(Rack.reaperTracks(rack)).isTrue();
        rack.shutdown();
        assertThat(Rack.reaperTracks(rack)).isFalse();
    }
}
