package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.PortSpec;

/**
 * A device written as a file — the v2.0.0 headline.
 *
 * <p>The Device SPI (v1.55.0) opened the rack to third parties, but only
 * to those willing to author a NetBeans module in Java. Six catalogs had
 * already gained user-writable drop-in siblings by v1.305.0; the rack —
 * the product's own soul — was the one that never did. This is that
 * sibling: a JSON file in {@code ~/.nmox/devices.d/} becomes a real
 * device on the shelf, wired, patchable, and recorded like any other.
 *
 * <p>This class is the pure half: parse and judge, no I/O and no Swing.
 * {@link #read} returns either a device or the reason it was refused,
 * and the refusal is the point — a device file names commands that will
 * EXECUTE, so it is held to the strictest validation in the drop-in
 * family (the v1.305.0 doctor.d lesson), and a file that fails any rule
 * is skipped whole rather than half-loaded.
 *
 * <p>What the host still enforces, never this file: workspace trust on
 * every spawn, button colour from role, accessible names, the port
 * lexicon, and the shelf laws {@link DeviceCatalog#validate} pins on
 * built-ins too. A device file cannot express a red GO or an ungated
 * command because the SPI it compiles to cannot express them.
 */
public final class DeviceFile {

    /** Tokens that would turn an argv into a shell script. */
    private static final List<String> SHELL_METACHARS =
            List.of("|", "&", ";", ">", "<", "`", "$(", "\n", "\r");

    /** A faceplate taller than this is a design smell, not a device. */
    private static final int MAX_UNITS = 4;

    private final String id;
    private final String title;
    private final String tagline;
    private final Color accent;
    private final DeviceCategory category;
    private final int units;
    private final String usage;
    private final List<PortSpec> ports;
    private final List<Knob> knobs;
    private final List<Button> buttons;
    private final String lcdLabel;
    private final int lcdWidth;

    private DeviceFile(String id, String title, String tagline, Color accent,
            DeviceCategory category, int units, String usage, List<PortSpec> ports,
            List<Knob> knobs, List<Button> buttons, String lcdLabel, int lcdWidth) {
        this.id = id;
        this.title = title;
        this.tagline = tagline;
        this.accent = accent;
        this.category = category;
        this.units = units;
        this.usage = usage;
        this.ports = List.copyOf(ports);
        this.knobs = List.copyOf(knobs);
        this.buttons = List.copyOf(buttons);
        this.lcdLabel = lcdLabel;
        this.lcdWidth = lcdWidth;
    }

    /** A labelled selector; its key is what {@code {{key}}} substitutes. */
    public record Knob(String key, String label, List<String> options) { }

    /**
     * A button and what it does. A STOP-role button stops the running
     * process and needs no command; every other role runs one.
     *
     * @param emit    an OUT trigger port pulsed on exit, or null
     * @param trigger an IN trigger port that presses this button, or null
     */
    public record Button(String label, DeviceFace.ButtonRole role, List<String> command,
            String emit, String trigger) { }

    /** The device, or the reason it was refused — never both. */
    public record Result(DeviceFile device, String problem) {
        public boolean ok() {
            return device != null;
        }
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String tagline() {
        return tagline;
    }

    public Color accent() {
        return accent;
    }

    public DeviceCategory category() {
        return category;
    }

    public int units() {
        return units;
    }

    public String usage() {
        return usage;
    }

    public List<PortSpec> ports() {
        return ports;
    }

    public List<Knob> knobs() {
        return knobs;
    }

    public List<Button> buttons() {
        return buttons;
    }

    public String lcdLabel() {
        return lcdLabel;
    }

    public int lcdWidth() {
        return lcdWidth;
    }

    /**
     * Substitutes {@code {{key}}} in one argv from the knob selections.
     * Every reference is validated at read time, so a token reaching
     * here can only name a knob this device declares.
     */
    public static List<String> substitute(List<String> command,
            java.util.function.Function<String, String> knobValue) {
        List<String> out = new ArrayList<>(command.size());
        for (String token : command) {
            String t = token;
            for (String key : varsIn(token)) {
                String value = knobValue.apply(key);
                t = t.replace("{{" + key + "}}", value == null ? "" : value);
            }
            out.add(t);
        }
        return out;
    }

    /** The {@code {{key}}} names inside one token, in order of appearance. */
    static List<String> varsIn(String token) {
        List<String> keys = new ArrayList<>();
        int i = 0;
        while (true) {
            int open = token.indexOf("{{", i);
            if (open < 0) {
                return keys;
            }
            int close = token.indexOf("}}", open + 2);
            if (close < 0) {
                return keys;
            }
            keys.add(token.substring(open + 2, close).trim());
            i = close + 2;
        }
    }

