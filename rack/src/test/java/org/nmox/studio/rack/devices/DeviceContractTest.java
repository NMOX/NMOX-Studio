package org.nmox.studio.rack.devices;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.ui.controls.Knob;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The control-surface contract, enforced for every cataloged device:
 * ports are unique and labeled, state survives a save/load round trip,
 * and the faceplate fits the rack. New devices inherit these checks for
 * free by being in the DeviceCatalog — parameterization runs over the
 * catalog, not the enum, so a registry-contributed device (the future
 * device SPI) is held to the same laws as a built-in.
 */
class DeviceContractTest {

    static java.util.List<DeviceCatalog.Entry> catalog() {
        return DeviceCatalog.all();
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Port ids must be unique and labeled within a device")
    void portIdsUniqueAndLabeled(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        Set<String> seen = new HashSet<>();
        for (Port p : device.getPorts()) {
            assertThat(p.getId()).as(type + " port id").isNotBlank();
            assertThat(p.getLabel()).as(type + " port label").isNotBlank();
            assertThat(seen.add(p.getDirection() + ":" + p.getId()))
                    .as(type + " duplicate port id '" + p.getId() + "'")
                    .isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Jacks must sit inside the back panel")
    void portsInsideThePanel(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        int w = device.getPreferredSize().width;
        int h = device.getPreferredSize().height;
        for (Port p : device.getPorts()) {
            assertThat(p.getX()).as(type + "." + p.getId() + " x").isBetween(0, w);
            assertThat(p.getY()).as(type + "." + p.getId() + " y").isBetween(0, h);
        }
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Control state must survive a save/load round trip")
    void stateRoundTrips(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        Map<String, String> state = device.getState();

        RackDevice clone = type.create();
        clone.applyState(state);

        assertThat(clone.getState())
                .as(type + " state after round trip")
                .isEqualTo(state);
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Controls must stay inside the faceplate, clear of the ears")
    void controlsInsideTheFaceplate(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        int w = device.getPreferredSize().width;
        int h = device.getPreferredSize().height;
        for (java.awt.Component c : device.getComponents()) {
            assertThat(c.getX()).as(type + " control x " + c.getClass().getSimpleName())
                    .isGreaterThanOrEqualTo(26); // EAR_WIDTH
            assertThat(c.getX() + c.getWidth()).as(type + " control right edge")
                    .isLessThanOrEqualTo(w - 26);
            assertThat(c.getY() + c.getHeight()).as(type + " control bottom edge")
                    .isLessThanOrEqualTo(h);
        }
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("The port lexicon: long-runners pair START with STOP; gates read RUNNING/SERVING")
    void portLexicon(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        java.util.Set<String> inIds = new HashSet<>();
        java.util.Set<String> gateLabels = new HashSet<>();
        for (Port p : device.getPorts()) {
            if (p.getDirection() == Port.Direction.IN) {
                inIds.add(p.getId());
            }
            if (p.getDirection() == Port.Direction.OUT
                    && p.getType() == org.nmox.studio.rack.model.SignalType.GATE) {
                gateLabels.add(p.getLabel());
            }
        }
        // a device you can start long-running, you must be able to stop by cable
        if (inIds.contains("serve") || inIds.contains("start")) {
            assertThat(inIds).as(type + " serve/start needs stop").contains("stop");
        }
        // gate outputs speak one vocabulary
        for (String label : gateLabels) {
            assertThat(label).as(type + " gate label")
                    .isIn("RUNNING", "SERVING", "ENABLE");
        }
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Every placed control exposes a non-blank accessible name")
    void controlsExposeAccessibleNames(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        java.util.List<String> nameless = new java.util.ArrayList<>();
        for (java.awt.Component c : device.getComponents()) {
            String where = c.getClass().getSimpleName() + " at " + c.getX() + "," + c.getY();
            if (!(c instanceof javax.accessibility.Accessible)
                    || c.getAccessibleContext() == null
                    || c.getAccessibleContext().getAccessibleName() == null
                    || c.getAccessibleContext().getAccessibleName().isBlank()) {
                nameless.add(where);
            }
        }
        assertThat(nameless).as(type + " controls without an accessible name").isEmpty();
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("Every device has a palette category and a usage recipe")
    void shelfGuidance(DeviceCatalog.Entry type) {
        assertThat(type.category()).isNotNull();
        assertThat(type.usage()).as(type + " usage").isNotBlank();
        // two lines minimum: what it does, and a concrete recipe
        assertThat(type.usage()).as(type + " usage has a recipe line").contains("\n");
        assertThat(type.usage().length()).as(type + " usage substance").isGreaterThan(60);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Knob.selectOption matches by name, falls back to legacy index, ignores junk")
    void knobSelectOption() {
        Knob knob = new Knob("TEST", new String[]{"alpha", "beta", "gamma"}, 0);

        knob.selectOption("beta");
        assertThat(knob.getSelectedOption()).isEqualTo("beta");

        knob.selectOption("2");          // legacy index format
        assertThat(knob.getSelectedOption()).isEqualTo("gamma");

        knob.selectOption("nonsense");   // unknown: selection unchanged
        assertThat(knob.getSelectedOption()).isEqualTo("gamma");
    }
}
