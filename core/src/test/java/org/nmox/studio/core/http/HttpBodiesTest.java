package org.nmox.studio.core.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 56 closed: the one capped HTTP-body read every module routes
 * through. Mechanics proven here; truncation POLICY (flag/refuse/shrug)
 * stays at the call sites, and the gate below keeps them from quietly
 * re-inlining the mechanics.
 */
class HttpBodiesTest {

    @Test
    @DisplayName("A body under the cap comes back whole and unflagged")
    void underCap() throws IOException {
        HttpBodies.Capped c = HttpBodies.readUtf8(stream("hello"), 100);
        assertThat(c.text()).isEqualTo("hello");
        assertThat(c.byteLength()).isEqualTo(5);
        assertThat(c.truncated()).isFalse();
    }

    @Test
    @DisplayName("A body exactly at the cap is whole and unflagged — the cap bit means MORE existed")
    void exactlyAtCap() throws IOException {
        HttpBodies.Capped c = HttpBodies.readUtf8(stream("12345"), 5);
        assertThat(c.text()).isEqualTo("12345");
        assertThat(c.truncated())
                .as("exactly-cap is not truncation; only a real extra byte is")
                .isFalse();
    }

    @Test
    @DisplayName("A body past the cap is held at the cap and flagged; the tail is never read")
    void overCap() throws IOException {
        CountingStream in = new CountingStream("1234567890".getBytes(StandardCharsets.UTF_8));
        HttpBodies.Capped c = HttpBodies.readUtf8(in, 4);
        assertThat(c.text()).isEqualTo("1234");
        assertThat(c.byteLength()).isEqualTo(4);
        assertThat(c.truncated()).isTrue();
        assertThat(in.readCount)
                .as("cap + one probe byte, never the whole stream — a gigabyte "
                        + "body must cost the cap, not the gigabyte")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("The charset parameter decodes non-UTF-8 bodies")
    void charsetHonored() throws IOException {
        byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);
        HttpBodies.Capped c = HttpBodies.read(
                new ByteArrayInputStream(latin1), 100, StandardCharsets.ISO_8859_1);
        assertThat(c.text()).isEqualTo("café");
    }

    @Test
    @DisplayName("Gate: all seven former ofString sites route through HttpBodies, none re-inlines readNBytes")
    void sitesRouteThroughHelper() throws Exception {
        // the seven sites the v1.99.0–v1.104.0 arc capped one by one,
        // unified here; LegacyWeb's readNBytes is a LOCAL file scan, not
        // HTTP, and deliberately stays inline
        List<String> sites = List.of(
                "../apiclient/src/main/java/org/nmox/studio/apiclient/api/ApiClient.java",
                "../web3/src/main/java/org/nmox/studio/web3/engine/JsonRpcClient.java",
                "../dbstudio/src/main/java/org/nmox/studio/dbstudio/engine/CouchBackend.java",
                "../rack/src/main/java/org/nmox/studio/rack/engine/OracleClient.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/HttpDevice.java",
                "../infra/src/main/java/org/nmox/studio/infra/api/DigitalOceanClient.java",
                "../ui/src/main/java/org/nmox/studio/ui/UpdateCheck.java");
        for (String site : sites) {
            String src = Files.readString(Path.of(site)).replace("\r\n", "\n");
            assertThat(src).as("%s routes through the core helper", site)
                    .contains("HttpBodies");
            assertThat(src).as("%s must not re-inline the capped-read mechanics", site)
                    .doesNotContain("readNBytes")
                    .doesNotContain("BodyHandlers.ofString");
        }
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Counts bytes handed out so the never-drains-the-tail law is provable. */
    private static final class CountingStream extends ByteArrayInputStream {
        int readCount;

        CountingStream(byte[] buf) {
            super(buf);
        }

        @Override
        public synchronized int read() {
            int b = super.read();
            if (b != -1) {
                readCount++;
            }
            return b;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int n = super.read(b, off, len);
            if (n > 0) {
                readCount += n;
            }
            return n;
        }
    }
}
