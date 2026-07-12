package org.nmox.studio.rack.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A patch that names a device this install cannot answer — a plugin
 * device from the future SPI, or a newer built-in opened in an older
 * Studio — must survive load and re-save UNTOUCHED. The old code
 * dropped the unknown device but resolved cables by positional index,
 * so every cable saved after the stranger silently re-attached to the
 * wrong device: the load didn't just lose one device, it rewired the
 * patch. These tests fail on that code.
 */
class MissingDeviceTest {

    /** master(trig1) → build(run), build(out) → console(in), with an unknown
     *  device spliced in at index 1 carrying its own state and a cable. */
    private static JSONObject patchWithStranger() {
        Rack rack = new Rack();
        RackDevice master = DeviceType.MASTER.create();
        RackDevice build = DeviceType.BUILD.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(master);
        rack.addDevice(build);
        rack.addDevice(console);
        rack.connect(master.getPort("trig1"), build.getPort("run"));
        rack.connect(build.getPort("out"), console.getPort("in"));
        JSONObject json = RackIO.toJson(rack);

        // splice the stranger in at index 1, shifting build/console to 2/3
        JSONObject stranger = new JSONObject()
                .put("type", "com.acme.widget")
                .put("state", new JSONObject().put("alpha", "1").put("beta", "two"));
        JSONArray devices = json.getJSONArray("devices");
        JSONArray spliced = new JSONArray().put(devices.get(0)).put(stranger)
                .put(devices.get(1)).put(devices.get(2));
        json.put("devices", spliced);
        for (int i = 0; i < json.getJSONArray("cables").length(); i++) {
            JSONObject c = json.getJSONArray("cables").getJSONObject(i);
            if (c.getInt("fromDevice") >= 1) {
                c.put("fromDevice", c.getInt("fromDevice") + 1);
            }
            if (c.getInt("toDevice") >= 1) {
                c.put("toDevice", c.getInt("toDevice") + 1);
            }
        }
        // and a cable from the stranger's own out jack into the console
        json.getJSONArray("cables").put(new JSONObject()
                .put("fromDevice", 1).put("fromPort", "events")
                .put("toDevice", 3).put("toPort", "in"));
        return json;
    }

    @Test
    @DisplayName("an unknown device keeps its slot, so the cables around it stay on the right devices")
    void unknownDeviceKeepsIndexAlignment() {
        Rack rack = new Rack();
        RackIO.fromJson(rack, patchWithStranger());

        assertThat(rack.getDevices()).hasSize(4);
        assertThat(rack.getDevices().get(1)).isInstanceOf(MissingDevice.class);
        assertThat(rack.getDevices().get(1).getTypeId()).isEqualTo("com.acme.widget");

        // the build→console cable must still join BUILD to CONSOLE — on the
        // old drop-the-stranger code it landed on the wrong devices
        boolean buildToConsole = rack.getCables().stream().anyMatch(c ->
                c.getFrom().getDevice().getTypeId().equals("build")
                        && c.getFrom().getId().equals("out")
                        && c.getTo().getDevice().getTypeId().equals("console"));
        assertThat(buildToConsole)
                .as("cable saved after the stranger re-attaches to the same devices")
                .isTrue();
        // and the stranger's own cable is alive on an adopted, peer-typed port
        boolean strangerToConsole = rack.getCables().stream().anyMatch(c ->
                c.getFrom().getDevice() instanceof MissingDevice
                        && c.getFrom().getId().equals("events")
                        && c.getTo().getDevice().getTypeId().equals("console"));
        assertThat(strangerToConsole).as("stranger's cable adopted").isTrue();
        assertThat(rack.getCables()).hasSize(3);
    }

    @Test
    @DisplayName("the stranger's type id, state, and cables round-trip a re-save verbatim")
    void strangerRoundTripsLosslessly() {
        Rack rack = new Rack();
        RackIO.fromJson(rack, patchWithStranger());

        JSONObject resaved = RackIO.toJson(rack);
        JSONArray devices = resaved.getJSONArray("devices");
        JSONObject stranger = devices.getJSONObject(1);
        assertThat(stranger.getString("type")).isEqualTo("com.acme.widget");
        assertThat(stranger.getJSONObject("state").getString("alpha")).isEqualTo("1");
        assertThat(stranger.getJSONObject("state").getString("beta")).isEqualTo("two");
        assertThat(resaved.getJSONArray("cables").length()).isEqualTo(3);

        // and a second load of the re-save gives the same picture — the
        // placeholder is stable, not a one-shot rescue
        Rack again = new Rack();
        RackIO.fromJson(again, resaved);
        assertThat(again.getDevices()).hasSize(4);
        assertThat(again.getCables()).hasSize(3);
    }

    @Test
    @DisplayName("a missing device is inert: not resumable, never live, controls carry accessible names")
    void placeholderIsInert() {
        MissingDevice d = new MissingDevice("com.acme.widget");
        assertThat(d.isLive()).isFalse();
        assertThat(d.isResumable()).isFalse();
        for (java.awt.Component c : d.getComponents()) {
            assertThat(c.getAccessibleContext().getAccessibleName())
                    .as("placeholder control accessible name").isNotBlank();
        }
    }
}
