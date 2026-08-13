package org.nmox.studio.rack.devices;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v2.0.0 walk's face truths, pinned. The GUI walk mounted the
 * tutorial's counter and had to prove three things the unit suite had
 * never asked: that every declared control really lands on the plate,
 * that a knob-carrying file mounts at all (it could not — the fit law
 * fired at the first click instead of at load), and that an edited
 * file mounts from its CURRENT contents.
 */
class FaceRenderProbeTest {

    private static final String COUNTER_NO_UNITS = """
        {
          "id": "com.example.counter",
          "title": "COUNTER",
          "tagline": "counts the files in the project",
          "category": "OBSERVE",
          "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\\nPatch OUT into MONITOR to read the list.",
          "knobs": [ { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] } ],
          "ports": [
            { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
            { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
            { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
          ],
          "buttons": [ { "label": "COUNT", "role": "QUERY",
                         "command": ["git", "ls-files", "*.{{kind}}"],
                         "emit": "done", "trigger": "count" } ]
        }
        """;

    @AfterEach
    void reset() {
        UserDevices.dirOverride = null;
        UserDevices.invalidate();
    }

    @Test
    @DisplayName("the tutorial's knob-carrying example auto-sizes and mounts")
    void tutorialExampleMounts() {
        DeviceFile.Result r = DeviceFile.read(COUNTER_NO_UNITS);
        assertThat(r.problem()).isNull();
        // the walk's finding: at the silent 1U default this face does
        // not fit and 2.0.0 threw at the first click — the load path
        // now picks the smallest height that fits instead
        DeviceFile fitted = UserDevices.loadForTest(r.device());
        assertThat(fitted).as("a knob file with no declared units must fit itself").isNotNull();
        assertThat(fitted.units()).isEqualTo(2);
    }

    @Test
    @DisplayName("every declared control lands on the plate, inside it")
    void allControlsOnPlate() {
        DeviceFile fitted = UserDevices.loadForTest(
                DeviceFile.read(COUNTER_NO_UNITS).device());
        ExtensionDevice dev = new ExtensionDevice(new JsonDeviceExtension(fitted));
        List<String> names = new ArrayList<>();
        int width = dev.getPreferredSize().width;
        int height = dev.getPreferredSize().height;
        for (Component c : dev.getComponents()) {
            if (c.getAccessibleContext() != null
                    && c.getAccessibleContext().getAccessibleName() != null) {
                names.add(c.getAccessibleContext().getAccessibleName());
            }
            assertThat(c.getX() + c.getWidth())
                    .as("control paints inside the plate width").isLessThanOrEqualTo(width);
            assertThat(c.getY() + c.getHeight())
                    .as("control paints inside the plate height").isLessThanOrEqualTo(height);
        }
        assertThat(names).contains("KIND", "COUNT", "RUN", "FAIL");
        dev.dispose();
    }

    @Test
    @DisplayName("a declared height too small for the face is refused at load")
    void declaredTooSmallRefused() {
        String oneUnit = COUNTER_NO_UNITS.replace("\"category\": \"OBSERVE\",",
                "\"category\": \"OBSERVE\",\n          \"units\": 1,");
        DeviceFile.Result r = DeviceFile.read(oneUnit);
        assertThat(r.problem()).isNull();
        // the file SAID 1U; honoring it is impossible, so the load
        // refuses — auto-size never overrides an explicit declaration
        assertThat(UserDevices.loadForTest(r.device())).isNull();
    }

    @Test
    @DisplayName("declining trust says why on the LCD, not a bare EXIT -1")
    void trustDeclineSpeaksHuman() throws Exception {
        var oldGate = ExtensionDevice.trustGate;
        try {
            ExtensionDevice.trustGate = dir -> false;
            DeviceFile fitted = UserDevices.loadForTest(
                    DeviceFile.read(COUNTER_NO_UNITS).device());
            ExtensionDevice dev = new ExtensionDevice(new JsonDeviceExtension(fitted));
            org.nmox.studio.rack.ui.controls.RackButton count = null;
            org.nmox.studio.rack.ui.controls.LcdDisplay screen = null;
            for (Component c : dev.getComponents()) {
                if (c instanceof org.nmox.studio.rack.ui.controls.RackButton b
                        && "COUNT".equals(b.getAccessibleContext().getAccessibleName())) {
                    count = b;
                }
                if (c instanceof org.nmox.studio.rack.ui.controls.LcdDisplay l) {
                    screen = l;
                }
            }
            assertThat(count).isNotNull();
            assertThat(screen).isNotNull();
            count.getAccessibleContext().getAccessibleAction().doAccessibleAction(0);
            // LCD writes marshal to the EDT; drain it before reading
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertThat(screen.getText())
                    .as("a decline must speak the host's refusal, not a fake exit code")
                    .contains("EXECUTION REFUSED");
            dev.dispose();
        } finally {
            ExtensionDevice.trustGate = oldGate;
        }
    }
}
