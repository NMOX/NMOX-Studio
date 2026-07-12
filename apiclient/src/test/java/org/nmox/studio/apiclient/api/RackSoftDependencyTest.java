package org.nmox.studio.apiclient.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveServings;
import org.nmox.studio.core.spi.ProjectAim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ledger 30: API Studio treats the rack as a SOFT dependency — it
 * compiles against core's {@link ProjectAim}/{@link LiveServings} only
 * and branches on a null lookup. These tests pin BOTH halves of that
 * contract in this module's own environment: the providers are absent
 * (this is exactly the null-branch every converted call site takes),
 * and no rack class is even loadable — the Maven dependency really is
 * gone, so a rack import can never creep back in unnoticed.
 */
class RackSoftDependencyTest {

    @Test
    @DisplayName("without the rack module, both facade lookups are null (the feature-off branch)")
    void lookupsAreNullWithoutRack() {
        assertThat(ProjectAim.find()).isNull();
        assertThat(LiveServings.find()).isNull();
    }

    @Test
    @DisplayName("rack classes are not on this module's classpath at all")
    void rackIsOffTheClasspath() {
        assertThatThrownBy(() -> Class.forName("org.nmox.studio.rack.service.RackService"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.nmox.studio.rack.service.ServingRegistry"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
