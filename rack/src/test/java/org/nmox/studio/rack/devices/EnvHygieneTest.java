package org.nmox.studio.rack.devices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ATMOS hygiene: the atmosphere a device shapes must not outlive it,
 * and free-form extras (where secrets get typed) must never persist
 * into the committable patch file.
 */
class EnvHygieneTest {

    @Test
    @DisplayName("Unracking ATMOS retracts NODE_ENV and CI from the shared env")
    void disposeRetractsAppliedEnv() {
        Rack rack = new Rack();
        EnvDevice atmos = new EnvDevice();
        rack.addDevice(atmos);
        assertThat(rack.getEnvOverrides()).containsKey("NODE_ENV");

        rack.removeDevice(atmos); // dispose() path — same as a patch swap

        assertThat(rack.getEnvOverrides()).doesNotContainKeys("NODE_ENV", "CI");
        rack.shutdown();
    }

    @Test
    @DisplayName("The EXTRA line is session-only: never in persisted state")
    void extrasAreNotPersisted() {
        EnvDevice atmos = new EnvDevice();
        assertThat(atmos.getState()).containsKeys("nodeEnv", "ci")
                .doesNotContainKey("extra");
    }

    @Test
    @DisplayName("Legacy patches with a stored extra value load without applying it")
    void legacyExtraIsIgnoredGracefully() {
        Rack rack = new Rack();
        EnvDevice atmos = new EnvDevice();
        rack.addDevice(atmos);

        atmos.applyState(java.util.Map.of("nodeEnv", "2", "extra", "SECRET=leaked"));

        assertThat(rack.getEnvOverrides())
                .containsEntry("NODE_ENV", "production")
                .doesNotContainKey("SECRET");
        rack.shutdown();
    }
}
