package org.nmox.studio.rack.gallery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.model.RackShare;

/**
 * The law a COMMUNITY rack must meet before it ships inside the product.
 *
 * <p>One judge, used twice: {@code CommunityRacksGateTest} fails the build
 * on any problem, by file name; the runtime loader ({@link CommunityRacks})
 * skips a rack that has one and logs the reason, so a bad file can never
 * break the gallery for the good ones. A user's own drop-in is NOT judged —
 * their file is theirs — this is the bar for what the product vouches for.
 *
 * <p>The rules, each a sentence a contributor can act on:
 * <ul>
 * <li>it says what it is — a non-blank {@code name} (≤ 60) and
 * {@code description} (≤ 400), nothing in the header the reader ignores;</li>
 * <li>it is a plain rack file — {@code version} 1, only {@code version},
 * {@code shared}, {@code devices}, {@code cables} at the top, ≤ 64 KiB;</li>
 * <li>every device is a BUILT-IN catalog device, named by its current id —
 * a rack the product ships may not depend on a plugin;</li>
 * <li>it MOUNTS — loaded into a fresh headless {@link Rack} through
 * {@link RackIO#fromJson}, the same door every patch uses, every device and
 * every cable arrives; a cable that does not is named with the reason
 * (the {@code StarterRacks} rule: a rack one cable short with nothing said
 * is the defect this exists to see);</li>
 * <li>it arrives at rest — no {@code armed}/{@code running} flag, and no
 * TAIL {@code follow}, is on: nothing watches, ticks or tails until the
 * receiver presses it;</li>
 * <li>nothing from the author's machine — no absolute path, no {@code ~},
 * no home-looking path, and no address but loopback in any setting;</li>
 * <li>{@code requires} are bare tool names and {@code kinds} are real
 * {@code ProjectKind} names — {@link RackCard} would quietly drop a bad one,
 * and a quiet drop is a rack that fits or requires less than it says.</li>
 * </ul>
 *
 * <p>Call off the EDT: judging mounts devices, which is cheap but not free.
 */
public final class RackJudge {

    /** The most a community rack file may weigh; a real one is two or three KiB. */
    public static final int MAX_BYTES = 64 * 1024;

    /** The most devices a community rack may hold — a shelf item, not a data centre. */
    static final int MAX_DEVICES = 24;

