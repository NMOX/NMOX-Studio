package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled website ships (v2.40.0): every file of the site source
 * exists byte-identical in the ASSEMBLED cluster, where
 * {@code InstalledFileLocator} will look for it at runtime. Reads
 * {@code target/nmoxstudio}, which exists only after {@code package} —
 * so this test rides the {@code packaged-app-gates} surefire execution
 * at integration-test phase (the PackagedConfGateTest seam), never the
 * plain test phase, where a clean build has no cluster yet (the
 * failure PR 605's first CI run proved on all three lanes).
 */
class PackagedSiteGateTest {

    private static final Path SITE =
            Path.of("../ui/src/main/release/website");

    @Test
    @DisplayName("the assembled cluster carries the whole site, byte-identical")
    void siteShips() throws Exception {
        Path cluster = Path.of("target/nmoxstudio/nmoxstudio/website");
        assertThat(cluster.resolve("index.html"))
                .as("nbmResources places the site in the cluster")
                .exists();
        try (var walk = Files.walk(SITE)) {
            for (Path src : walk.filter(Files::isRegularFile).toList()) {
                Path shipped = cluster.resolve(SITE.relativize(src));
                assertThat(shipped).as("shipped: %s", shipped).exists();
                assertThat(Files.mismatch(src, shipped))
                        .as("%s byte-identical", src.getFileName()).isEqualTo(-1L);
            }
        }
    }
}
