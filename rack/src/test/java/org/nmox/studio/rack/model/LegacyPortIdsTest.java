package org.nmox.studio.rack.model;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cables persist by port ID, so a jack rename must alias the old id on
 * load or every saved patch into that jack silently loses its cable.
 * TEMPO's STOP was {@code halt}; INSPECTOR's and WORMHOLE's RUNNING gates
 * were {@code live} (renamed 2026-09-17, labels unchanged). A patch saved
 * with the old ids loads with its cables intact and re-saves with the new.
 */
class LegacyPortIdsTest {

    private static JSONObject device(String type) {
        return new JSONObject().put("type", type).put("state", new JSONObject());
    }

    private static JSONObject cable(int from, String fromPort, int to, String toPort) {
        return new JSONObject().put("fromDevice", from).put("fromPort", fromPort)
                .put("toDevice", to).put("toPort", toPort);
    }

    @Test
    @DisplayName("Every alias names a real device and a port it carries today, and never an id it still has")
    void aliasTableIsCurrent() {
        for (var e : RackIO.LEGACY_PORT_IDS.entrySet()) {
            String typeId = e.getKey().substring(0, e.getKey().indexOf('.'));
            String oldId = e.getKey().substring(typeId.length() + 1);
            RackDevice device = DeviceCatalog.byId(typeId)
                    .orElseThrow(() -> new AssertionError("alias names no device: " + e.getKey()))
                    .create();
            try {
                assertThat(device.getPort(e.getValue())).as(e.getKey() + " → " + e.getValue()).isNotNull();
                assertThat(device.getPort(oldId)).as("the old id must be gone, or the alias is a lie").isNull();
            } finally {
                device.dispose();
            }
        }
    }

    @Test
    @DisplayName("A patch saved with tempo.halt, debug.live and tunnel.live loads with every cable intact")
    void legacyIdsLoadTheirCables() {
        // 0 SURGE, 1 TEMPO, 2 INSPECTOR, 3 WORMHOLE, 4 MONITOR-ish probe (a console)
        JSONObject patch = new JSONObject()
                .put("version", 1)
                .put("devices", new JSONArray()
                        .put(device("dev-server")).put(device("tempo"))
                        .put(device("debug")).put(device("tunnel")).put(device("dev-server")))
                .put("cables", new JSONArray()
                        .put(cable(0, "ready", 1, "halt"))       // SURGE READY → TEMPO STOP (old id)
                        .put(cable(2, "live", 4, "enable"))      // INSPECTOR RUNNING → SURGE ENABLE (old id)
                        .put(cable(3, "live", 1, "enable")));    // WORMHOLE RUNNING → TEMPO ENABLE (old id)
        Rack rack = new Rack();
        try {
            RackIO.fromJson(rack, patch);
            assertThat(rack.getCables()).as("three legacy cables, all resolved").hasSize(3);
            List<String> ends = rack.getCables().stream()
                    .map(c -> c.getFrom().getDevice().getTypeId() + "." + c.getFrom().getId()
                            + " → " + c.getTo().getDevice().getTypeId() + "." + c.getTo().getId())
                    .toList();
            assertThat(ends).containsExactlyInAnyOrder(
                    "dev-server.ready → tempo.stop",
                    "debug.running → dev-server.enable",
                    "tunnel.running → tempo.enable");

            // the next save writes today's ids
            JSONArray saved = RackIO.toJson(rack).getJSONArray("cables");
            List<String> savedIds = new java.util.ArrayList<>();
            for (int i = 0; i < saved.length(); i++) {
                savedIds.add(saved.getJSONObject(i).getString("fromPort") + "/" + saved.getJSONObject(i).getString("toPort"));
            }
            assertThat(savedIds).containsExactlyInAnyOrder("ready/stop", "running/enable", "running/enable");
        } finally {
            rack.shutdown();
        }
    }
}