    private static final Set<String> TOP_LEVEL = Set.of("version", RackShare.SHARED, "devices", "cables");
    private static final Set<String> HEADER = Set.of("product", "name", "description", "author", "kinds", "requires");
    private static final Set<String> DEVICE_KEYS = Set.of("type", "state");
    private static final Set<String> CABLE_KEYS = Set.of("fromDevice", "fromPort", "toDevice", "toPort");
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "0.0.0.0");

    private RackJudge() {
    }

    /**
     * Judges a rack file as TEXT: the size cap and the parse, then every rule
     * of {@link #problems(String, JSONObject)}. What the gate and the loader
     * call, because a file that is too big or not JSON has no document to judge.
     */
    public static List<String> problemsOfText(String fileName, String text) {
        if (text == null) {
            return List.of(fileName + ": missing");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return List.of(fileName + ": over " + (MAX_BYTES / 1024) + " KiB");
        }
        JSONObject doc;
        try {
            doc = new JSONObject(text);
        } catch (JSONException ex) {
            return List.of(fileName + ": not a JSON object (" + ex.getMessage() + ")");
        }
        return problems(fileName, doc);
    }

    /** Every reason this document may not ship as a community rack; empty when it may. */
    public static List<String> problems(String fileName, JSONObject doc) {
        List<String> found = new ArrayList<>();
        if (doc == null) {
            return List.of(fileName + ": missing");
        }
        topLevel(doc, found);
        header(doc, found);
        boolean devicesSound = devices(doc, found);
        boolean cablesSound = cables(doc, found);
        // mounted whenever the SHAPE allows it, whatever else is wrong, so a
        // contributor reads every problem in one run rather than one per push
        if (devicesSound && cablesSound) {
            mount(doc, found);
        }
        List<String> out = new ArrayList<>(found.size());
        for (String problem : found) {
            out.add(fileName + ": " + problem);
        }
        return List.copyOf(out);
    }

    private static void topLevel(JSONObject doc, List<String> found) {
        for (String key : sorted(doc.keySet())) {
            if (!TOP_LEVEL.contains(key)) {
                found.add("unknown top-level key \"" + RackWiring.plain(key) + "\"");
            }
        }
        if (!(doc.opt("version") instanceof Integer v) || v != 1) {
            found.add("version must be 1");
        }
    }

    private static void header(JSONObject doc, List<String> found) {
        JSONObject header = doc.optJSONObject(RackShare.SHARED);
        if (header == null) {
            found.add("no \"shared\" header — a community rack must say what it is");
            return;
        }
        for (String key : sorted(header.keySet())) {
            if (!HEADER.contains(key) && !isLanguageSibling(key)) {
                found.add("unknown header key \"" + RackWiring.plain(key) + "\"");
            }
        }
        // the English card: what the file says before any translation
        RackCard card = RackCard.of(doc, "");
        if (card.name().isBlank()) {
            found.add("name is blank");
        } else if (codePoints(header.opt("name")) > RackCard.MAX_NAME) {
            found.add("name is over " + RackCard.MAX_NAME + " characters");
        }
        if (card.description().isBlank()) {
            found.add("description is blank");
        } else if (codePoints(header.opt("description")) > RackCard.MAX_DESCRIPTION) {
            found.add("description is over " + RackCard.MAX_DESCRIPTION + " characters");
        }
        JSONArray kinds = header.optJSONArray("kinds");
        if (header.has("kinds") && kinds == null) {
            found.add("kinds must be an array");
        } else if (kinds != null) {
            for (int i = 0; i < kinds.length(); i++) {
                if (!(kinds.opt(i) instanceof String k) || !isProjectKind(k)) {
                    found.add("kinds[" + i + "] is not a ProjectKind name: "
                            + RackWiring.plain(String.valueOf(kinds.opt(i))));
                }
            }
            if (card.kinds().size() != kinds.length()) {
                found.add("kinds holds a duplicate or more than " + RackCard.MAX_LIST + " entries");
            }
        }
        JSONArray requires = header.optJSONArray("requires");
        if (header.has("requires") && requires == null) {
            found.add("requires must be an array");
        } else if (requires != null && card.requires().size() != requires.length()) {
            // RackCard keeps only bare tool names, once each: a shorter list is a dropped entry
            found.add("requires must be bare tool names, each once (at most " + RackCard.MAX_LIST
                    + "): read " + card.requires() + " from " + requires.length() + " entries");
        }
    }

    /** True when the device list is sound enough to try mounting. */
    private static boolean devices(JSONObject doc, List<String> found) {
        JSONArray devices = doc.optJSONArray("devices");
        if (devices == null || devices.isEmpty()) {
            found.add("no devices");
            return false;
        }
        if (devices.length() > MAX_DEVICES) {
            found.add(devices.length() + " devices — at most " + MAX_DEVICES);
            return false;
        }
        boolean sound = true;
        for (int i = 0; i < devices.length(); i++) {
            JSONObject dj = devices.optJSONObject(i);
            if (dj == null || !(dj.opt("type") instanceof String type)) {
                found.add("devices[" + i + "] is not a device object with a type");
                sound = false;
                continue;
            }
            String where = "devices[" + i + "] " + RackWiring.plain(type);
            for (String key : sorted(dj.keySet())) {
                if (!DEVICE_KEYS.contains(key)) {
                    found.add(where + ": unknown key \"" + RackWiring.plain(key) + "\"");
                }
            }
            Optional<DeviceCatalog.Entry> entry = DeviceCatalog.byId(type);
            if (entry.isEmpty() || !entry.get().builtIn()) {
                found.add(where + " is not a built-in device — a community rack may not depend on a plugin");
                sound = false;
            } else if (!entry.get().id().equals(type)) {
                found.add(where + " is a retired id — use \"" + entry.get().id() + "\"");
                sound = false;
            }
            if (dj.has("state") && dj.optJSONObject("state") == null) {
                found.add(where + ": state must be an object");
                sound = false;
                continue;
            }
            JSONObject state = dj.optJSONObject("state");
            if (state != null) {
                sound &= state(where, type, state, found);
            }
        }
        return sound;
    }

    private static boolean state(String where, String type, JSONObject state, List<String> found) {
        boolean sound = true;
        for (String key : sorted(state.keySet())) {
            if (!(state.opt(key) instanceof String value)) {
                // RackIO reads every setting as a string; anything else does not load
                found.add(where + "." + RackWiring.plain(key) + " must be a string");
                sound = false;
                continue;
            }
            String setting = where + "." + RackWiring.plain(key);
            boolean selfStarting = "armed".equals(key) || "running".equals(key)
                    || ("follow".equals(key) && "tail".equals(type));
            if (selfStarting && "true".equalsIgnoreCase(value.trim())) {
                found.add(setting + " is on — a community rack arrives at rest");
            }
            if (value.indexOf('~') >= 0) {
                found.add(setting + " holds a ~ path — settings must be project-relative");
            }
            if (hasAbsolutePath(value)) {
                found.add(setting + " holds an absolute path — settings must be project-relative");
            }
            if (looksLikeHome(value)) {
                found.add(setting + " names a home directory");
            }
            String host = foreignHost(value);
            if (host != null) {
                found.add(setting + " addresses " + RackWiring.plain(host) + " — only localhost may ship in a rack");
            }
        }
        return sound;
    }

    /** True when the cable list is sound enough to try mounting. */
    private static boolean cables(JSONObject doc, List<String> found) {
        JSONArray cables = doc.optJSONArray("cables");
        if (cables == null || cables.isEmpty()) {
            found.add("no cables — a rack worth sharing is wired");
            return false;
        }
        JSONArray devices = doc.optJSONArray("devices");
        int deviceCount = devices == null ? 0 : devices.length();
        boolean sound = true;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cables.length(); i++) {
            JSONObject c = cables.optJSONObject(i);
            if (c == null || !(c.opt("fromDevice") instanceof Integer from) || !(c.opt("toDevice") instanceof Integer to)
                    || !(c.opt("fromPort") instanceof String fromPort) || !(c.opt("toPort") instanceof String toPort)) {
                found.add("cables[" + i + "] needs integer fromDevice/toDevice and string fromPort/toPort");
                sound = false;
                continue;
            }
            for (String key : sorted(c.keySet())) {
                if (!CABLE_KEYS.contains(key)) {
                    found.add("cables[" + i + "]: unknown key \"" + RackWiring.plain(key) + "\"");
                }
            }
            if (from < 0 || from >= deviceCount || to < 0 || to >= deviceCount) {
                found.add("cables[" + i + "] points at a device the rack does not have ("
                        + from + " -> " + to + " of " + deviceCount + ")");
                sound = false;
                continue;
            }
            if (!seen.add(from + "\n" + fromPort + "\n" + to + "\n" + toPort)) {
                found.add("cables[" + i + "] repeats an earlier cable");
                sound = false;
            }
        }
        return sound;
    }

    /**
     * Mounts the rack the way every patch is mounted and names what did not
     * arrive. {@link RackIO#fromJson} logs a cable it cannot connect and
     * carries on — right for a user's patch, where one lost cable must not
     * lose the rest — so the judge compares what mounted with what the file
     * asked for, cable by cable.
     */
    private static void mount(JSONObject doc, List<String> found) {
        Rack rack = new Rack();
        int before = found.size();
        try {
            // a fresh Rack aims at user.home; devices that read the project on
            // attach must not walk it just to be judged (the RackPresets rule)
            rack.setProjectDir(scratchDir());
            // imported(): the header dropped and every self-starting flag off, so
            // even a rack refused for arriving armed watches nothing while judged
            RackIO.fromJson(rack, RackShare.imported(doc, null));
            List<RackDevice> mounted = rack.getDevices();
            JSONArray devices = doc.getJSONArray("devices");
            JSONArray cables = doc.getJSONArray("cables");
            if (mounted.size() != devices.length()) {
                found.add("mounted " + mounted.size() + " of " + devices.length() + " devices");
                return;
            }
            List<Cable> live = rack.getCables();
            for (int i = 0; i < cables.length(); i++) {
                JSONObject c = cables.getJSONObject(i);
                RackDevice from = mounted.get(c.getInt("fromDevice"));
                RackDevice to = mounted.get(c.getInt("toDevice"));
                String fromPort = c.getString("fromPort");
                String toPort = c.getString("toPort");
                if (!arrived(live, from, fromPort, to, toPort)) {
                    found.add("cables[" + i + "] " + from.getTypeId() + "." + RackWiring.plain(fromPort) + " -> "
                            + to.getTypeId() + "." + RackWiring.plain(toPort) + " does not mount: "
                            + whyNot(from, fromPort, to, toPort));
                }
            }
            if (found.size() == before && live.size() != cables.length()) {
                found.add("mounted " + live.size() + " of " + cables.length() + " cables");
            }
        } catch (RuntimeException ex) {
            found.add("does not mount: " + ex);
        } finally {
            rack.shutdown();
        }
    }

    private static boolean arrived(List<Cable> live, RackDevice from, String fromPort, RackDevice to, String toPort) {
        for (Cable cable : live) {
            if (cable.getFrom().getDevice() == from && cable.getFrom().getId().equals(fromPort)
                    && cable.getTo().getDevice() == to && cable.getTo().getId().equals(toPort)) {
                return true;
            }
        }
        return false;
    }

    private static String whyNot(RackDevice from, String fromPort, RackDevice to, String toPort) {
        Port out = from.getPort(fromPort);
        Port in = to.getPort(toPort);
        if (out == null) {
            return from.getTitle() + " has no jack \"" + RackWiring.plain(fromPort) + "\"";
        }
        if (in == null) {
            return to.getTitle() + " has no jack \"" + RackWiring.plain(toPort) + "\"";
        }
        if (out.getDirection() != Port.Direction.OUT || in.getDirection() != Port.Direction.IN) {
            return "a cable runs from an OUT jack to an IN jack";
        }
        if (out.getType() != in.getType()) {
            return out.getType() + " cannot feed " + in.getType();
        }
        return "it would close a loop, or patch a device into itself";
    }

    // ---- the small linear scanners (no regex: a file is a stranger's text) ----

    /** A whitespace-separated word that starts like a path from a root or a drive. */
    static boolean hasAbsolutePath(String value) {
        int i = 0;
        int n = value.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            if (start < i && startsAbsolute(value, start, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsAbsolute(String v, int start, int end) {
        char first = v.charAt(start);
        if (first == '/' || first == '\\') {
            return true;
        }
        // C:\ or C:/ — a drive letter is ASCII by definition, not "a letter" in any script
        boolean drive = (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z');
        return end - start >= 3 && drive && v.charAt(start + 1) == ':'
                && (v.charAt(start + 2) == '\\' || v.charAt(start + 2) == '/');
    }

    static boolean looksLikeHome(String value) {
        String v = value.toLowerCase(Locale.ROOT).replace('\\', '/');
        return v.contains("/users/") || v.contains("/home/") || v.contains("$home") || v.contains("%userprofile%");
    }

    /** The first host named after a {@code ://} that is not this machine, or null. */
    static String foreignHost(String value) {
        int at = 0;
        while (true) {
            int scheme = value.indexOf("://", at);
            if (scheme < 0) {
                return null;
            }
            int start = scheme + 3;
            int end = start;
            if (end < value.length() && value.charAt(end) == '[') {
                int close = value.indexOf(']', end);
                end = close < 0 ? value.length() : close + 1;
            } else {
                while (end < value.length() && "/:?#".indexOf(value.charAt(end)) < 0
                        && !Character.isWhitespace(value.charAt(end))) {
                    end++;
                }
            }
            String host = value.substring(start, end).toLowerCase(Locale.ROOT);
            int user = host.lastIndexOf('@');
            if (user >= 0) {
                host = host.substring(user + 1);
            }
            if (!LOOPBACK.contains(host)) {
                return host.isEmpty() ? "(no host)" : host;
            }
            at = end;
        }
    }

    private static boolean isProjectKind(String name) {
        for (ProjectKind kind : ProjectKind.values()) {
            if (kind.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** {@code name.de}, {@code description.pt-br}: the translation siblings {@link RackCard} reads. */
    private static boolean isLanguageSibling(String key) {
        String tail;
        if (key.startsWith("name.")) {
            tail = key.substring("name.".length());
        } else if (key.startsWith("description.")) {
            tail = key.substring("description.".length());
        } else {
            return false;
        }
        if (tail.length() < 2 || tail.length() > 8) {
            return false;
        }
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (!((c >= 'a' && c <= 'z') || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static int codePoints(Object raw) {
        return raw instanceof String s ? s.strip().codePointCount(0, s.strip().length()) : 0;
    }

    /** Key sets come out of a hash map; problems must read the same on every run. */
    private static List<String> sorted(Set<String> keys) {
        List<String> out = new ArrayList<>(keys);
        out.sort(null);
        return out;
    }

    private static volatile File scratchDir;

    private static File scratchDir() {
        File dir = scratchDir;
        if (dir == null || !dir.isDirectory()) {
            try {
                dir = Files.createTempDirectory("nmox-rack-judge").toFile();
                dir.deleteOnExit();
            } catch (IOException ex) {
                dir = new File(System.getProperty("java.io.tmpdir"));
            }
            scratchDir = dir;
        }
        return dir;
    }
}
