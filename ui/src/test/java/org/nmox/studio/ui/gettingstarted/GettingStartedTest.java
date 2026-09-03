package org.nmox.studio.ui.gettingstarted;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GettingStartedTest {

    @Test
    @DisplayName("Five steps with stable keys, counted and phrased as 'n of 5'")
    void arithmetic() {
        assertThat(GettingStarted.STEPS).extracting(GettingStarted.Step::key)
                .containsExactly("project", "run", "serve", "oracle", "learn");
        assertThat(GettingStarted.progress(Set.of())).isEqualTo("0 of 5");
        assertThat(GettingStarted.progress(Set.of("project", "learn"))).isEqualTo("2 of 5");
        // an unknown key never counts
        assertThat(GettingStarted.done(Set.of("project", "bogus"))).isEqualTo(1);
    }

    @Test
    @DisplayName("next() is the first untouched step in order; all five done means no next")
    void next() {
        assertThat(GettingStarted.next(Set.of()).key()).isEqualTo("project");
        assertThat(GettingStarted.next(Set.of("project", "serve")).key()).isEqualTo("run");
        assertThat(GettingStarted.next(Set.of("project", "run", "serve", "oracle", "learn"))).isNull();
        assertThat(GettingStarted.allDone(Set.of("project", "run", "serve", "oracle", "learn"))).isTrue();
    }

    @Test
    @DisplayName("The column shows only while something is left AND the user has not hidden it")
    void visibility() {
        assertThat(GettingStarted.visible(Set.of(), false)).isTrue();
        assertThat(GettingStarted.visible(Set.of(), true)).isFalse();
        assertThat(GettingStarted.visible(Set.of("project", "run", "serve", "oracle", "learn"), false)).isFalse();
    }
}
