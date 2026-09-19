package org.nmox.studio.rack.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.util.Versions;

/**
 * What a rack file loses on THIS install, found before it mounts.
 *
 * <p>A rack file outlives the release that wrote it: devices gain and lose
 * jacks, settings are renamed, a newer release wires devices this one has never
 * heard of. {@link RackIO#fromJson} is deliberately tolerant about all of it —
 * an unknown device keeps its slot as a placeholder, a cable to a jack that
 * does not exist is dropped with a WARNING in the log — which is right for the
 * user's own patch and not enough for a stranger's: a rack that silently
 * arrives with one cable missing looks complete and does something else.
 *
 * <p>So the file is mounted into a throwaway rack first and the result is
 * compared with what the file asked for. The dry run mounts NOTHING live: the
 * copy is made local by {@link RackShare#imported} and then every switch in it
 * is set off, whatever the self-starting ledger says, because a probe has no
 * business arming anything; the throwaway rack points at an empty scratch
 * directory and is shut down before this returns.
 *
 * <p>A file in a FORMAT newer than this install reads is not dry-run at all —
 * its fields cannot be trusted to mean what they meant — and Import refuses it
 * by name.
 */
public final class RackCompat {

    /** The patch format this install reads and writes ({@code "version"} in the file). */
    public static final int FORMAT = 1;

    private RackCompat() {
    }

    /**
     * @param format the file's format number
     * @param formatTooNew the file is in a format this install does not read: nothing else was checked
     * @param madeWith the product version the file says it was shared from; empty when it does not say
     * @param madeWithNewer that version is newer than this install's
     * @param lostCables cables the file names that this install cannot connect, as {@code A out ▸ B in}
     * @param lostSettings settings the file carries that the device here does not have, as {@code DEVICE · key}
     */
    public record Report(int format, boolean formatTooNew, String madeWith, boolean madeWithNewer,
            List<String> lostCables, List<String> lostSettings) {

        public Report {
            lostCables = List.copyOf(lostCables);
            lostSettings = List.copyOf(lostSettings);
        }

        /** Everything the file asks for exists here. */
        public boolean carriesWhole() {
            return !formatTooNew && lostCables.isEmpty() && lostSettings.isEmpty();
        }
    }

    /**
     * EDT (it constructs devices). Checks {@code doc} against this install.
     *
     * @param thisProduct this install's product version; null or blank in a dev build, which is never "older"
     * @throws IllegalArgumentException when the document is not a rack ({@link RackShare}'s slot refusals)
     */
    public static Report check(JSONObject doc, String thisProduct) {
        int format = doc.optInt("version", FORMAT);
        JSONObject header = doc.optJSONObject(RackShare.SHARED);
        String madeWith = header != null && header.opt("product") instanceof String p ? RackCard.clean(p, 24) : "";
        boolean newer = !madeWith.isEmpty() && thisProduct != null && !thisProduct.isBlank()
                && Versions.compare(madeWith, thisProduct) > 0;
        if (format > FORMAT) {
            return new Report(format, true, madeWith, newer, List.of(), List.of());
        }
        JSONObject local = atRest(RackShare.imported(doc, null));
        Rack scratch = new Rack();
        try {
            scratch.setProjectDir(scratchDir());
            RackIO.fromJson(scratch, local);
            List<RackDevice> mounted = scratch.getDevices();
            return new Report(format, false, madeWith, newer,
                    lostCables(local, mounted, RackIO.toJson(scratch)), lostSettings(local, mounted));
        } finally {
            scratch.shutdown();
        }
    }

    /** Every switch off: the probe arms nothing, whether or not the ledger knows the switch. */
    static JSONObject atRest(JSONObject patch) {
        JSONObject out = new JSONObject(patch.toString());
        JSONArray devices = out.optJSONArray("devices");
        for (int i = 0; devices != null && i < devices.length(); i++) {
            JSONObject state = devices.optJSONObject(i) == null ? null : devices.optJSONObject(i).optJSONObject("state");
            if (state == null) {
                continue;
            }
            for (String key : new ArrayList<>(state.keySet())) {
                if ("true".equalsIgnoreCase(state.optString(key, ""))) {
                    state.put(key, "false");
                }
            }
        }
        return out;
    }

    private static List<String> lostCables(JSONObject asked, List<RackDevice> mounted, JSONObject got) {
        Set<String> connected = new HashSet<>();
        JSONArray gotCables = got.optJSONArray("cables");
        for (int i = 0; gotCables != null && i < gotCables.length(); i++) {
            JSONObject c = gotCables.getJSONObject(i);
            connected.add(key(c.optInt("fromDevice", -1), c.optString("fromPort"),
                    c.optInt("toDevice", -1), c.optString("toPort")));
        }
        List<String> lost = new ArrayList<>();
        JSONArray askedCables = asked.optJSONArray("cables");
        for (int i = 0; askedCables != null && i < askedCables.length(); i++) {
            JSONObject c = askedCables.optJSONObject(i);
            if (c == null) {
                lost.add("#" + (i + 1));
                continue;
            }
            int fd = c.optInt("fromDevice", -1);
            int td = c.optInt("toDevice", -1);
            String fromPort = c.optString("fromPort");
            String toPort = c.optString("toPort");
            if (fd < 0 || td < 0 || fd >= mounted.size() || td >= mounted.size()) {
                lost.add("#" + (i + 1));
                continue;
            }
            // the id a saved cable names may be one a later release renamed:
            // that cable is not lost, it is spelled the old way
            String from = RackIO.currentPortId(mounted.get(fd), fromPort);
            String to = RackIO.currentPortId(mounted.get(td), toPort);
            if (!connected.contains(key(fd, from, td, to))) {
                lost.add(mounted.get(fd).getTitle() + " " + RackCard.clean(fromPort, 24)
                        + " ▸ " + mounted.get(td).getTitle() + " " + RackCard.clean(toPort, 24));
            }
        }
        return lost;
    }

    private static List<String> lostSettings(JSONObject asked, List<RackDevice> mounted) {
        List<String> lost = new ArrayList<>();
        JSONArray devices = asked.optJSONArray("devices");
        for (int i = 0; devices != null && i < devices.length() && i < mounted.size(); i++) {
            RackDevice device = mounted.get(i);
            JSONObject state = devices.optJSONObject(i) == null ? null : devices.optJSONObject(i).optJSONObject("state");
            if (state == null || device instanceof MissingDevice) {
                continue; // a placeholder keeps every setting verbatim for the day its device is installed
            }
            Set<String> known = device.getState().keySet();
            for (String key : state.keySet()) {
                if (!known.contains(key) && lost.size() < 40) {
                    lost.add(device.getTitle() + " · " + RackCard.clean(key, 24));
                }
            }
        }
        return lost;
    }

    /** Joins a cable's four parts; a character no port id can hold, written as a number so the source stays printable. */
    private static final String SEP = String.valueOf((char) 0x1F);

    private static String key(int fd, String fromPort, int td, String toPort) {
        return fd + SEP + fromPort + SEP + td + SEP + toPort;
    }

    private static final java.util.concurrent.atomic.AtomicReference<File> SCRATCH =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** An empty directory for the throwaway rack: a fresh Rack aims at user.home, which a probe must never walk. */
    private static File scratchDir() {
        return ScratchDirs.cached(SCRATCH, "nmox-rack-compat");
    }
}
