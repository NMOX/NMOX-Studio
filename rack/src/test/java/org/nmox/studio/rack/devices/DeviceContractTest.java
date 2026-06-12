package org.nmox.studio.rack.devices;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.ui.controls.Knob;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The control-surface contract, enforced for every cataloged device:
 * ports are unique and labeled, state survives a save/load round trip,
 * and the faceplate fits the rack. New devices inherit these checks
 * for free by being in the DeviceType catalog.
 */
class DeviceContractTest {

    @ParameterizedTest
    @EnumSource(DeviceType.class)
    @DisplayName("Port ids must be unique and labeled within a device")
    void portIdsUniqueAndLabeled(DeviceType type) {
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
    @EnumSource(DeviceType.class)
    @DisplayName("Jacks must sit inside the back panel")
    void portsInsideThePanel(DeviceType type) {
        RackDevice device = type.create();
        int w = device.getPreferredSize().width;
        int h = device.getPreferredSize().height;
        for (Port p : device.getPorts()) {
            assertThat(p.getX()).as(type + "." + p.getId() + " x").isBetween(0, w);
            assertThat(p.getY()).as(type + "." + p.getId() + " y").isBetween(0, h);
        }
    }

    @ParameterizedTest
    @EnumSource(DeviceType.class)
    @DisplayName("Control state must survive a save/load round trip")
    void stateRoundTrips(DeviceType type) {
        RackDevice device = type.create();
        Map<String, String> state = device.getState();

        RackDevice clone = type.create();
        clone.applyState(state);

        assertThat(clone.getState())
                .as(type + " state after round trip")
                .isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(DeviceType.class)
    @DisplayName("Controls must stay inside the faceplate, clear of the ears")
    void controlsInsideTheFaceplate(DeviceType type) {
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
    @EnumSource(DeviceType.class)
    @DisplayName("The port lexicon: long-runners pair START with STOP; gates read RUNNING/SERVING")
    void portLexicon(DeviceType type) {
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
    @EnumSource(DeviceType.class)
    @DisplayName("Every device has a palette category and a usage recipe")
    void shelfGuidance(DeviceType type) {
        assertThat(type.getPaletteCategory()).isNotNull();
        assertThat(type.getUsage()).as(type + " usage").isNotBlank();
        // two lines minimum: what it does, and a concrete recipe
        assertThat(type.getUsage()).as(type + " usage has a recipe line").contains("\n");
        assertThat(type.getUsage().length()).as(type + " usage substance").isGreaterThan(60);
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
