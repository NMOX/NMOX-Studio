package org.nmox.studio.rack.model;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two devices of one type are two devices (3.4, question 1; the hostile
 * review of the first 3.4 cut).
 *
 * <p>Type and title were the first 3.4 answer to a teammate moving the slots
 * under a cable, and they cannot tell two PURITYs apart. Bob saved
 * {@code [cmd, reflex, lintA, lintB]} with {@code reflex(1) → lintA(2)};
 * Alice removed {@code cmd}; git merged cleanly. The old index 2 now named
 * lintB — "lint / PURITY", exactly as recorded — so the cable was trusted
 * and landed on the wrong device, reported {@code followed=0 dropped=0}.
 * Every device now carries an id and every cable end names it.
 */
class CableSameTypeTest {

    private static JSONObject device(String type) {
        return new JSONObject().put(RackIO.TYPE, type).put(RackIO.STATE, new JSONObject());
    }

    private static JSONObject typedCable(int from, int to) {
        return new JSONObject()
                .put(RackIO.FROM_DEVICE, from).put(RackIO.FROM_PORT, "changed")
                .put(RackIO.FROM_TYPE, "reflex").put(RackIO.FROM_TITLE, "REFLEX")
                .put(RackIO.TO_DEVICE, to).put(RackIO.TO_PORT, "run")
                .put(RackIO.TO_TYPE, "lint").put(RackIO.TO_TITLE, "PURITY");
    }

    @Test
    @DisplayName("the review's merge, without ids: the index lands on lint B, so the cable is dropped and counted — never rewired")
    void typedCableWithTwoCandidatesIsNotTrustedByIndex() {
        JSONArray devices = new JSONArray();
        for (String t : List.of("reflex", "lint", "lint")) {
            devices.put(device(t));
        }
        JSONObject merged = new JSONObject().put(RackIO.VERSION, 1).put(RackIO.DEVICES, devices)
                .put(RackIO.CABLES, new JSONArray().put(typedCable(1, 2)));
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, merged);
            RackDevice lintB = rack.getDevices().get(2);
            assertThat(rack.getCables()).as("Bob patched lint A; lint B is a different device of the same type")
                    .noneMatch(c -> c.getTo().getDevice() == lintB);
            assertThat(rack.getCables()).isEmpty();
            assertThat(report.dropped()).as("the loss is counted, so it is said").isEqualTo(1);
            assertThat(report.quiet()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("with ids, the same merge follows Bob's cable to lint A wherever it now sits, and says it followed")
    void idsFollowTheDeviceNotTheSlot() {
        Rack bob = new Rack();
        JSONObject saved;
        String lintAId;
        try {
            RackDevice cmd = DeviceType.CMD.create();
            RackDevice reflex = DeviceType.REFLEX.create();
            RackDevice lintA = DeviceType.LINT.create();
            RackDevice lintB = DeviceType.LINT.create();
            bob.addDevice(cmd);
            bob.addDevice(reflex);
            bob.addDevice(lintA);
            bob.addDevice(lintB);
            bob.connect(reflex.getPort("changed"), lintA.getPort("run"));
            saved = RackIO.toJson(bob);
            lintAId = lintA.getUid();
        } finally {
            bob.shutdown();
        }
        // Alice's side of the merge: the first device's line is gone, Bob's
        // cable line (indices 1 → 2) arrives unchanged
        saved.getJSONArray(RackIO.DEVICES).remove(0);

        Rack merged = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(merged, saved);
            assertThat(merged.getCables()).hasSize(1);
            RackDevice to = merged.getCables().get(0).getTo().getDevice();
            assertThat(to.getUid()).as("the cable reaches the device it was patched to").isEqualTo(lintAId);
            assertThat(merged.getDevices().indexOf(to)).isEqualTo(1);
            assertThat(report.followed()).isEqualTo(1);
            assertThat(report.dropped()).isZero();
        } finally {
            merged.shutdown();
        }
    }

