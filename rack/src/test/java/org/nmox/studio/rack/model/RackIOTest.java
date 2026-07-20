package org.nmox.studio.rack.model;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RackIOTest {

    @Test
    @DisplayName("Should round-trip devices, control state and cables through JSON")
    void shouldRoundTripPatch() {
        Rack rack = new Rack();
        RackDevice master = DeviceType.MASTER.create();
        RackDevice build = DeviceType.BUILD.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(master);
        rack.addDevice(build);
        rack.addDevice(console);
        rack.connect(master.getPort("trig1"), build.getPort("run"));
        rack.connect(build.getPort("out"), console.getPort("in"));

        // twist a knob so state has something non-default
        build.applyState(java.util.Map.of("tool", "1", "watch", "true"));

        JSONObject json = RackIO.toJson(rack);

        Rack restored = new Rack();
        RackIO.fromJson(restored, json);

        assertThat(restored.getDevices()).hasSize(3);
        assertThat(restored.getDevices().get(0).getTypeId()).isEqualTo("master");
        assertThat(restored.getDevices().get(1).getTypeId()).isEqualTo("build");
        assertThat(restored.getDevices().get(2).getTypeId()).isEqualTo("console");
        assertThat(restored.getCables()).hasSize(2);
        assertThat(restored.getDevices().get(1).getState())
                .containsEntry("tool", "1")
                .containsEntry("watch", "true");
    }

    @Test
    @DisplayName("Should create every cataloged device type")
    void shouldCreateEveryDeviceType() {
        for (DeviceType type : DeviceType.values()) {
            RackDevice device = type.create();
            assertThat(device).as(type.name()).isNotNull();
            assertThat(device.getTypeId()).isEqualTo(type.getId());
            assertThat(DeviceType.byId(type.getId())).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("A valid patch loads from disk through load()")
    void shouldLoadValidPatchFromFile(@org.junit.jupiter.api.io.TempDir java.io.File dir)
            throws Exception {
        Rack rack = new Rack();
        rack.addDevice(DeviceType.MASTER.create());
        rack.addDevice(DeviceType.BUILD.create());
        java.io.File file = new java.io.File(dir, RackIO.DEFAULT_FILENAME);
        RackIO.save(rack, file);

        Rack restored = new Rack();
        RackIO.load(restored, file);
        assertThat(restored.getDevices()).hasSize(2);
        assertThat(restored.getDevices().get(0).getTypeId()).isEqualTo("master");
    }

    @Test
    @DisplayName("A corrupt patch is kept as .bak, the rack is reset, and no stale device survives")
    void corruptPatchIsBackedUpAndRackReset(@org.junit.jupiter.api.io.TempDir java.io.File dir)
            throws Exception {
        java.io.File file = new java.io.File(dir, RackIO.DEFAULT_FILENAME);
        String corruptBytes = "{ this is not valid json ]";
        java.nio.file.Files.writeString(file.toPath(), corruptBytes);

        // a rack already holding the PREVIOUS project's devices
        Rack rack = new Rack();
        rack.addDevice(DeviceType.MASTER.create());
        rack.addDevice(DeviceType.BUILD.create());

        // load must fail loudly, but leave the rack in a known-empty state —
        // never the previous project's devices aimed at this patch's project
        assertThatThrownBy(() -> RackIO.load(rack, file))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining(".bak");
        assertThat(rack.getDevices())
                .as("no stale device from the previous project survives a corrupt load")
                .isEmpty();

        // the user's bytes are preserved as .bak, and the original is gone so
        // the next save writes a fresh valid file instead of clobbering theirs
        java.io.File bak = new java.io.File(dir, RackIO.DEFAULT_FILENAME + ".bak");
        assertThat(bak).exists();
        assertThat(java.nio.file.Files.readString(bak.toPath())).isEqualTo(corruptBytes);
        assertThat(file).doesNotExist();
    }
}
