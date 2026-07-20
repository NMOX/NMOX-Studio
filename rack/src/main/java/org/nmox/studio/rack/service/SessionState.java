package org.nmox.studio.rack.service;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

/**
 * What was alive in the rack, written continuously so it survives any
 * kind of death - clean quit, crash, kill -9, power loss. The mosh
 * principle applied to the IDE: the session is state to resynchronize,
 * not a stream that dies with the process.
 */
public record SessionState(String project, long at, List<Entry> running) {

    public SessionState {
        running = List.copyOf(running); // snapshots are facts; no one edits a fact
    }

    /** One live device: matched on resume by position and type. */
    public record Entry(int index, String typeId, String title) {
    }

    /** Snapshot whatever is live right now; empty list = nothing to resume. */
    public static SessionState capture(Rack rack) {
        List<Entry> live = new ArrayList<>();
        List<RackDevice> devices = rack.getDevices();
        for (int i = 0; i < devices.size(); i++) {
            RackDevice d = devices.get(i);
            if (d.isResumable()) {
                live.add(new Entry(i, d.getTypeId(), d.getTitle()));
            }
        }
        return new SessionState(rack.getProjectDir().getAbsolutePath(),
                System.currentTimeMillis(), live);
    }

    public String toJson() {
        JSONArray arr = new JSONArray();
        for (Entry e : running) {
            arr.put(new JSONObject()
                    .put("index", e.index())
                    .put("typeId", e.typeId())
                    .put("title", e.title()));
        }
        return new JSONObject()
                .put("project", project)
                .put("at", at)
                .put("running", arr)
                .toString();
    }

    public static SessionState fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            List<Entry> running = new ArrayList<>();
            JSONArray arr = o.getJSONArray("running");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                running.add(new Entry(e.getInt("index"),
                        e.getString("typeId"), e.optString("title", e.getString("typeId"))));
            }
            return new SessionState(o.getString("project"), o.getLong("at"), running);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * The devices in this rack that the session says were running -
     * matched by position AND type, so a re-arranged patch never
     * resurrects the wrong unit.
     */
    public List<RackDevice> matchAgainst(Rack rack) {
        List<RackDevice> matches = new ArrayList<>();
        List<RackDevice> devices = rack.getDevices();
        for (Entry e : running) {
            if (e.index() >= devices.size()) {
                continue;
            }
            RackDevice d = devices.get(e.index());
            // ledger 44: if the device that was live at the crash belonged to
            // a plugin that has since been uninstalled, its slot now holds a
            // MissingDevice with the same type id — it matches, but its
            // resume() is a no-op, so "Resume last session?" would be a
            // dead-click balloon. A placeholder can never resume anything.
            if (d.getTypeId().equals(e.typeId())
                    && !(d instanceof org.nmox.studio.rack.model.MissingDevice)) {
                matches.add(d);
            }
        }
        return matches;
    }

    /**
     * The entries this rack CANNOT account for — no device of the
     * recorded type sits at the recorded position (or the slot holds a
     * MissingDevice placeholder). This is the unsaved-patch case the
     * v1.95.2 night journey found: kill -9 with a never-saved rack
     * leaves a perfect snapshot whose devices simply don't exist after
     * relaunch, and resurrection silently offered nothing. The caller
     * decides which of these it can re-create (DeviceCatalog lookup —
     * an uninstalled plugin's type stays unresurrectable, ledger 44).
     */
    public List<Entry> unmatchedAgainst(Rack rack) {
        List<Entry> unmatched = new ArrayList<>();
        List<RackDevice> devices = rack.getDevices();
        for (Entry e : running) {
            boolean matched = e.index() < devices.size()
                    && devices.get(e.index()).getTypeId().equals(e.typeId())
                    && !(devices.get(e.index()) instanceof org.nmox.studio.rack.model.MissingDevice);
            if (!matched) {
                unmatched.add(e);
            }
        }
        return unmatched;
    }

    /** Sessions older than this are history, not intent. */
    public boolean fresh() {
        return System.currentTimeMillis() - at < 7L * 24 * 3600 * 1000;
    }
}