    @Test
    @DisplayName("a device keeps its id through a save and a load; the cable names both ends by it")
    void idsRoundTrip() {
        Rack rack = new Rack();
        Rack again = new Rack();
        try {
            RackDevice reflex = DeviceType.REFLEX.create();
            RackDevice lint = DeviceType.LINT.create();
            rack.addDevice(reflex);
            rack.addDevice(lint);
            rack.connect(reflex.getPort("changed"), lint.getPort("run"));
            JSONObject json = RackIO.toJson(rack);
            JSONObject c = json.getJSONArray(RackIO.CABLES).getJSONObject(0);
            assertThat(c.getString(RackIO.FROM_ID)).isEqualTo(reflex.getUid());
            assertThat(c.getString(RackIO.TO_ID)).isEqualTo(lint.getUid());
            assertThat(json.getJSONArray(RackIO.DEVICES).getJSONObject(1).getString(RackIO.ID))
                    .isEqualTo(lint.getUid());

            RackIO.fromJson(again, json);
            assertThat(again.getDevices()).extracting(RackDevice::getUid)
                    .containsExactly(reflex.getUid(), lint.getUid());
        } finally {
            rack.shutdown();
            again.shutdown();
        }
    }

    @Test
    @DisplayName("a cable whose device id is gone is dropped, even when its old slot holds the same type")
    void aVanishedIdIsNeverReplacedByTheSlot() {
        JSONArray devices = new JSONArray()
                .put(device("reflex").put(RackIO.ID, "r1"))
                .put(device("lint").put(RackIO.ID, "l2"));
        JSONObject cable = typedCable(0, 1).put(RackIO.FROM_ID, "r1").put(RackIO.TO_ID, "l-removed");
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, new JSONObject().put(RackIO.VERSION, 1)
                    .put(RackIO.DEVICES, devices).put(RackIO.CABLES, new JSONArray().put(cable)));
            assertThat(rack.getCables()).isEmpty();
            assertThat(report.dropped()).isEqualTo(1);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a keep-both merge duplicating a device heals at parse: the first keeps its id, the copy gets a new one")
    void duplicateIdsHeal() {
        JSONArray devices = new JSONArray()
                .put(device("lint").put(RackIO.ID, "same"))
                .put(device("lint").put(RackIO.ID, "same"))
                .put(device("reflex"));
        Rack rack = new Rack();
        try {
            RackIO.fromJson(rack, new JSONObject().put(RackIO.VERSION, 1).put(RackIO.DEVICES, devices));
            List<RackDevice> ds = rack.getDevices();
            assertThat(ds.get(0).getUid()).isEqualTo("same");
            assertThat(ds.get(1).getUid()).isNotEqualTo("same").isNotBlank();
            assertThat(ds.get(2).getUid()).as("a device saved before ids is given one").isNotBlank();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the reader ignores keys it does not know, so a 3.3 build — which reads neither id nor type — loads a 3.4 patch by index")
    void unknownKeysAreIgnored() {
        JSONArray devices = new JSONArray()
                .put(device("reflex").put(RackIO.ID, "r").put("fromTheFuture", 7))
                .put(device("lint").put(RackIO.ID, "l"));
        JSONObject cable = new JSONObject()
                .put(RackIO.FROM_DEVICE, 0).put(RackIO.FROM_PORT, "changed")
                .put(RackIO.TO_DEVICE, 1).put(RackIO.TO_PORT, "run").put("wireColour", "red");
        Rack rack = new Rack();
        try {
            RackIO.CableReport report = RackIO.fromJson(rack, new JSONObject().put(RackIO.VERSION, 1)
                    .put(RackIO.DEVICES, devices).put(RackIO.CABLES, new JSONArray().put(cable)).put("later", true));
            assertThat(rack.getCables()).hasSize(1);
            assertThat(report.quiet()).isTrue();
        } finally {
            rack.shutdown();
        }
    }
}
