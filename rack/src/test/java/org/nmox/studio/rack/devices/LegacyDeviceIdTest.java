package org.nmox.studio.rack.devices;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

/**
 * v2.95.0: the ORACLE device became KVASIR. A patch saved before the
 * rename carries {@code "type": "oracle"}; it must load as the same
 * device — never as a MissingDevice placeholder — and the next save
 * writes the current id.
 */
class LegacyDeviceIdTest {

    @Test
    @DisplayName("the catalog resolves the pre-rename id to KVASIR")
    void catalogAlias() {
        assertThat(DeviceCatalog.byId("oracle")).isPresent();
        assertThat(DeviceCatalog.byId("oracle").get().id()).isEqualTo("kvasir");
        assertThat(DeviceCatalog.byId("kvasir")).isPresent();
    }

    @Test
    @DisplayName("a saved patch naming oracle loads a KVASIR device and re-saves as kvasir")
    void legacyPatchLoads() {
        JSONObject patch = new JSONObject()
                .put("version", 1)
                .put("devices", new org.json.JSONArray().put(new JSONObject()
                        .put("type", "oracle").put("state", new JSONObject())))
                .put("cables", new org.json.JSONArray());
        Rack rack = new Rack();
        RackIO.fromJson(rack, patch);
        assertThat(rack.getDevices()).hasSize(1);
        assertThat(rack.getDevices().get(0)).isInstanceOf(KvasirDevice.class);
        assertThat(RackIO.toJson(rack).getJSONArray("devices").getJSONObject(0).getString("type"))
                .isEqualTo("kvasir");
    }
}
