package org.nmox.studio.rack.model;

import java.nio.file.Path;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rack shared as a file travels without naming its sender, arrives at rest,
 * and says what it holds before anything mounts (v2.176.0).
 */
class RackShareTest {

    private static final Path HOME = Path.of("/Users/sender");
    /** The sender's home as this platform spells it, with '/' — Windows makes it {@code D:/Users/sender}. */
    private static final String SENDER = HOME.toAbsolutePath().normalize().toString().replace('\\', '/');
    private static final Path RECEIVER = Path.of("/home/receiver");

    private static JSONObject patch() {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray devices = new JSONArray();
        devices.put(new JSONObject().put("type", "tail").put("state", new JSONObject()
                .put("file", SENDER + "/proj/logs/app.log").put("lines", "40")));
        devices.put(new JSONObject().put("type", "reflex").put("state", new JSONObject()
                .put("armed", "true").put("filter", "1")));
        devices.put(new JSONObject().put("type", "tempo").put("state", new JSONObject()
                .put("running", "true").put("rate", "2")));
        devices.put(new JSONObject().put("type", "cmd").put("state", new JSONObject()
                .put("command", "npm run build").put("cwd", SENDER)));
        devices.put(new JSONObject().put("type", "com.example.uptime").put("state", new JSONObject()));
        devices.put(new JSONObject().put("type", "console").put("state", new JSONObject().put("tap", "1")));
        root.put("devices", devices);
        JSONArray cables = new JSONArray();
        cables.put(new JSONObject().put("fromDevice", 1).put("fromPort", "changed").put("toDevice", 3).put("toPort", "run"));
        cables.put(new JSONObject().put("fromDevice", 3).put("fromPort", "out").put("toDevice", 5).put("toPort", "in"));
        root.put("cables", cables);
        return root;
    }

