package org.nmox.studio.core.http;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The localhost dual-stack law (v1.259.0): the WebView loader dials only
 * the FIRST address localhost resolves to, and Angular 21's `ng serve`
 * binds only [::1] — measured live on the shipped 1.258.0 runtime as
 * "Connection refused by server" for the exact URL the CLI prints.
 * These tests pin resolve()'s three verdicts (as-typed works / only the
 * other stack works / nothing works) in both resolution orders, through
 * the prober seam so no test opens a socket.
 */
class LoopbackUrlsTest {

    private final List<String> probed = new ArrayList<>();

    private void arm(boolean v4First, boolean v4Answers, boolean v6Answers) {
        LoopbackUrls.firstResolvedIsV4 = () -> v4First;
        LoopbackUrls.prober = (host, port) -> {
            probed.add(host + ":" + port);
            return "127.0.0.1".equals(host) ? v4Answers : v6Answers;
        };
    }

    @AfterEach
    void restoreSeams() {
        LoopbackUrls.prober = (host, port) -> {
            throw new AssertionError("test forgot to arm the prober");
        };
        LoopbackUrls.firstResolvedIsV4 = () -> true;
    }

    @Test
    @DisplayName("non-localhost URLs are never probed and never touched")
    void nonLocalhostUntouched() {
        arm(true, false, false);
        assertThat(LoopbackUrls.needsProbe("https://example.com/")).isFalse();
        assertThat(LoopbackUrls.needsProbe("http://127.0.0.1:3000/")).isFalse();
        assertThat(LoopbackUrls.needsProbe("file:///tmp/x.html")).isFalse();
        assertThat(LoopbackUrls.needsProbe("not a url")).isFalse();
        assertThat(LoopbackUrls.resolve("https://example.com/"))
                .isEqualTo("https://example.com/");
        assertThat(probed).as("no socket ever opened for a foreign host").isEmpty();
    }

    @Test
    @DisplayName("when the first-resolved stack answers, the URL stays as typed")
    void asTypedWinsWhenFirstStackAnswers() {
        arm(true, true, false);
        assertThat(LoopbackUrls.resolve("http://localhost:4321/"))
                .isEqualTo("http://localhost:4321/");
        assertThat(probed).as("second stack never probed when the first works")
                .containsExactly("127.0.0.1:4321");
    }

    @Test
    @DisplayName("v4-first, only [::1] listening -> host rewritten to [::1] (the ng serve case)")
    void rewritesToV6WhenOnlyV6Listens() {
        arm(true, false, true);
        assertThat(LoopbackUrls.resolve("http://localhost:4321/path?q=1#f"))
                .isEqualTo("http://[::1]:4321/path?q=1#f");
        assertThat(probed).containsExactly("127.0.0.1:4321", "::1:4321");
    }

    @Test
    @DisplayName("v6-first, only 127.0.0.1 listening -> the mirror rewrite")
    void rewritesToV4WhenOnlyV4Listens() {
        arm(false, true, false);
        assertThat(LoopbackUrls.resolve("http://localhost:3000/app"))
                .isEqualTo("http://127.0.0.1:3000/app");
        assertThat(probed).containsExactly("::1:3000", "127.0.0.1:3000");
    }

    @Test
    @DisplayName("neither stack listening -> unchanged, so the error names what was typed")
    void honestFailureStaysAsTyped() {
        arm(true, false, false);
        assertThat(LoopbackUrls.resolve("http://localhost:9999/"))
                .isEqualTo("http://localhost:9999/");
    }

    @Test
    @DisplayName("portless URLs probe the scheme default (80 / 443)")
    void schemeDefaultPorts() {
        arm(true, true, false);
        LoopbackUrls.resolve("http://localhost/");
        LoopbackUrls.resolve("https://localhost/");
        assertThat(probed).containsExactly("127.0.0.1:80", "127.0.0.1:443");
    }


    @Test
    @DisplayName("Gate: every localhost-capable HTTP seam routes through LoopbackUrls.resolve")
    void sitesRouteThroughResolver() throws Exception {
        // The six seams a localhost URL can actually reach: the Browser
        // (the v1.259.0 original), API Studio sends (the {{baseUrl}}
        // offer announces "http://localhost:<port>" verbatim), the rack
        // HTTP console and BEACON (user-pointed URLs), CouchDB (the
        // stock install is localhost:5984) and JSON-RPC (localhost
        // devnets). Deliberately OUT, endpoints fixed remote by
        // construction: OracleClient (api.anthropic.com), UpdateCheck
        // (github.com), DigitalOceanClient (api.digitalocean.com).
        java.util.List<String> sites = java.util.List.of(
                "../ui/src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java",
                "../apiclient/src/main/java/org/nmox/studio/apiclient/api/ApiClient.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/HttpDevice.java",
                "../rack/src/main/java/org/nmox/studio/rack/devices/BeaconDevice.java",
                "../dbstudio/src/main/java/org/nmox/studio/dbstudio/engine/CouchBackend.java",
                "../web3/src/main/java/org/nmox/studio/web3/engine/JsonRpcClient.java");
        for (String site : sites) {
            String src = java.nio.file.Files.readString(java.nio.file.Path.of(site));
            assertThat(src).as("%s routes through LoopbackUrls", site)
                    .contains("LoopbackUrls.");
        }
    }
}
