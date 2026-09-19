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
import javax.swing.SwingUtilities;
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
 * Two defences over extension devices, and why BOTH are kept.
 *
 * <p>A plugin's {@code ToggleHandle.onChange} is arbitrary code holding
 * {@code DeviceServices}, so it can reach {@code services.exec}. Until
 * v2.180.0 a patch load ran it: {@code RackIO.fromJson} → {@code applyState}
 * → the setter {@code ExtensionDevice.Face.toggle} registered →
 * {@code ToggleSwitch.setOn} → the plugin's listener. Workspace Trust still
 * stood in front of the spawn, but in a workspace the receiver already
 * trusted, opening a file ran a stranger's command (ledger 102).
 *
 * <ul>
 * <li><b>A restore is not a gesture</b> (v2.180.0): loading ANY patch — a
 * stranger's or your own — never runs a plugin's knob or toggle callback.
 * The plugin is told once afterwards through
 * {@code DeviceLogic.onStateRestored}, the only hook that can carry the
 * restored values.</li>
 * <li><b>Every extension switch arrives off</b> (v2.179.0): a rack that was
 * IMPORTED also has those switches turned off before it is mounted, because
 * the host cannot tell a plugin's flag from its watcher — so a stranger's
 * rack never even shows one on.</li>
 * </ul>
 *
 * <p>Neither makes the other redundant: the first stops plugin code running,
 * the second stops a stranger's rack arriving armed on the face.
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
    @DisplayName("a RESTORE never runs the plugin's callback: a patch value cannot walk into services.exec — while the same value set by a gesture still does")
    void restoreNeverRunsThePluginCallback() {
        List<File> asked = new ArrayList<>();
        ExtensionDevice.trustGate = dir -> {
            asked.add(dir);
            return false; // decline: the point is whether exec was REACHED, not that anything ran
        };
        ExtensionDevice device = new ExtensionDevice(watcherPlugin());
        assertThat(asked).as("building and mounting the plugin asks for nothing").isEmpty();

        device.applyState(Map.of("watch", "true"));

        assertThat(asked)
                .as("a patch value alone must not reach the plugin's exec call (ledger 102)").isEmpty();
        assertThat(device.getState().get("watch"))
                .as("the value is still restored — the face shows it, only the callback is skipped")
                .isEqualTo("true");

    }

    @Test
    @DisplayName("the suppression is the RESTORE phase alone, not a mute button: the same switch moved outside a restore still runs the plugin's callback")
    void outsideARestoreTheCallbackStillRuns() throws Exception {
        List<File> asked = new ArrayList<>();
        ExtensionDevice.trustGate = dir -> {
            asked.add(dir);
            return false;
        };
        List<DeviceFace.ToggleHandle> handle = new ArrayList<>();
        ExtensionDevice device = new ExtensionDevice(new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return watcherPlugin().descriptor();
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                var watch = face.toggle("watch", "WATCH", false);
                handle.add(watch);
                watch.onChange(() -> {
                    if (watch.isOn()) {
                        services.exec(List.of("stranger-chosen-tool", "--watch"), line -> { }, code -> { });
                    }
                });
                return new DeviceLogic() {
                };
            }
        });

        device.applyState(Map.of("watch", "true"));
        assertThat(asked).as("restored: the callback is skipped").isEmpty();

        // the plugin moves its own switch — no restore in sight
        handle.get(0).setOn(false);
        flushEdt();
        handle.get(0).setOn(true);
        flushEdt();
        assertThat(asked).as("moved outside a restore, the callback runs and reaches exec").hasSize(1);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("the plugin is TOLD once after a restore — the hook onAttached cannot serve, because a device is racked before its state is applied")
    void thePluginIsToldAfterTheRestore() {
        List<String> calls = new ArrayList<>();
        ExtensionDevice device = new ExtensionDevice(new DeviceExtension() {
            @Override
            public DeviceDescriptor descriptor() {
                return watcherPlugin().descriptor();
            }

            @Override
            public DeviceLogic build(DeviceFace face, DeviceServices services) {
                var watch = face.toggle("watch", "WATCH", false);
                return new DeviceLogic() {
                    @Override
                    public void onAttached(DeviceServices s) {
                        calls.add("onAttached:" + watch.isOn());
                    }

                    @Override
                    public void onStateRestored(DeviceServices s) {
                        calls.add("onStateRestored:" + watch.isOn());
                    }
                };
            }
        });

        device.applyState(Map.of("watch", "true"));
        assertThat(calls).as("told once, with the restored value readable").containsExactly("onStateRestored:true");

        calls.clear();
        device.applyState(Map.of("nothing-this-device-has", "x"));
        assertThat(calls).as("a state that sets nothing restores nothing, and says nothing").isEmpty();
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
