package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresentationTest {

    @AfterEach
    void off() {
        Presentation.setOn(false);
    }

    @Test
    @DisplayName("listeners hear each real change with its value, never a no-op flip, and detach symmetrically")
    void listeners() {
        List<Boolean> heard = new ArrayList<>();
        Consumer<Boolean> l = heard::add;
        int before = Presentation.listenerCount();
        Presentation.addListener(l);
        Presentation.setOn(true);
        Presentation.setOn(true);   // no change, no call
        Presentation.setOn(false);
        assertThat(heard).containsExactly(true, false);
        Presentation.removeListener(l);
        assertThat(Presentation.listenerCount()).isEqualTo(before);
        Presentation.setOn(true);
        assertThat(heard).containsExactly(true, false); // detached: silent
    }

    @Test
    @DisplayName("a late subscriber reads the current state")
    void lateSubscriber() {
        Presentation.setOn(true);
        assertThat(Presentation.isOn()).isTrue();
    }

    @Test
    @DisplayName("the browser's presenting zoom multiplies the user's own zoom and leaving restores it exactly")
    void browserZoom() {
        assertThat(Presentation.browserZoom(1.0, true)).isEqualTo(1.5);
        assertThat(Presentation.browserZoom(0.8, true)).isCloseTo(1.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Presentation.browserZoom(0.8, false)).isEqualTo(0.8);
    }
}
