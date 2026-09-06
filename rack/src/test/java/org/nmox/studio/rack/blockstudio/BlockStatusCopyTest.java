package org.nmox.studio.rack.blockstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The status line's plural (v2.85.0): "1 piece", never "1 pieces"; both status sites ride the helper. */
class BlockStatusCopyTest {

    @Test
    @DisplayName("one piece is singular; every piece count in a status rides pieces()")
    void plural() throws Exception {
        assertThat(BlockStudioTopComponent.pieces(1)).isEqualTo("1 piece");
        assertThat(BlockStudioTopComponent.pieces(0)).isEqualTo("0 pieces");
        assertThat(BlockStudioTopComponent.pieces(7)).isEqualTo("7 pieces");
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/blockstudio/BlockStudioTopComponent.java"));
        assertThat(src).doesNotContain("\" pieces → \"").doesNotContain("+ \" pieces\"");
        assertThat(src.split("pieces\\(").length - 1).as("two status sites + the helper").isGreaterThanOrEqualTo(3);
    }
}
