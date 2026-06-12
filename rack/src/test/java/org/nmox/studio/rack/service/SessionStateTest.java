package org.nmox.studio.rack.service;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/** The resurrection contract: state round-trips and matches safely. */
class SessionStateTest {

    @Test
    @DisplayName("Session state round-trips through JSON")
    void roundTrips() {
        SessionState state = new SessionState("/p", 123L, List.of(
                new SessionState.Entry(2, "dev-server", "SURGE"),
                new SessionState.Entry(0, "console", "MONITOR")));
        SessionState back = SessionState.fromJson(state.toJson());
        assertThat(back).isEqualTo(state);
    }

    @Test
    @DisplayName("Matching is positional AND typed: rearranged racks never resurrect the wrong unit")
    void matchesSafely() {
        Rack rack = new Rack();
        try {
            RackDevice monitor = DeviceType.CONSOLE.create();
            RackDevice surge = DeviceType.DEV_SERVER.create();
            rack.addDevice(monitor);
            rack.addDevice(surge);

            // session recorded SURGE at index 1: exact match resumes
            SessionState good = new SessionState(rack.getProjectDir().getAbsolutePath(), 1,
                    List.of(new SessionState.Entry(1, "dev-server", "SURGE")));
            assertThat(good.matchAgainst(rack)).containsExactly(surge);

            // same index, different type (patch was rearranged): no match
            SessionState moved = new SessionState("x", 1,
                    List.of(new SessionState.Entry(0, "dev-server", "SURGE")));
            assertThat(moved.matchAgainst(rack)).isEmpty();

            // index out of range: no match, no exception
            SessionState gone = new SessionState("x", 1,
                    List.of(new SessionState.Entry(9, "dev-server", "SURGE")));
            assertThat(gone.matchAgainst(rack)).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Corrupt JSON is a null session, not an exception")
    void corruptJsonIsNull() {
        assertThat(SessionState.fromJson("{nope")).isNull();
        assertThat(SessionState.fromJson("{}")).isNull();
    }

    @Test
    @DisplayName("Freshness: week-old sessions are history, not intent")
    void staleSessionsExpire() {
        assertThat(new SessionState("/p", System.currentTimeMillis(), List.of()).fresh()).isTrue();
        assertThat(new SessionState("/p", System.currentTimeMillis() - 8L * 24 * 3600 * 1000,
                List.of()).fresh()).isFalse();
    }
}
