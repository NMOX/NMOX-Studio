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

    @TempDir
    java.io.File projectB;

    @Test
    @DisplayName("Passive aims may follow each other but never override an explicit aim")
    void passiveNeverOverridesExplicit() {
        RackService service = new RackService();

        // passive then passive: later passive wins (cold-start restore order)
        service.openProjectPassively(projectA);
        assertThat(service.isAimed()).as("passive aim claims no intent").isFalse();
        service.openProjectPassively(projectB);
        assertThat(service.getRack().getProjectDir()).isEqualTo(projectB);

        // explicit beats everything after it
        service.openProject(projectA);
        service.openProjectPassively(projectB);
        assertThat(service.getRack().getProjectDir())
                .as("passive must not clobber explicit").isEqualTo(projectA);
        service.getRack().shutdown();
    }
}
