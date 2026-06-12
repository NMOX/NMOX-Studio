package org.nmox.studio.rack.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aim-priority rule: an explicit openProject always outranks
 * passive followers. This is the regression test for the wizard's
 * fresh aim being clobbered by the rack window's persisted state.
 */
class AimPriorityTest {

    @TempDir
    java.io.File projectA;

    @Test
    @DisplayName("isAimed flips on the first explicit aim and stays")
    void explicitAimIsSticky() {
        RackService service = new RackService();
        assertThat(service.isAimed()).as("fresh service is unaimed").isFalse();

        service.openProject(projectA);

        assertThat(service.isAimed()).isTrue();
        assertThat(service.getRack().getProjectDir()).isEqualTo(projectA);
        service.getRack().shutdown();
    }
}
