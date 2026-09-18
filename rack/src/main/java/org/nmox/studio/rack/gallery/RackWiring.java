package org.nmox.studio.rack.gallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A rack document read as a person would describe it: which devices, in
 * rack order, and which jack feeds which — {@code REFLEX CHANGED ▸ VERITAS
 * RUN}. Pure: the names come through {@link Names}, so the rules here are
 * unit tests and a stranger's file costs no device construction to read.
 *
 * <p>Every read is tolerant and bounded. A document is whatever a file held
 * — a slot that is not an object, a cable pointing past the last device, a
 * million cables — so a malformed part is left out of the sketch (the
 * {@link RackJudge} is where a community rack is REFUSED for it) and the
 * lists stop at {@link #MAX_DEVICES} / {@link #MAX_LINES} with the
 * remainder counted, never silently dropped.
 */
public final class RackWiring {

    /** The most device titles listed before the rest are counted. */
    public static final int MAX_DEVICES = 64;
    /** The most wiring lines listed before the rest are counted. */
    public static final int MAX_LINES = 64;
    /** Cables examined at most; a saved rack holds tens. */
    private static final int MAX_CABLES_READ = 4096;
    /** Device slots examined at most for the sketch. */
    private static final int MAX_DEVICES_READ = 1024;
    /** A type or port id shown in place of a name it does not have is clipped to this. */
    private static final int MAX_ID = 40;

    /** What the sketch needs to know about a device type; either answer may be null (unknown). */
    public interface Names {
        /** The faceplate title of a device type ({@code VERITAS}), or null when this install lacks it. */
        String title(String typeId);

        /** The label on a jack ({@code RUN}), or null when the type has no such port. */
        String label(String typeId, String portId);
    }

    private RackWiring() {
    }

    /**
     * The device titles in rack order. A type this install lacks reads as its
     * type id and {@code " ?"}; a slot that is not a device reads {@code "?"};
     * past {@link #MAX_DEVICES} the rest are one {@code "+N"} item.
     */
    public static List<String> titles(JSONObject doc, Names names) {
        JSONArray devices = doc == null ? null : doc.optJSONArray("devices");
        if (devices == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int shown = Math.min(devices.length(), MAX_DEVICES);
        for (int i = 0; i < shown; i++) {
            out.add(title(typeAt(devices, i), names));
        }
        if (devices.length() > shown) {
            out.add("+" + (devices.length() - shown));
        }
        return List.copyOf(out);
    }

    /** How many cables the document carries (the raw count, as the file says it). */
    public static int cableCount(JSONObject doc) {
        JSONArray cables = doc == null ? null : doc.optJSONArray("cables");
        return cables == null ? 0 : cables.length();
    }

    /**
     * One line per source jack, its targets after the arrow:
     * {@code REFLEX CHANGED ▸ VERITAS RUN, PURITY RUN}. Ordered by the source
     * device's place in the rack, then by the order the file lists the
     * cables. A jack reads as its LABEL where the type is known and as its id
     * otherwise; the second device of one title reads {@code VERITAS·2}, the
     * way the patch bay's bus names do, so two lanes never read as one.
     */
    public static List<String> sketch(JSONObject doc, Names names) {
        JSONArray devices = doc == null ? null : doc.optJSONArray("devices");
        JSONArray cables = doc == null ? null : doc.optJSONArray("cables");
        if (devices == null || cables == null) {
            return List.of();
        }
        int count = Math.min(devices.length(), MAX_DEVICES_READ);
        String[] types = new String[count];
        String[] display = new String[count];
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < count; i++) {
            types[i] = typeAt(devices, i);
            String title = title(types[i], names);
            int nth = seen.merge(title, 1, Integer::sum);
            display[i] = nth == 1 ? title : title + "·" + nth;
        }
        // source jack -> its line, in first-seen (cable) order; sorted by device below
        Map<String, StringBuilder> lines = new LinkedHashMap<>();
        Map<String, Integer> sourceDevice = new HashMap<>();
        int read = Math.min(cables.length(), MAX_CABLES_READ);
        for (int i = 0; i < read; i++) {
            JSONObject c = cables.optJSONObject(i);
            if (c == null) {
                continue;
            }
            int from = c.optInt("fromDevice", -1);
            int to = c.optInt("toDevice", -1);
            if (from < 0 || from >= count || to < 0 || to >= count
                    || !(c.opt("fromPort") instanceof String fromPort)
                    || !(c.opt("toPort") instanceof String toPort)) {
                continue;
            }
            String jack = from + "\n" + fromPort;
            StringBuilder line = lines.get(jack);
            if (line == null) {
                line = new StringBuilder(display[from]).append(' ')
                        .append(label(types[from], fromPort, names)).append(" ▸ ");
                lines.put(jack, line);
                sourceDevice.put(jack, from);
            } else {
                line.append(", ");
            }
            line.append(display[to]).append(' ').append(label(types[to], toPort, names));
        }
        List<Map.Entry<String, StringBuilder>> ordered = new ArrayList<>(lines.entrySet());
        // List.sort is stable: within one device the cable order of its jacks is kept
        ordered.sort((a, b) -> Integer.compare(sourceDevice.get(a.getKey()), sourceDevice.get(b.getKey())));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, StringBuilder> e : ordered) {
            if (out.size() >= MAX_LINES) {
                out.add("+" + (ordered.size() - MAX_LINES));
                break;
            }
            out.add(e.getValue().toString());
        }
        return List.copyOf(out);
    }

    private static String typeAt(JSONArray devices, int i) {
        JSONObject dj = devices.optJSONObject(i);
        return dj != null && dj.opt("type") instanceof String s ? s : null;
    }

    private static String title(String typeId, Names names) {
        if (typeId == null) {
            return "?";
        }
        String title = names.title(typeId);
        return title == null || title.isBlank() ? plain(typeId) + " ?" : title;
    }

    private static String label(String typeId, String portId, Names names) {
        String label = typeId == null ? null : names.label(typeId, portId);
        return label == null || label.isBlank() ? plain(portId) : label;
    }

    /**
     * An id from the file, shown because nothing here has a name for it: a
     * stranger's text, so control characters (a newline could forge a second
     * wiring line) become spaces and the rest is clipped by code points.
     */
    static String plain(String raw) {
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < raw.length() && kept < MAX_ID;) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            boolean lone = cp <= 0xFFFF && Character.isSurrogate((char) cp);
            boolean unsafe = Character.isISOControl(cp) || lone || cp == 0x2028 || cp == 0x2029;
            sb.appendCodePoint(unsafe ? ' ' : cp);
            kept++;
        }
        return sb.toString().strip();
    }
}
