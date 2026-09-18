package org.nmox.studio.rack.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.rack.devices.DeviceCatalog;

/**
 * Saves and restores a rack patch - the device stack, every control
 * position, and the full cable harness - as JSON ("song file" for the
 * rack). Default location is .nmoxrack.json in the project directory.
 */
public final class RackIO {

    public static final String DEFAULT_FILENAME = ".nmoxrack.json";

    /**
     * Port ids a saved patch may still name, keyed {@code <typeId>.<oldId>}
     * → the id that port carries now. Cables persist by port ID, so a
     * rename without this table silently drops every cable into the
     * renamed jack on the next load (the id is what the file says; the
     * label is what the user saw, and the label never changed). Three ids
     * drifted from the rack's vocabulary — TEMPO's STOP was {@code halt},
     * INSPECTOR's and WORMHOLE's RUNNING gates were {@code live} — and were
     * renamed on 2026-09-17. Applied when a cable's ports are resolved in
     * {@link #fromJson}; the next save writes the current id.
     */
    static final Map<String, String> LEGACY_PORT_IDS = Map.of(
            "tempo.halt", "stop",
            "debug.live", "running",
            "tunnel.live", "running");

    /** The id a device's port carries today for the id a patch named. */
    static String currentPortId(RackDevice device, String savedId) {
        return LEGACY_PORT_IDS.getOrDefault(device.getTypeId() + "." + savedId, savedId);
    }

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(RackIO.class.getName());

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
            String typeId = dj.getString("type");
            // an unknown type id (a plugin device not installed here) keeps
            // its slot as a MissingDevice: cables are index-based, so
            // dropping it would silently re-route every cable saved after
            // it, and its state must survive the next save untouched
            RackDevice device = DeviceCatalog.byId(typeId)
                    .map(DeviceCatalog.Entry::create)
                    .orElseGet(() -> new MissingDevice(typeId));
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
                // By the time cables load, the rack has been CLEARED and its
                // devices mounted: an exception here leaves the user with half a
                // rack and no way back (loading is an undo boundary). A cable
                // entry that is not an object, or names no device or jack, is one
                // lost cable said by name — never org.json's exception (found by
                // RackCompatTest, 2026-09-18: v2.178.0 hardened the device slots
                // of a stranger's file and not the cable slots).
                JSONObject cj = cableArr.optJSONObject(i);
                if (cj == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable #{0} dropped: not a cable entry", i + 1);
                    continue;
                }
                int fi = cj.optInt("fromDevice", -1), ti = cj.optInt("toDevice", -1);
                if (fi < 0 || fi >= devices.size() || ti < 0 || ti >= devices.size()) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable #{0} dropped: it names a device slot this patch does not have", i + 1);
                    continue;
                }
                RackDevice fd = devices.get(fi);
                RackDevice td = devices.get(ti);
                // a renamed jack keeps its cables: the saved id maps to today's
                String fromId = currentPortId(fd, cj.optString("fromPort", ""));
                String toId = currentPortId(td, cj.optString("toPort", ""));
                Port from = fd.getPort(fromId);
                Port to = td.getPort(toId);
                // a missing device adopts the ports its saved cables name,
                // typed like the live peer so canConnectTo accepts the patch
                if (from == null && fd instanceof MissingDevice m) {
                    from = m.adoptPort(fromId, Port.Direction.OUT,
                            to != null ? to.getType() : SignalType.DATA);
                }
                if (to == null && td instanceof MissingDevice m) {
                    to = m.adoptPort(toId, Port.Direction.IN,
                            from != null ? from.getType() : SignalType.DATA);
                }
                // a port a device no longer has (STELLAR's ENABLE, removed
                // 2026-09-17) loses its cable and nothing else: the patch
                // still loads, every other cable intact — and the loss is
                // SAID, by name (refusals speak; the 2026-09-17 arc review
                // found this branch silent, and Rack.connect's null for a
                // duplicate or illegal pair silent beside it)
                String cable = fd.getTypeId() + "." + fromId + " -> " + td.getTypeId() + "." + toId;
                if (from == null || to == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable {0} dropped: {1} has no such port",
                            new Object[]{cable, from == null ? fd.getTypeId() + "." + fromId : td.getTypeId() + "." + toId});
                } else if (rack.connect(from, to) == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable {0} not connected: duplicate, incompatible or a loop", cable);
                }
            }
        }
        // A patch/preset load REPLACES the rack's contents, so the device
        // removals and additions above must not be reachable by ⌘Z: undoing
        // past a load would peel the just-loaded patch apart device by device
        // and eventually resurrect the PREVIOUS patch's structure (a real
        // correctness bug — the undo edits predate the current patch). This is
        // THE single choke point every load routes through — the Presets menu,
        // the Load Patch button, and RackService's project-switch autoload —
        // so clearing here covers them all. RackService also clears after a
        // project switch with no patch file, a case that never reaches here.
        rack.clearUndoHistory();
    }

    public static void save(Rack rack, File file) throws IOException {
        // atomic swap: mtime pollers and external readers of .nmoxrack.json
        // must never observe a truncated patch
        AtomicFiles.writeString(file.toPath(), toJson(rack).toString(2));
    }

    public static void load(Rack rack, File file) throws IOException {
        String text;
        try {
            text = readCapped(file);
        } catch (PatchTooLargeException tooLarge) {
            // load()'s contract is "replace the rack's contents" (the v1.107.0
            // corrupt-patch rule): a patch refused unread cannot supply them,
            // so the previous project's devices must not stay mounted — but
            // the file is not corrupt, so it is not moved aside
            for (RackDevice d : rack.getDevices()) {
                rack.removeDevice(d);
            }
            rack.clearUndoHistory();
            throw tooLarge;
        }
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException corrupt) {
            // A corrupt or hand-broken patch used to throw here BEFORE fromJson
            // ran — so the previous project's devices stayed mounted (a switch
            // A->B with B corrupt aimed A's rack at B's dir), and the untouched
            // corrupt file was silently clobbered by the next atomic save.
            // load()'s contract is "replace the rack's contents"; a corrupt
            // patch can't supply real contents, so replace with empty, and
            // preserve the user's file as .bak first (the BlockStudio idiom) so
            // their hand-edit survives and save() writes a fresh file.
            backupCorrupt(file);
            for (RackDevice d : rack.getDevices()) {
                rack.removeDevice(d);
            }
            rack.clearUndoHistory();
            throw new IOException("Corrupt rack patch " + file.getName()
                    + " (kept as .bak): " + corrupt.getMessage(), corrupt);
        }
        fromJson(rack, root);
    }

    /**
     * Reads and parses a patch file WITHOUT touching the rack — safe to call
     * off the EDT (the caller applies the returned document with
     * {@link #fromJson} on the EDT, where the device components are mutated).
     * On corrupt JSON the user's file is preserved as {@code <name>.bak} and an
     * IOException is thrown, the same data-safety guarantee {@link #load} gives.
     */
    public static JSONObject readDocument(File file) throws IOException {
        String text = readCapped(file);
        try {
            return new JSONObject(text);
        } catch (JSONException corrupt) {
            backupCorrupt(file);
            throw new IOException("Corrupt rack patch " + file.getName()
                    + " (kept as .bak): " + corrupt.getMessage(), corrupt);
        }
    }

    /**
     * The most a patch file may be before it is refused unread: a saved rack
     * is a few kilobytes, and since v2.176.0 Import… reads a file from another
     * machine — the bounded-read law (every outside read capped) reached the
     * one text read in this class on the 2026-09-17 arc review.
     */
    static final long MAX_PATCH_BYTES = 8L * 1024 * 1024;

    /** A patch over {@link #MAX_PATCH_BYTES}: refused before a byte is read, and never moved aside. */
    static final class PatchTooLargeException extends IOException {
        PatchTooLargeException(String message) {
            super(message);
        }
    }

    /** The patch text, or a refusal naming the size — the file is untouched either way. */
    private static String readCapped(File file) throws IOException {
        long size = Files.size(file.toPath());
        if (size > MAX_PATCH_BYTES) {
            throw new PatchTooLargeException("Rack patch " + file.getName() + " is " + (size / 1024)
                    + " KiB, over the " + (MAX_PATCH_BYTES / 1024 / 1024) + " MiB cap — not read");
        }
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    /** Renames a corrupt patch to {@code <name>.bak} so save() can't clobber it. */
    private static void backupCorrupt(File file) {
        try {
            Files.move(file.toPath(), file.toPath().resolveSibling(file.getName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort: if the rename fails the empty rack is still the safe
            // state; we simply couldn't preserve the bytes
        }
    }
}
