package org.nmox.studio.ui.irc.protocol;

import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code @time=} tag decoder: ISO instants become local wall-clock
 * times in the given zone; garbage and absence degrade to empty (the
 * caller falls back to "now").
 */
class ServerTimeTest {

    @Test
    @DisplayName("A spec-shaped instant renders in the requested zone")
    void parsesInZone() {
        assertThat(ServerTime.localTime("2026-07-30T12:34:56.789Z", ZoneId.of("UTC")))
                .contains(LocalTime.of(12, 34, 56, 789_000_000));
        assertThat(ServerTime.localTime("2026-07-30T12:00:00Z", ZoneId.of("+02:00")))
                .contains(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("Missing or garbled values are empty, never a crash")
    void honestEmpties() {
        assertThat(ServerTime.localTime(null, ZoneId.of("UTC"))).isEmpty();
        assertThat(ServerTime.localTime("", ZoneId.of("UTC"))).isEmpty();
        assertThat(ServerTime.localTime("not-a-time", ZoneId.of("UTC"))).isEmpty();
        assertThat(ServerTime.localTime("2026-07-30", ZoneId.of("UTC")))
                .as("a bare date is not an instant")
                .isEmpty();
    }
}
