package org.nmox.studio.rack.model;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A racked device is heard as itself (3.4). The census found every
 * faceplate reporting {@code role=panel name=null}: SOLDER, VERITAS and
 * MONITOR each exposed a STOP button and an OK light, and a screen reader
 * could not say whose. And the rear of the rack — the wiring — existed
 * only as paint. The device's accessible context now carries its bus name
 * and, in its description, its cables in words, updated as they change.
 */
class DeviceAccessibilityTest {

    private Rack rack;

    @BeforeEach
    void setUp() {
        rack = new Rack();
    }

    @AfterEach
    void tearDown() {
        rack.shutdown();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("a device's accessible name is its bus name; a second of the same kind is told apart")
    void nameIsTheBusName() {
        RackDevice first = DeviceType.CMD.create();
        RackDevice second = DeviceType.CMD.create();
        rack.addDevice(first);
        rack.addDevice(second);

        assertThat(first.getAccessibleContext().getAccessibleName()).isEqualTo("SOLDER");
        assertThat(second.getAccessibleContext().getAccessibleName())
                .as("two SOLDERs must not sound alike: their STOPs are different buttons")
                .isEqualTo("SOLDER ·2");
    }

    @Test
    @DisplayName("a control on the faceplate is heard as belonging to its device")
    void controlsHaveTheDeviceAsParent() {
        RackDevice solder = DeviceType.CMD.create();
        rack.addDevice(solder);
        int seen = 0;
        for (java.awt.Component c : solder.getComponents()) {
            if (c instanceof javax.accessibility.Accessible a) {
                assertThat(a.getAccessibleContext().getAccessibleParent())
                        .as(c.getClass().getSimpleName() + " belongs to SOLDER")
                        .isSameAs(solder);
                seen++;
            }
        }
        assertThat(seen).as("SOLDER has controls").isPositive();
    }

    @Test
    @DisplayName("the description names the device's role, then its cables in words from its own side")
    void descriptionSpeaksTheCables() {
        RackDevice run = DeviceType.RUN.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(run);
        rack.addDevice(console);
        String unwired = run.getAccessibleContext().getAccessibleDescription();
        assertThat(unwired).isNotBlank().doesNotContain("→");

        Port out = run.getPort("out");
        Port in = console.getPort("in");
        assertThat(rack.connect(out, in)).isNotNull();

        assertThat(run.getAccessibleContext().getAccessibleDescription())
                .as("the output side reads OUT → MONITOR IN")
                .startsWith(unwired)
                .contains(out.getLabel() + " → " + console.getBusName() + " " + in.getLabel());
        assertThat(console.getAccessibleContext().getAccessibleDescription())
                .as("the input side reads IN ← <source> OUT")
                .contains(in.getLabel() + " ← " + run.getBusName() + " " + out.getLabel());
    }

    @Test
    @DisplayName("two cables are listed apart, joined in words")
    void twoCablesAreBothListed() {
        RackDevice run = DeviceType.RUN.create();
        RackDevice first = DeviceType.CONSOLE.create();
        RackDevice second = DeviceType.CONSOLE.create();
        rack.addDevice(run);
        rack.addDevice(first);
        rack.addDevice(second);
        rack.connect(run.getPort("out"), first.getPort("in"));
        rack.connect(run.getPort("out"), second.getPort("in"));

        String words = run.cablesInWords();
        assertThat(words).contains(first.getBusName()).contains(second.getBusName()).contains("; ");
    }

    @Test
    @DisplayName("a patch or an unplug tells a listening screen reader the description changed")
    void cablesChangeIsAnnounced() throws Exception {
        RackDevice run = DeviceType.RUN.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(run);
        rack.addDevice(console);
        List<PropertyChangeEvent> heard = new ArrayList<>();
        run.getAccessibleContext().addPropertyChangeListener(e -> {
            if (AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY.equals(e.getPropertyName())) {
                heard.add(e);
            }
        });

        Cable c = rack.connect(run.getPort("out"), console.getPort("in"));
        flushEdt();
        assertThat(heard).as("the patch is announced").hasSize(1);
        assertThat(String.valueOf(heard.get(0).getNewValue())).contains("→");

        rack.disconnect(c);
        flushEdt();
        assertThat(heard).as("the unplug is announced").hasSize(2);
        assertThat(String.valueOf(heard.get(1).getNewValue())).doesNotContain("→");
    }

    @Test
    @DisplayName("an explicit accessible name still wins over the bus name")
    void explicitNameWins() {
        RackDevice solder = DeviceType.CMD.create();
        solder.getAccessibleContext().setAccessibleName("BUILD LANE");
        assertThat(solder.getAccessibleContext().getAccessibleName()).isEqualTo("BUILD LANE");
    }
}
