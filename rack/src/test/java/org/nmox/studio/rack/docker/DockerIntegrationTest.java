package org.nmox.studio.rack.docker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Against a real daemon when one is reachable (CI's ubuntu runners
 * have one); skips cleanly everywhere else. Read-only: lists and df,
 * no mutations.
 */
class DockerIntegrationTest {

    @BeforeAll
    static void requireDaemon() throws Exception {
        String v = DockerClient.getDefault().engineVersion().get();
        assumeTrue(v != null, "docker daemon not reachable; integration skipped");
    }

    @Test
    @DisplayName("Real listings parse end to end")
    void realListingsParse() throws Exception {
        // empty lists are fine; what must hold is: no exception, no nulls
        assertThat(DockerClient.getDefault().containers().get()).isNotNull();
        assertThat(DockerClient.getDefault().images().get()).isNotNull();
        assertThat(DockerClient.getDefault().volumes().get()).isNotNull();
        assertThat(DockerClient.getDefault().networks().get()).isNotNull();
        assertThat(DockerClient.getDefault().systemDf().get()).isNotEmpty();
    }
}
