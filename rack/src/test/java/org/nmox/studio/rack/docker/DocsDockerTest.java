package org.nmox.studio.rack.docker;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.DocsFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Docker Panel scene writes nothing: its container belongs to the forge
 * script and reaches the app only through the read-only docs view.
 */
class DocsDockerTest {

    @Test
    @DisplayName("staging writes nothing and names the demo shop")
    void stagingWritesNothing(@TempDir Path home) throws Exception {
        assertThat(new DocsDocker().stage(home.toFile(), "{}", "pl"))
                .isEqualTo(DocsFixtures.projectDir(home.toFile()));
        try (var entries = Files.list(home)) {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    @DisplayName("with no Docker Panel open the scene is quiet and never ready")
    void quietWithoutAWindow() {
        DocsDocker scene = new DocsDocker();
        scene.arrange();
        assertThat(scene.ready()).isFalse();
        assertThat(scene.id()).isEqualTo("docker-panel");
    }
}
