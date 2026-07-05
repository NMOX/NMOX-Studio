package org.nmox.studio.apiclient.api;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The re-aim storm laws, tested through the listener seam: every rack
 * projectChanged lands in {@link ProjectRebind#shouldLoad}, every
 * finished off-EDT read in {@link ProjectRebind#shouldApply}.
 */
class ProjectRebindTest {

    private final File a = new File("/projects/a");
    private final File b = new File("/projects/b");
    private final File c = new File("/projects/c");

    @Test
    @DisplayName("equality guard: re-aiming to the bound dir loads nothing")
    void equalityGuard() {
        ProjectRebind rebind = new ProjectRebind(a);
        assertThat(rebind.shouldLoad(a)).isFalse();
        assertThat(rebind.boundDir()).isEqualTo(a);
    }

    @Test
    @DisplayName("a real re-aim loads once, then binds")
    void plainRebind() {
        ProjectRebind rebind = new ProjectRebind(a);
        assertThat(rebind.shouldLoad(b)).isTrue();
        assertThat(rebind.shouldApply(b, b)).isTrue();
        assertThat(rebind.boundDir()).isEqualTo(b);
        // bound now — the same aim is the equality guard's business
        assertThat(rebind.shouldLoad(b)).isFalse();
    }

    @Test
    @DisplayName("bounded reaction: an event storm for one aim is one load")
    void stormIsOneLoad() {
        ProjectRebind rebind = new ProjectRebind(a);
        int loads = 0;
        for (int i = 0; i < 100; i++) {
            if (rebind.shouldLoad(b)) {
                loads++;
            }
        }
        assertThat(loads).isEqualTo(1);
        assertThat(rebind.shouldApply(b, b)).isTrue();
    }

    @Test
    @DisplayName("the newest aim wins: a stale load never binds")
    void staleLoadNeverBinds() {
        ProjectRebind rebind = new ProjectRebind(a);
        assertThat(rebind.shouldLoad(b)).isTrue();
        // the rack moved on to c before b's read landed
        assertThat(rebind.shouldLoad(c)).isTrue();
        assertThat(rebind.shouldApply(b, c)).isFalse();
        assertThat(rebind.boundDir()).isEqualTo(a); // b never bound
        assertThat(rebind.shouldApply(c, c)).isTrue();
        assertThat(rebind.boundDir()).isEqualTo(c);
    }

    @Test
    @DisplayName("aiming back home mid-flight: the old workspace never reloads")
    void reAimBackMidFlight() {
        ProjectRebind rebind = new ProjectRebind(a);
        assertThat(rebind.shouldLoad(b)).isTrue();
        assertThat(rebind.shouldLoad(a)).isFalse(); // home again — equality guard
        assertThat(rebind.shouldApply(b, a)).isFalse(); // b's read is stale
        assertThat(rebind.boundDir()).isEqualTo(a);
    }

    @Test
    @DisplayName("after a bounce A→B→A, a fresh re-aim to B loads again")
    void bounceThenRebind() {
        ProjectRebind rebind = new ProjectRebind(a);
        assertThat(rebind.shouldLoad(b)).isTrue();
        assertThat(rebind.shouldLoad(a)).isFalse();
        // the second B re-aim is a NEW move — it must load even though
        // the first B read is still in flight
        assertThat(rebind.shouldLoad(b)).isTrue();
        assertThat(rebind.shouldApply(b, b)).isTrue();
        // the first (stale) read lands last and is refused: already bound
        assertThat(rebind.shouldApply(b, b)).isFalse();
        assertThat(rebind.boundDir()).isEqualTo(b);
    }
}
