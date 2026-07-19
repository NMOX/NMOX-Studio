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
    @DisplayName("unmatchedAgainst names what the rack lost — the unsaved-patch case (v1.96.0)")
    void unmatchedAgainstFindsUnsavedPatchEntries() {
        Rack rack = new Rack();
        try {
            RackDevice monitor = DeviceType.CONSOLE.create();
            rack.addDevice(monitor);

            // kill -9 before any Save Patch: the snapshot names a device
            // the relaunched rack simply does not have
            SessionState state = new SessionState(rack.getProjectDir().getAbsolutePath(), 1,
                    List.of(new SessionState.Entry(0, "console", "MONITOR"),
                            new SessionState.Entry(2, "run", "IGNITION")));
            assertThat(state.matchAgainst(rack)).containsExactly(monitor);
            assertThat(state.unmatchedAgainst(rack))
                    .as("the lost IGNITION must be reported, not silently dropped")
                    .containsExactly(new SessionState.Entry(2, "run", "IGNITION"));

            // a fully matched snapshot reports nothing lost
            SessionState allThere = new SessionState("x", 1,
                    List.of(new SessionState.Entry(0, "console", "MONITOR")));
            assertThat(allThere.unmatchedAgainst(rack)).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The resume click re-creates lost devices from the catalog and skips unknown types")
    void resumeSessionRecreatesLostDevices(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        Rack rack = new Rack();
        try {
            rack.setProjectDir(dir.toFile()); // no manifest: resume() refuses to spawn
            rack.addDevice(DeviceType.CONSOLE.create());

            RackService.resumeSession(rack, List.of(), List.of(
                    new SessionState.Entry(2, "run", "IGNITION"),
                    new SessionState.Entry(1, "gone-plugin-type", "GHOST")));

            assertThat(rack.getDevices())
                    .as("IGNITION re-created (index clamped), the uninstalled type skipped")
                    .hasSize(2);
            assertThat(rack.getDevices().get(1).getTypeId()).isEqualTo("run");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A snapshot is a fact: the running list cannot be mutated after capture")
    void runningListIsImmutable() {
        java.util.List<SessionState.Entry> mutable = new java.util.ArrayList<>();
        mutable.add(new SessionState.Entry(0, "console", "MONITOR"));
        SessionState state = new SessionState("/p", 1L, mutable);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> state.running().add(new SessionState.Entry(1, "x", "X")))
                .isInstanceOf(UnsupportedOperationException.class);
        // and the constructor copied: editing the caller's list changes nothing
        mutable.clear();
        assertThat(state.running()).hasSize(1);
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

    @Test
    @DisplayName("a MissingDevice never matches for resume — no dead-click balloon (ledger 44)")
    void missingDeviceIsNeverResumed() {
        Rack rack = new Rack();
        // a patch whose device at index 0 is an uninstalled plugin: RackIO
        // mounts a MissingDevice carrying the plugin's type id
        rack.addDevice(new org.nmox.studio.rack.model.MissingDevice("com.acme.gone"));
        rack.addDevice(DeviceType.CONSOLE.create());

        // the session recorded that plugin device as live at the crash
        SessionState state = new SessionState(rack.getProjectDir().getAbsolutePath(), 1,
                List.of(new SessionState.Entry(0, "com.acme.gone", "WIDGET")));
        assertThat(state.matchAgainst(rack))
                .as("a placeholder can never resume anything, so it must not be offered")
                .isEmpty();
    }
}
