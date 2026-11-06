package org.nmox.studio.infra.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1.104.0: the DigitalOcean API client must bound its response read.
 * {@code ofString()} buffered the whole body, so a hostile or
 * misconfigured endpoint behind a secret token could OOM the IDE — the
 * bug already capped in apiclient/web3/dbstudio. Source-gated: the
 * client uses the shared HttpClient with no injectable URL seam.
 */
class DigitalOceanReadCapTest {

    @Test
    @DisplayName("The API response is read through a bounded stream, not ofString")
    void boundsTheRead() throws Exception {
        // 3.11.0: the read lives in CloudHttp, the one bearer round trip the
        // DigitalOcean client and the Ops Hub's providers share; the client
        // itself must send through it and hold no read of its own
        String http = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/infra/api/CloudHttp.java"), StandardCharsets.UTF_8);
        assertThat(http)
                .contains("BodyHandlers.ofInputStream()")
                .contains("HttpBodies")   // the v1.124.0 core helper owns the capped mechanics
                .doesNotContain("BodyHandlers.ofString()");
        String client = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/infra/api/DigitalOceanClient.java"), StandardCharsets.UTF_8);
        assertThat(client)
                .contains("CloudHttp.over(provider).send(")
                .doesNotContain("BodyHandlers.");
    }
}
