package org.nmox.studio.rack.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A rack as a file another NMOX Studio user can mount (v2.176.0).
 *
 * <p>The patch format ({@link RackIO}) has always been a file, and the drop-in
 * presets since v1.294.0 proved it travels: a saved patch dropped into
 * {@code ~/.nmox/presets.d} appears on the Presets menu. What was missing is
 * the pair of doors — Share writes the patch somewhere the user chooses, Import
 * reads one from wherever it arrived — and the two things a file from another
 * machine needs that a file from this one does not:
 *
 * <ul>
 * <li><b>Portability without disclosure.</b> A device's state can hold an
 * absolute path (TAIL's file, an INSPECTOR entry, a CMD working directory), and
 * a path under the sender's home names the sender: {@code /Users/david/…} is a
 * username. {@link #export} rewrites every state value under the sender's home
 * to {@code ~/…}; {@link #imported} expands {@code ~/} to the receiver's. Nothing
 * else in the state is touched — the whole point of sharing a rack is the
 * commands and settings in it, and the receiver is shown them before mounting.</li>
 * <li><b>Arrival at rest.</b> A shared rack never runs anything by being
 * imported. Loading a patch spawns nothing by construction, and every command
 * device gates its own GO on Workspace Trust; but a REFLEX saved {@code armed}
 * would start watching on mount and a TEMPO saved {@code running} would start
 * firing triggers into whatever it was cabled to. {@link #imported} sets both to
 * off and {@link #inspect} counts them, so the receiver reads "3 devices arrive
 * at rest" rather than discovering a clock they never started.</li>
 * <li><b>Honesty about what is inside.</b> {@link #inspect} lists the devices
 * (naming any this install does not have — they mount as placeholders that keep
 * their slot and cables, the v1.54.0 rule), the cable count, and every setting
 * that reads like a command, path or address, so the decision to mount is made
 * on what the file says, not on who sent it.</li>
 * </ul>
 *
 * <p>Pure: every method takes what it needs and touches no disk, so the rules
 * are unit tests rather than a walk.
 */
public final class RackShare {

    /** The header a shared file carries beside the patch; absent on a plain patch. */
    public static final String SHARED = "shared";

    /** State keys that mean "start doing something on mount"; a shared rack arrives with these off. */
    static final Set<String> SELF_STARTING = Set.of("armed", "running");

    /** What a shared file holds, read before anything mounts. */
    public record Manifest(List<Device> devices, int cables, List<Setting> settings,
            List<String> unknownTypes, int atRest, String sharedBy) {
        /** True when this install can mount every device the file names. */
        public boolean complete() {
            return unknownTypes.isEmpty();
        }
    }

    /** One device in the shared file: its type id and whether this install knows it. */
    public record Device(String typeId, boolean known) {
    }

    /** A setting the receiver should read before mounting: a command, a path, an address. */
    public record Setting(String typeId, String key, String value) {
    }

    private RackShare() {
    }

    /**
     * The patch made portable: a {@code shared} header naming the product version
     * it came from, and every state value under {@code home} rewritten to
     * {@code ~/…}. The receiver's product version, not the sender's, decides what
     * the file means, so nothing but the version is recorded — no name, no
     * machine, no date.
     */
    public static JSONObject export(JSONObject patch, Path home, String productVersion) {
        JSONObject out = new JSONObject(patch.toString());
        JSONObject header = new JSONObject();
        header.put("product", productVersion == null ? "" : productVersion);
        out.put(SHARED, header);
        String prefix = home == null ? null : home.toAbsolutePath().normalize().toString();
        rewriteStates(out, value -> {
            if (prefix != null && (value.equals(prefix) || value.startsWith(prefix + "/"))) {
                return "~" + value.substring(prefix.length());
            }
            return value;
        });
        return out;
    }

    /**
     * A shared file made mountable here: {@code ~/} expanded to this user's home,
     * every self-starting flag set off, and the {@code shared} header dropped so
     * what lands in the rack is a plain patch a later Save Patch writes as one.
     */
    public static JSONObject imported(JSONObject shared, Path home) {
        JSONObject out = new JSONObject(shared.toString());
        out.remove(SHARED);
        String prefix = home == null ? null : home.toAbsolutePath().normalize().toString();
        JSONArray devices = out.optJSONArray("devices");
        if (devices == null) {
            return out;
        }
        for (int i = 0; i < devices.length(); i++) {
            JSONObject state = devices.getJSONObject(i).optJSONObject("state");
            if (state == null) {
                continue;
            }
            for (String key : new ArrayList<>(state.keySet())) {
                String value = state.optString(key, "");
                if (SELF_STARTING.contains(key)) {
                    state.put(key, "false");
                } else if (prefix != null && (value.equals("~") || value.startsWith("~/"))) {
                    state.put(key, prefix + value.substring(1));
                }
            }
        }
        return out;
    }

    /**
     * What the file holds, for the receiver to read before mounting: every device
     * with whether {@code known} accepts its type id, the cable count, the
     * settings worth reading, the types this install lacks, and how many devices
     * were saved armed or running (and will arrive at rest).
     */
    public static Manifest inspect(JSONObject shared, Predicate<String> known) {
        List<Device> devices = new ArrayList<>();
        List<Setting> settings = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int atRest = 0;
        JSONArray deviceArr = shared.optJSONArray("devices");
        if (deviceArr != null) {
            for (int i = 0; i < deviceArr.length(); i++) {
                JSONObject dj = deviceArr.getJSONObject(i);
                String typeId = dj.optString("type", "?");
                boolean isKnown = known.test(typeId);
                devices.add(new Device(typeId, isKnown));
                if (!isKnown && !unknown.contains(typeId)) {
                    unknown.add(typeId);
                }
                JSONObject state = dj.optJSONObject("state");
                if (state == null) {
                    continue;
                }
                for (String key : state.keySet()) {
                    String value = state.optString(key, "");
                    if (SELF_STARTING.contains(key) && "true".equalsIgnoreCase(value)) {
                        atRest++;
                    } else if (worthReading(value)) {
                        settings.add(new Setting(typeId, key, value));
                    }
                }
            }
        }
        JSONArray cables = shared.optJSONArray("cables");
        JSONObject header = shared.optJSONObject(SHARED);
        String sharedBy = header == null ? null : header.optString("product", "");
        return new Manifest(Collections.unmodifiableList(devices), cables == null ? 0 : cables.length(),
                Collections.unmodifiableList(settings), Collections.unmodifiableList(unknown), atRest, sharedBy);
    }

    /** True when the file carries the {@code shared} header — it came through Share, not Save Patch. */
    public static boolean isShared(JSONObject doc) {
        return doc.has(SHARED);
    }

    /**
     * A value the receiver should see before mounting: anything that could be a
     * command, a path or an address. Knob positions ({@code "2"}), switches
     * ({@code "false"}) and short words are the device's own business.
     */
    static boolean worthReading(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (v.matches("-?[0-9]+(\\.[0-9]+)?") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return false;
        }
        return v.contains(" ") || v.contains("/") || v.contains("\\") || v.contains("://")
                || v.toLowerCase(Locale.ROOT).startsWith("~");
    }

    private static void rewriteStates(JSONObject patch, java.util.function.UnaryOperator<String> rewrite) {
        JSONArray devices = patch.optJSONArray("devices");
        if (devices == null) {
            return;
        }
        for (int i = 0; i < devices.length(); i++) {
            JSONObject state = devices.getJSONObject(i).optJSONObject("state");
            if (state == null) {
                continue;
            }
            for (String key : new ArrayList<>(state.keySet())) {
                state.put(key, rewrite.apply(state.optString(key, "")));
            }
        }
    }
}
