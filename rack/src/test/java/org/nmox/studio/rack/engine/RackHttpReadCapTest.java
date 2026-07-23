package org.nmox.studio.rack.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.104.0: the rack's two HTTP readers must bound the response body.
 * {@code BodyHandlers.ofString()} buffers the whole body into heap, so a
 * user-pointed endpoint (HttpDevice's console) or a remote API
 * (OracleClient → Anthropic) streaming gigabytes could OOM the IDE — the
 * bug already capped in apiclient/web3/dbstudio. Source-gated because
 * neither reader has an injectable URL seam.
 */
class RackHttpReadCapTest {

    @Test
    @DisplayName("OracleClient reads the Messages response through a bounded stream, not ofString")
    void oracleClientBoundsTheRead() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/engine/OracleClient.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .contains("BodyHandlers.ofInputStream()")
                .contains("HttpBodies")   // the v1.124.0 core helper owns the capped mechanics
                .doesNotContain("BodyHandlers.ofString()");
    }

    @Test
    @DisplayName("HttpDevice reads the console response through a bounded stream, not ofString")
    void httpDeviceBoundsTheRead() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/devices/HttpDevice.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .contains("BodyHandlers.ofInputStream()")
                .contains("HttpBodies")   // the v1.124.0 core helper owns the capped mechanics
                .doesNotContain("BodyHandlers.ofString()");
    }
}
