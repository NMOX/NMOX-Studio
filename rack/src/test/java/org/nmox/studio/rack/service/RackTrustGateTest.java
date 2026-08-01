package org.nmox.studio.rack.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.TrustGate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trust facade's wiring (v1.224.0/v1.225.0): with the rack on the
 * classpath, {@link TrustGate#find()} must resolve to the rack's
 * delegating adapter — this is what lets web3's forge buttons gate
 * without a rack dependency. If this lookup breaks, those gates
 * silently vanish (the null branch proceeds), so the wiring is pinned.
 */
class RackTrustGateTest {

    @Test
    @DisplayName("TrustGate.find() resolves to the rack's adapter when the rack is present")
    void facadeResolvesToRackAdapter() {
        TrustGate gate = TrustGate.find();
        assertThat(gate)
                .as("the @ServiceProvider registration must reach Lookup — "
                        + "web3's forge gates depend on it")
                .isNotNull()
                .isInstanceOf(RackTrustGate.class);
    }
}
