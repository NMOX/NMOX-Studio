package org.nmox.studio.rack.model;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

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
}