    /**
     * Reads one device file. Returns the device, or the problem that
     * disqualifies the WHOLE file — a half-valid device would put a
     * control on the shelf that does not do what its label says.
     */
    public static Result read(String json) {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException ex) {
            return new Result(null, "not valid JSON (" + ex.getMessage() + ")");
        }

        String id = o.optString("id", "").trim();
        if (id.isEmpty()) {
            return new Result(null, "needs an \"id\"");
        }
        if (!id.contains(".")) {
            return new Result(null, "id \"" + id + "\" must be reverse-DNS namespaced "
                    + "(contain a dot) — un-dotted ids belong to the built-in fleet");
        }
        String title = o.optString("title", "").trim();
        if (title.isEmpty()) {
            return new Result(null, "needs a \"title\"");
        }
        String tagline = o.optString("tagline", "").trim();
        if (tagline.isEmpty()) {
            return new Result(null, "needs a \"tagline\" (one line, what it is for)");
        }
        String usage = o.optString("usage", "").trim();
        if (usage.length() <= 60 || !usage.contains("\n")) {
            return new Result(null, "\"usage\" must be at least two lines and more than "
                    + "60 characters (what it does, then a patch recipe — the shelf law)");
        }

        DeviceCategory category;
        String cat = o.optString("category", "").trim().toUpperCase(Locale.ROOT);
        try {
            category = DeviceCategory.valueOf(cat);
        } catch (IllegalArgumentException ex) {
            return new Result(null, "\"category\" must be one of "
                    + List.of(DeviceCategory.values()) + ", not \"" + cat + "\"");
        }

        Color accent;
        String hex = o.optString("accent", "#6EBEA0").trim();
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            return new Result(null, "\"accent\" must be #RRGGBB, not \"" + hex + "\"");
        }
        accent = Color.decode(hex);

        int units = o.optInt("units", 1);
        if (units < 1 || units > MAX_UNITS) {
            return new Result(null, "\"units\" must be 1–" + MAX_UNITS + ", not " + units);
        }

