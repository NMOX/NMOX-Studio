package org.nmox.studio.rack.model;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cables survive a teammate's device edits (3.4, question 1).
 *
 * <p>A cable names its ends by slot INDEX. Alice removes the rack's first
 * device and Bob, on his branch, patches {@code reflex.changed → lint.run};
 * git merges the two edits cleanly — different lines — and Bob's indices now
 * point one slot past the devices he meant. Measured before 3.4, the merged
 * patch loaded as {@code reflex → test.run}: silently rewired, because the
 * index still named a device and nothing asked which one. When the index
 * named no slot at all the cable vanished with a log line only.
 */
class CableIdentityTest {

    private static JSONObject device(String type) {
        return new JSONObject().put(RackIO.TYPE, type).put(RackIO.STATE, new JSONObject());
    }

    private static JSONObject cable(int from, String fromPort, String fromType, String fromTitle,
            int to, String toPort, String toType, String toTitle) {
        JSONObject c = new JSONObject()
                .put(RackIO.FROM_DEVICE, from).put(RackIO.FROM_PORT, fromPort)
                .put(RackIO.TO_DEVICE, to).put(RackIO.TO_PORT, toPort);
        if (fromType != null) {
            c.put(RackIO.FROM_TYPE, fromType).put(RackIO.FROM_TITLE, fromTitle);
        }
        if (toType != null) {
            c.put(RackIO.TO_TYPE, toType).put(RackIO.TO_TITLE, toTitle);
        }
        return c;
    }

    private static JSONObject patch(List<String> types, JSONObject... cables) {
        JSONArray devices = new JSONArray();
        types.forEach(t -> devices.put(device(t)));
        JSONArray cableArr = new JSONArray();
        for (JSONObject c : cables) {
            cableArr.put(c);
        }
        return new JSONObject().put(RackIO.VERSION, 1).put(RackIO.DEVICES, devices).put(RackIO.CABLES, cableArr);
    }

    private static String wiring(Rack rack) {
        StringBuilder sb = new StringBuilder();
        for (Cable c : rack.getCables()) {
            sb.append(c.getFrom().getDevice().getTypeId()).append('.').append(c.getFrom().getId())
                    .append("->").append(c.getTo().getDevice().getTypeId()).append('.')
                    .append(c.getTo().getId()).append(';');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("a saved cable records the type and title of both ends")
    void savedCablesNameTheirDevices() {
        Rack rack = new Rack();
        try {
            RackDevice reflex = DeviceType.REFLEX.create();
            RackDevice lint = DeviceType.LINT.create();
            rack.addDevice(reflex);
            rack.addDevice(lint);
            rack.connect(reflex.getPort("changed"), lint.getPort("run"));
            JSONObject c = RackIO.toJson(rack).getJSONArray(RackIO.CABLES).getJSONObject(0);
            assertThat(c.getString(RackIO.FROM_TYPE)).isEqualTo("reflex");
            assertThat(c.getString(RackIO.FROM_TITLE)).isEqualTo("REFLEX");
            assertThat(c.getString(RackIO.TO_TYPE)).isEqualTo("lint");
            assertThat(c.getString(RackIO.TO_TITLE)).isEqualTo("PURITY");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the merged patch: Bob's cable follows its devices past Alice's removal, and says so")
    void cableFollowsItsDeviceAfterATeammateRemovesOne() {
        // Bob saved [console, reflex, lint, test] with reflex(1).changed → lint(2).run;
        // Alice removed the console; git merged Alice's device list with Bob's cable
        JSONObject merged = patch(List.of("reflex", "lint", "test"),
                cable(1, "changed", "reflex", "REFLEX", 2, "run", "lint", "PURITY"));
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, merged);
            assertThat(wiring(rack))
                    .as("the cable reaches the devices it was patched to — never test.run by position")
                    .isEqualTo("reflex.changed->lint.run;");
            assertThat(report.followed()).isEqualTo(1);
            assertThat(report.dropped()).isZero();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a cable whose device the teammate removed is dropped, and counted — not rewired")
    void cableToARemovedDeviceIsDroppedAndCounted() {
        // lint was removed; the saved index 2 now names 'test', of the wrong type
        JSONObject merged = patch(List.of("reflex", "test", "test"),
                cable(0, "changed", "reflex", "REFLEX", 2, "run", "lint", "PURITY"));
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, merged);
            assertThat(rack.getCables()).as("no cable lands on a device it was never patched to").isEmpty();
            assertThat(report.dropped()).isEqualTo(1);
            assertThat(report.quiet()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("two candidates of the recorded type: refuse rather than guess")
    void twoCandidatesAreRefused() {
        JSONObject merged = patch(List.of("reflex", "cmd", "lint", "lint"),
                cable(0, "changed", "reflex", "REFLEX", 1, "run", "lint", "PURITY"));
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, merged);
            assertThat(rack.getCables()).isEmpty();
            assertThat(report.dropped()).isEqualTo(1);
            assertThat(report.followed()).isZero();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a patch written before 3.4 carries no types and loads by index, exactly as before")
    void legacyPatchLoadsByIndex() {
        JSONObject old = patch(List.of("reflex", "lint", "test"),
                cable(0, "changed", null, null, 2, "run", null, null));
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, old);
            assertThat(wiring(rack)).isEqualTo("reflex.changed->test.run;");
            assertThat(report.quiet()).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a cable whose slot still holds its device loads quietly")
    void matchingSlotIsQuiet() {
        JSONObject saved = patch(List.of("reflex", "lint"),
                cable(0, "changed", "reflex", "REFLEX", 1, "run", "lint", "PURITY"));
        Rack rack = new Rack();
        try {
            assertThat(RackIO.fromJson(rack, saved).quiet()).isTrue();
            assertThat(wiring(rack)).isEqualTo("reflex.changed->lint.run;");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("every dropped cable is counted, including slots the patch does not have")
    void outOfRangeSlotsAreCounted() {
        JSONObject broken = patch(List.of("reflex"),
                cable(0, "changed", null, null, 7, "run", null, null));
        Rack rack = new Rack();
        try {
            assertThat(RackIO.fromJson(rack, broken).dropped()).isEqualTo(1);
        } finally {
            rack.shutdown();
        }
    }
}
