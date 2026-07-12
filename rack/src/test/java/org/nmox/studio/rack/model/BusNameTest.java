package org.nmox.studio.rack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DeviceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Output tab, the monitor bus, and the flight recorder all key a
 * run on the name the device's exec() publishes under. When that name
 * was the bare title, two SOLDERs in one rack merged their launch/exit
 * pairings and duration stats into one phantom device — BLACKBOX's
 * slow-creep alarm and ORACLE's failure context read a fiction. These
 * tests fail on the title-keyed code.
 */
class BusNameTest {

    /** Same-package accessor for the assigned identity. */
    private static String busNameOf(RackDevice d) {
        return d.busName();
    }

    @Test
    @DisplayName("two same-title devices in one rack get distinct bus names; the first keeps the bare title")
    void sameTitleDevicesGetDistinctNames() {
        Rack rack = new Rack();
        RackDevice first = DeviceType.CMD.create();
        RackDevice second = DeviceType.CMD.create();
        RackDevice third = DeviceType.CMD.create();
        rack.addDevice(first);
        rack.addDevice(second);
        rack.addDevice(third);

        // the first instance keeps the plain title so existing journals
        // and stats stay continuous for the common single-instance case
        assertThat(busNameOf(first)).isEqualTo("SOLDER");
        assertThat(busNameOf(second)).isEqualTo("SOLDER ·2");
        assertThat(busNameOf(third)).isEqualTo("SOLDER ·3");
    }

    @Test
    @DisplayName("the bus name is assigned once: undo re-attach never renames a lane")
    void reattachKeepsTheName() {
        Rack rack = new Rack();
        RackDevice first = DeviceType.CMD.create();
        RackDevice second = DeviceType.CMD.create();
        rack.addDevice(first);
        rack.addDevice(second);
        assertThat(busNameOf(second)).isEqualTo("SOLDER ·2");

        rack.removeDevice(second);
        rack.addDevice(second);   // undo of a remove re-attaches the instance

        assertThat(busNameOf(second))
                .as("stats keyed on this name must stay continuous across undo")
                .isEqualTo("SOLDER ·2");
    }

    @Test
    @DisplayName("an unattached device answers its title — exec before attach cannot NPE")
    void unattachedFallsBackToTitle() {
        RackDevice d = DeviceType.CMD.create();
        assertThat(busNameOf(d)).isEqualTo("SOLDER");
    }
}
