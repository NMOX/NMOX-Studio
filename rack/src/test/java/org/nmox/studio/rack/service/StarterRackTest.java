package org.nmox.studio.rack.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first thing a new user sees: one MONITOR, tapped onto the stderr
 * bus, so errors are visible before a single cable is patched.
 */
class StarterRackTest {

    @Test
    @DisplayName("Starter rack is a single MONITOR with its TAP on stderr")
    void starterRackIsOneStderrMonitor() {
        Rack rack = new RackService().getRack();
        try {
            assertThat(rack.getDevices()).hasSize(1);
            RackDevice monitor = rack.getDevices().get(0);
            assertThat(monitor.getTypeId()).isEqualTo("console");
            // TAP options are {off, stderr, all}; index 1 = stderr
            assertThat(monitor.getState()).containsEntry("tap", "1");
        } finally {
            rack.shutdown();
        }
    }
}
