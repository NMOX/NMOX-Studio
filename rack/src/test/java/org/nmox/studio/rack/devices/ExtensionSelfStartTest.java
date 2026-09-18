package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decision about extension devices, pinned: a restored extension toggle
 * CAN reach {@code services.exec}, so every toggle of a device that is not a
 * built-in arrives off when a rack is imported.
 *
 * <p>The path: {@code RackIO.fromJson} → {@code applyState} → the setter
 * {@code ExtensionDevice.Face.toggle} registered → {@code ToggleSwitch.setOn}
 * → the change listener the plugin passed to {@code ToggleHandle.onChange} —
 * arbitrary plugin code holding {@code DeviceServices}. Workspace Trust still
 * stands in front of the spawn, but in a workspace the receiver already
 * trusts, a stranger's file would run the plugin's command by being opened.
 */
class ExtensionSelfStartTest {

    private final Predicate<File> realGate = ExtensionDevice.trustGate;

    @AfterEach
    void restoreGate() {
        ExtensionDevice.trustGate = realGate;
    }

    /** A plugin a reasonable author could write: a WATCH switch that starts its watcher when flipped on. */
    private static DeviceExtension watcherPlugin() {
        return new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return new DeviceDescriptor("com.example.watcher", "WATCHER", "runs a command while WATCH is on",
                        new Color(90, 160, 200), DeviceCategory.UTILITY,
                        "A plugin whose WATCH switch starts its command the moment it is flipped on.\n"
                                + "Flip WATCH and the command runs; flip it off and it stops.",
                        2, List.of());
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                var watch = face.toggle("watch", "WATCH", false);
                face.toggle("verbose", "VERBOSE", false);
                watch.onChange(() -> {
                    if (watch.isOn()) {
                        services.exec(List.of("stranger-chosen-tool", "--watch"), line -> { }, code -> { });
                    } else {
                        services.stop();
                    }
                });
                return new DeviceLogic() {
                };
            }
        };
    }

    @Test
    @DisplayName("restoring an extension toggle ON runs the plugin's onChange, and that reaches services.exec — the trust gate is asked, which only exec does")
    void restoredToggleReachesExec() {
        List<File> asked = new ArrayList<>();
        ExtensionDevice.trustGate = dir -> {
            asked.add(dir);
            return false; // decline: the point is that exec was REACHED, not that anything ran
        };
        ExtensionDevice device = new ExtensionDevice(watcherPlugin());
        assertThat(asked).as("building and mounting the plugin asks for nothing").isEmpty();

        device.applyState(Map.of("watch", "true"));

        assertThat(asked).as("a patch value alone walked into the plugin's exec call").hasSize(1);
    }

    @Test
    @DisplayName("so the host declares EVERY extension toggle self-starting — it cannot tell a flag from a watcher")
    void everyExtensionToggleIsDeclaredSelfStarting() {
        ExtensionDevice device = new ExtensionDevice(watcherPlugin());
        assertThat(device.toggleKeys()).containsExactly("watch", "verbose");
        assertThat(device.selfStartingKeys()).containsExactlyElementsOf(device.toggleKeys());
    }

    @Test
    @DisplayName("an imported rack sets every switch of a non-built-in device off — installed extension and missing type alike — and the manifest counts each device once")
    void importedExtensionTogglesArriveOff() {
        JSONArray devices = new JSONArray();
        // installed on the test classpath (SpiFixtures.Good) — known, but not a built-in
        devices.put(new JSONObject().put("type", "org.nmox.fixture.echo").put("state", new JSONObject()
                .put("armed", "true").put("mode", "beta")));
        // not installed anywhere: a MISSING placeholder would keep this state verbatim for later
        devices.put(new JSONObject().put("type", "com.example.not.installed").put("state", new JSONObject()
                .put("watching", "TRUE").put("alsoOn", "true").put("target", "~/logs/app.log").put("level", "3")));
        // a built-in beside them: only its DECLARED key is touched
        devices.put(new JSONObject().put("type", "terminal").put("state", new JSONObject().put("follow", "true")));
        JSONObject shared = new JSONObject().put("version", 1).put(RackShare.SHARED, new JSONObject())
                .put("devices", devices).put("cables", new JSONArray());

        Path home = Path.of(System.getProperty("user.home"));
        JSONArray mounted = RackShare.imported(shared, home).getJSONArray("devices");
        assertThat(mounted.getJSONObject(0).getJSONObject("state").getString("armed")).isEqualTo("false");
        assertThat(mounted.getJSONObject(0).getJSONObject("state").getString("mode"))
                .as("a knob is a setting and travels").isEqualTo("beta");
        JSONObject missing = mounted.getJSONObject(1).getJSONObject("state");
        assertThat(missing.getString("watching")).as("Boolean.parseBoolean ignores case, so does the rule").isEqualTo("false");
        assertThat(missing.getString("alsoOn")).isEqualTo("false");
        assertThat(missing.getString("level")).isEqualTo("3");
        assertThat(missing.getString("target")).as("paths still expand").startsWith(home.toAbsolutePath().normalize().toString());
        assertThat(mounted.getJSONObject(2).getJSONObject("state").getString("follow"))
                .as("PHOSPHOR's follow is a SETTING of a built-in: it travels on").isEqualTo("true");

        assertThat(RackShare.inspect(shared, id -> true).atRest())
                .as("two devices arrive at rest — the missing one counts once, not once per switch").isEqualTo(2);
    }
}
