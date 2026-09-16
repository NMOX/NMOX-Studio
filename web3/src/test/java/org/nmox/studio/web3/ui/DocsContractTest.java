package org.nmox.studio.web3.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Foundry project Contract Studio's picture shows. It must stand on its
 * own — no library imports, so {@code forge build} compiles offline — and a
 * restage over an already-built project must not build again.
 */
class DocsContractTest {

    @Test
    @DisplayName("the escrow project is a self-contained Foundry project beside the demo shop")
    void stagesASelfContainedProject(@TempDir Path home) throws Exception {
        // an existing out/ means built: the stage must not spawn forge again
        Files.createDirectories(home.resolve(DocsContract.PROJECT).resolve("out"));
        File dir = new DocsContract().stage(home.toFile(), "{}", "de");
        assertThat(dir.toPath()).isEqualTo(home.resolve(DocsContract.PROJECT));
        assertThat(Files.readString(dir.toPath().resolve("foundry.toml"))).contains("offline = true");
        String escrow = Files.readString(dir.toPath().resolve("src/StorefrontEscrow.sol"));
        assertThat(escrow).contains("contract StorefrontEscrow");
        for (String source : new String[]{escrow, Files.readString(dir.toPath().resolve("src/IRefundPolicy.sol"))}) {
            assertThat(source.lines().filter(l -> l.startsWith("import")))
                    .allMatch(l -> l.contains("\"./"), "imports only its own sources, never a library");
        }
        assertThat(Files.list(dir.toPath().resolve("out"))).isEmpty();
    }

    @Test
    @DisplayName("with no Contract Studio window the scene is quiet and never ready")
    void quietWithoutAWindow() {
        DocsContract scene = new DocsContract();
        scene.arrange();
        assertThat(scene.ready()).isFalse();
        assertThat(scene.id()).isEqualTo("contract-studio");
    }
}
