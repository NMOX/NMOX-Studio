package org.nmox.studio.rack.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.rack.devices.DeviceType;

/**
 * Saves and restores a rack patch - the device stack, every control
 * position, and the full cable harness - as JSON ("song file" for the
 * rack). Default location is .nmoxrack.json in the project directory.
 */
public final class RackIO {

    public static final String DEFAULT_FILENAME = ".nmoxrack.json";

    private RackIO() {
    }

    public static JSONObject toJson(Rack rack) {
        JSONObject root = new JSONObject();
        root.put("version", 1);

        List<RackDevice> devices = rack.getDevices();
        JSONArray deviceArr = new JSONArray();
        for (RackDevice d : devices) {
            JSONObject dj = new JSONObject();
            dj.put("type", d.getTypeId());
            dj.put("state", new JSONObject(d.getState()));
            deviceArr.put(dj);
        }
        root.put("devices", deviceArr);

        JSONArray cableArr = new JSONArray();
        for (Cable c : rack.getCables()) {
            JSONObject cj = new JSONObject();
            cj.put("fromDevice", devices.indexOf(c.getFrom().getDevice()));
            cj.put("fromPort", c.getFrom().getId());
            cj.put("toDevice", devices.indexOf(c.getTo().getDevice()));
            cj.put("toPort", c.getTo().getId());
            cableArr.put(cj);
        }
        root.put("cables", cableArr);
        return root;
    }

    /** Replaces the rack's contents with the patch in the JSON document. */
    public static void fromJson(Rack rack, JSONObject root) {
        for (RackDevice d : rack.getDevices()) {
            rack.removeDevice(d);
        }
        JSONArray deviceArr = root.optJSONArray("devices");
        if (deviceArr == null) {
            return;
        }
        for (int i = 0; i < deviceArr.length(); i++) {
            JSONObject dj = deviceArr.getJSONObject(i);
            DeviceType type = DeviceType.byId(dj.getString("type"));
            if (type == null) {
                continue;
            }
            RackDevice device = type.create();
            rack.addDevice(device);
            JSONObject state = dj.optJSONObject("state");
            if (state != null) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : state.keySet()) {
                    map.put(key, state.getString(key));
                }
                device.applyState(map);
            }
        }
        List<RackDevice> devices = rack.getDevices();
        JSONArray cableArr = root.optJSONArray("cables");
        if (cableArr != null) {
            for (int i = 0; i < cableArr.length(); i++) {
                JSONObject cj = cableArr.getJSONObject(i);
                int fi = cj.getInt("fromDevice"), ti = cj.getInt("toDevice");
                if (fi < 0 || fi >= devices.size() || ti < 0 || ti >= devices.size()) {
                    continue;
                }
                Port from = devices.get(fi).getPort(cj.getString("fromPort"));
                Port to = devices.get(ti).getPort(cj.getString("toPort"));
                if (from != null && to != null) {
                    rack.connect(from, to);
                }
            }
        }
    }

    public static void save(Rack rack, File file) throws IOException {
        // atomic swap: mtime pollers and external readers of .nmoxrack.json
        // must never observe a truncated patch
        AtomicFiles.writeString(file.toPath(), toJson(rack).toString(2));
    }

    public static void load(Rack rack, File file) throws IOException {
        fromJson(rack, new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
    }
}
