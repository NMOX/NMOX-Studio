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

    /** Borders may kiss by this many pixels; more than this hides content. */
    private static final int TOUCH = 2;

    /** The control types a faceplate places; everything else is chrome. */
    private static final java.util.List<Class<?>> CONTROLS = java.util.List.of(
            Knob.class,
            org.nmox.studio.rack.ui.controls.ToggleSwitch.class,
            org.nmox.studio.rack.ui.controls.RackButton.class,
            org.nmox.studio.rack.ui.controls.Led.class,
            org.nmox.studio.rack.ui.controls.LcdDisplay.class,
            org.nmox.studio.rack.ui.controls.VuMeter.class);

    private static boolean isControl(java.awt.Component c) {
        return CONTROLS.stream().anyMatch(type -> type.isInstance(c));
    }

    /** A control's own name, for a failure a person can act on. */
    private static String nameOf(java.awt.Component c) {
        String name = c.getAccessibleContext() == null
                ? null : c.getAccessibleContext().getAccessibleName();
        return (name == null || name.isBlank() ? c.getClass().getSimpleName() : name)
                + " " + c.getBounds();
    }

    @ParameterizedTest
    @MethodSource("catalog")
    @DisplayName("No two controls on a faceplate overlap")
    void faceplateControlsDoNotOverlap(DeviceCatalog.Entry type) {
        RackDevice device = type.create();
        java.util.List<java.awt.Component> controls = new java.util.ArrayList<>();
        for (java.awt.Component c : device.getComponents()) {
            if (isControl(c)) {
                controls.add(c);
            }
        }
        java.util.List<String> collisions = new java.util.ArrayList<>();
        for (int i = 0; i < controls.size(); i++) {
            for (int j = i + 1; j < controls.size(); j++) {
                java.awt.Rectangle a = controls.get(i).getBounds();
                java.awt.Rectangle b = controls.get(j).getBounds();
                java.awt.Rectangle over = a.intersection(b);
                // borders may TOUCH: stacked displays and stacked buttons sit
                // edge to edge, and a one- or two-pixel border kiss hides
                // nothing (LcdDisplay paints its text at h/2, a RackButton its
                // label inside its own inset). Past that, one control is
                // painting over another's label — which is what VERITAS showed
                if (a.intersects(b) && over.width > TOUCH && over.height > TOUCH) {
                    collisions.add(nameOf(controls.get(i)) + "  ×  " + nameOf(controls.get(j))
                            + "  overlap " + over);
                }
            }
        }
        // a control's bounds INCLUDE the band where it paints its own label
        // and value (a Knob is 64x78 for an 18px dial), so two controls that
        // overlap are two labels printed on top of each other — which is what
        // VERITAS showed: FAILURES sat inside the RUNNER knob's label band
        assertThat(collisions)
                .as(type + ": controls whose rectangles overlap — their labels collide on screen")
                .isEmpty();
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
