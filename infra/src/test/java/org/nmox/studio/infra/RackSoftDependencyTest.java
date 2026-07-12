package org.nmox.studio.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.ProjectAim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ledger 30: the Infra Designer treats the rack as a SOFT dependency —
 * it compiles against core's {@link ProjectAim} only and branches on a
 * null lookup. Pins both halves of that contract in this module's own
 * environment: the provider is absent (the null-branch every converted
 * call site takes), and no rack class is loadable — the Maven
 * dependency really is gone.
 */
class RackSoftDependencyTest {

    @Test
    @DisplayName("without the rack module, the facade lookup is null (the feature-off branch)")
    void lookupIsNullWithoutRack() {
        assertThat(ProjectAim.find()).isNull();
    }

    @Test
    @DisplayName("rack classes are not on this module's classpath at all")
    void rackIsOffTheClasspath() {
        assertThatThrownBy(() -> Class.forName("org.nmox.studio.rack.service.RackService"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