        List<PortSpec> ports = new ArrayList<>();
        Set<String> inTriggers = new HashSet<>();
        Set<String> outTriggers = new HashSet<>();
        JSONArray portArr = o.optJSONArray("ports");
        if (portArr != null) {
            for (int i = 0; i < portArr.length(); i++) {
                JSONObject p = portArr.optJSONObject(i);
                if (p == null) {
                    return new Result(null, "ports[" + i + "] is not an object");
                }
                String pid = p.optString("id", "").trim();
                String label = p.optString("label", "").trim();
                if (pid.isEmpty() || label.isEmpty()) {
                    return new Result(null, "ports[" + i + "] needs an \"id\" and a \"label\"");
                }
                PortSpec.Direction dir;
                PortSpec.Signal sig;
                try {
                    dir = PortSpec.Direction.valueOf(
                            p.optString("direction", "").trim().toUpperCase(Locale.ROOT));
                    sig = PortSpec.Signal.valueOf(
                            p.optString("signal", "").trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return new Result(null, "ports[" + i + "] needs \"direction\" IN or OUT "
                            + "and \"signal\" TRIGGER, DATA, or GATE");
                }
                ports.add(new PortSpec(pid, label, dir, sig));
                if (sig == PortSpec.Signal.TRIGGER) {
                    (dir == PortSpec.Direction.IN ? inTriggers : outTriggers).add(pid);
                }
            }
        }

        List<Knob> knobs = new ArrayList<>();
        Set<String> knobKeys = new HashSet<>();
        JSONArray knobArr = o.optJSONArray("knobs");
        if (knobArr != null) {
            for (int i = 0; i < knobArr.length(); i++) {
                JSONObject k = knobArr.optJSONObject(i);
                if (k == null) {
                    return new Result(null, "knobs[" + i + "] is not an object");
                }
                String key = k.optString("key", "").trim();
                String label = k.optString("label", "").trim();
                if (key.isEmpty() || label.isEmpty()) {
                    return new Result(null, "knobs[" + i + "] needs a \"key\" and a \"label\"");
                }
                if (!knobKeys.add(key)) {
                    return new Result(null, "two knobs share the key \"" + key + "\"");
                }
                JSONArray opts = k.optJSONArray("options");
                if (opts == null || opts.length() == 0) {
                    return new Result(null, "knob \"" + key + "\" needs a non-empty \"options\"");
                }
                List<String> options = new ArrayList<>();
                for (int j = 0; j < opts.length(); j++) {
                    String opt = opts.optString(j, "").trim();
                    if (opt.isEmpty()) {
                        return new Result(null, "knob \"" + key + "\" has a blank option");
                    }
                    options.add(opt);
                }
                knobs.add(new Knob(key, label, options));
            }
        }

        List<Button> buttons = new ArrayList<>();
        JSONArray btnArr = o.optJSONArray("buttons");
        if (btnArr == null || btnArr.length() == 0) {
            return new Result(null, "needs at least one button — a device with no control "
                    + "is a label, not a device");
        }
        for (int i = 0; i < btnArr.length(); i++) {
            JSONObject b = btnArr.optJSONObject(i);
            if (b == null) {
                return new Result(null, "buttons[" + i + "] is not an object");
            }
            String label = b.optString("label", "").trim();
            if (label.isEmpty()) {
                return new Result(null, "buttons[" + i + "] needs a \"label\" "
                        + "(every control is named — the accessibility law)");
            }
            DeviceFace.ButtonRole role;
            try {
                role = DeviceFace.ButtonRole.valueOf(
                        b.optString("role", "").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return new Result(null, "button \"" + label + "\" needs a \"role\" of "
                        + "GO, STOP, MUTATE, or QUERY (the colour law picks the colour)");
            }

            List<String> command = new ArrayList<>();
            JSONArray cmd = b.optJSONArray("command");
            if (role == DeviceFace.ButtonRole.STOP) {
                if (cmd != null) {
                    return new Result(null, "button \"" + label + "\" is a STOP: it stops the "
                            + "running command and must not declare one of its own");
                }
            } else {
                if (cmd == null || cmd.length() == 0) {
                    return new Result(null, "button \"" + label + "\" needs a \"command\" array");
                }
                for (int j = 0; j < cmd.length(); j++) {
                    String token = cmd.optString(j, "");
                    if (token.isBlank()) {
                        return new Result(null, "button \"" + label + "\" has a blank "
                                + "command token at " + j);
                    }
                    for (String meta : SHELL_METACHARS) {
                        if (token.contains(meta)) {
                            return new Result(null, "button \"" + label + "\" command token \""
                                    + token + "\" contains \"" + meta.replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                    + "\" — commands are argv, never a shell line");
                        }
                    }
                    for (String key : varsIn(token)) {
                        if (!knobKeys.contains(key)) {
                            return new Result(null, "button \"" + label + "\" refers to {{" + key
                                    + "}}, which no knob declares — an unknown variable would "
                                    + "run as a literal argument");
                        }
                    }
                    command.add(token);
                }
                String tool = command.get(0);
                if (tool.contains("/") || tool.contains("\\")) {
                    return new Result(null, "button \"" + label + "\" runs \"" + tool
                            + "\": the tool must be a bare name found on PATH, never a path");
                }
                if (!varsIn(tool).isEmpty()) {
                    return new Result(null, "button \"" + label + "\" builds its tool name from "
                            + "a knob — the tool must be a literal so it can be read before it runs");
                }
            }

            String emit = b.optString("emit", "").trim();
            if (!emit.isEmpty() && !outTriggers.contains(emit)) {
                return new Result(null, "button \"" + label + "\" emits \"" + emit
                        + "\", which is not a declared OUT TRIGGER port");
            }
            String trigger = b.optString("trigger", "").trim();
            if (!trigger.isEmpty() && !inTriggers.contains(trigger)) {
                return new Result(null, "button \"" + label + "\" is triggered by \"" + trigger
                        + "\", which is not a declared IN TRIGGER port");
            }
            buttons.add(new Button(label, role, command,
                    emit.isEmpty() ? null : emit, trigger.isEmpty() ? null : trigger));
        }

        JSONObject lcd = o.optJSONObject("lcd");
        String lcdLabel = lcd == null ? "STATUS" : lcd.optString("label", "STATUS").trim();
        int lcdWidth = lcd == null ? 420 : lcd.optInt("widthPx", 420);
        if (lcdLabel.isEmpty()) {
            return new Result(null, "the lcd needs a \"label\"");
        }
        if (lcdWidth < 60 || lcdWidth > 900) {
            return new Result(null, "lcd \"widthPx\" must be 60–900, not " + lcdWidth);
        }

        return new Result(new DeviceFile(id, title, tagline, accent, category, units,
                usage, ports, knobs, buttons, lcdLabel, lcdWidth), null);
    }
}