    @Test
    @DisplayName("export rewrites every path under the sender's home to ~ — a home path is a username — and names only the product version")
    void exportHidesTheSender() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        assertThat(shared.getJSONObject(RackShare.SHARED).getString("product")).isEqualTo("2.176.0");
        assertThat(shared.getJSONObject(RackShare.SHARED).keySet())
                .as("nothing else about the sender travels").containsExactly("product");
        JSONArray devices = shared.getJSONArray("devices");
        assertThat(devices.getJSONObject(0).getJSONObject("state").getString("file"))
                .isEqualTo("~/proj/logs/app.log");
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("cwd"))
                .as("the home directory itself is ~, not ~/").isEqualTo("~");
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("command"))
                .as("everything that is not a home path travels verbatim — the commands ARE the point")
                .isEqualTo("npm run build");
        assertThat(shared.toString()).as("no trace of the sender's home").doesNotContain(SENDER);
        assertThat(patch().toString()).as("export never mutates its input").contains(SENDER);
    }

    @Test
    @DisplayName("import expands ~ to the receiver's home, sets every self-starting flag off, and drops the header so what mounts is a plain patch")
    void importArrivesAtRest() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        JSONObject mounted = RackShare.imported(shared, RECEIVER);
        String receiver = RECEIVER.toAbsolutePath().normalize().toString();
        assertThat(mounted.has(RackShare.SHARED)).isFalse();
        JSONArray devices = mounted.getJSONArray("devices");
        assertThat(devices.getJSONObject(0).getJSONObject("state").getString("file"))
                .as("expanded with the receiver's own separator")
                .isEqualTo(RECEIVER.toAbsolutePath().normalize().resolve("proj").resolve("logs").resolve("app.log").toString());
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("cwd")).isEqualTo(receiver);
        assertThat(devices.getJSONObject(1).getJSONObject("state").getString("armed"))
                .as("a watcher saved armed must not start watching because a file was opened").isEqualTo("false");
        assertThat(devices.getJSONObject(2).getJSONObject("state").getString("running"))
                .as("a clock saved running must not start firing triggers into whatever it was cabled to").isEqualTo("false");
        assertThat(devices.getJSONObject(1).getJSONObject("state").getString("filter"))
                .as("only the self-starting flags change").isEqualTo("1");
        assertThat(mounted.getJSONArray("cables")).hasSize(2);
    }

    @Test
    @DisplayName("the manifest names every device, marks the ones this install lacks, counts cables and arrivals at rest, and lists the settings worth reading")
    void manifestSaysWhatIsInside() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        Set<String> known = Set.of("tail", "reflex", "tempo", "cmd", "console");
        RackShare.Manifest m = RackShare.inspect(shared, known::contains);
        assertThat(m.devices()).extracting(RackShare.Device::typeId)
                .containsExactly("tail", "reflex", "tempo", "cmd", "com.example.uptime", "console");
        assertThat(m.unknownTypes()).as("a plugin device this install does not have mounts as a placeholder — say so first")
                .containsExactly("com.example.uptime");
        assertThat(m.complete()).isFalse();
        assertThat(m.cables()).isEqualTo(2);
        assertThat(m.atRest()).as("two devices were saved armed/running").isEqualTo(2);
        assertThat(m.sharedBy()).isEqualTo("2.176.0");
        assertThat(m.settings()).extracting(RackShare.Setting::value)
                .as("commands and paths are shown; knob positions and switches are not")
                .containsExactlyInAnyOrder("~/proj/logs/app.log", "npm run build", "~");
    }

    @Test
    @DisplayName("a device entry that is not an object is refused by its slot from inspect, imported and export alike — never org.json's own exception; a header alone is an empty manifest")
    void hostileDeviceEntryIsRefusedByName() {
        JSONObject hostile = new JSONObject("{\"shared\":{},\"devices\":[{\"type\":\"tempo\",\"state\":{}},1]}");
        for (String door : java.util.List.of("inspect", "imported", "export")) {
            Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> {
                switch (door) {
                    case "inspect" -> RackShare.inspect(hostile, id -> true);
                    case "imported" -> RackShare.imported(hostile, RECEIVER);
                    default -> RackShare.export(hostile, HOME, "2.176.0");
                }
            });
            assertThat(t).as(door + " refuses").isInstanceOf(IllegalArgumentException.class);
            assertThat(t.getMessage()).as(door + " names the slot").contains("devices[1]");
        }
        JSONObject noDevices = new JSONObject("{\"shared\":{}}");
        assertThat(RackShare.inspect(noDevices, id -> true).devices()).as("a header alone is an empty manifest").isEmpty();
        assertThat(RackShare.imported(noDevices, RECEIVER).has("shared")).isFalse();
    }

    @Test
    @DisplayName("a plain Save Patch file is not a shared one, and inspecting it still works")
    void plainPatchIsNotShared() {
        assertThat(RackShare.isShared(patch())).isFalse();
        assertThat(RackShare.isShared(RackShare.export(patch(), HOME, "x"))).isTrue();
        RackShare.Manifest m = RackShare.inspect(patch(), t -> true);
        assertThat(m.sharedBy()).isNull();
        assertThat(m.unknownTypes()).isEmpty();
    }

    @Test
    @DisplayName("worth reading: a command, a path or an address; not a number, a switch or a short word")
    void worthReading() {
        assertThat(RackShare.worthReading("npm run build")).isTrue();
        assertThat(RackShare.worthReading("/var/log/app.log")).isTrue();
        assertThat(RackShare.worthReading("C:\\Users\\x")).isTrue();
        assertThat(RackShare.worthReading("http://localhost:3000/health")).isTrue();
        assertThat(RackShare.worthReading("~/proj")).isTrue();
        assertThat(RackShare.worthReading("2")).isFalse();
        assertThat(RackShare.worthReading("-1.5")).isFalse();
        assertThat(RackShare.worthReading("false")).isFalse();
        assertThat(RackShare.worthReading("pytest")).isFalse();
        assertThat(RackShare.worthReading("")).isFalse();
        assertThat(RackShare.worthReading(null)).isFalse();
    }

    @Test
    @DisplayName("a home elsewhere on the path is not the sender's home: only the prefix is rewritten")
    void onlyThePrefixIsHome() {
        JSONObject root = new JSONObject().put("version", 1)
                .put("devices", new JSONArray().put(new JSONObject().put("type", "tail").put("state",
                        new JSONObject().put("file", "/srv" + SENDER + "/x").put("note", SENDER + "ling/y"))))
                .put("cables", new JSONArray());
        JSONObject shared = RackShare.export(root, HOME, "x");
        JSONObject state = shared.getJSONArray("devices").getJSONObject(0).getJSONObject("state");
        assertThat(state.getString("file")).isEqualTo("/srv" + SENDER + "/x");
        assertThat(state.getString("note")).as("/Users/senderling is another user").isEqualTo(SENDER + "ling/y");
    }
}
