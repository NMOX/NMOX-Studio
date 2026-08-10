package org.nmox.studio.rack.devices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The banner-port read behind the probed-port serving lanes
 * (v1.320.0). Once the STATIC and PHP lanes may bind a port other than
 * their preferred one, announcing a constant would put the ⇄ chip on a
 * port nothing listens on — so the announce reads the port the
 * server's OWN banner names. Verbatim banners, not paraphrases.
 */
class BannerPortTest {

    @Test
    @DisplayName("python's banner names its port — both real spellings")
    void pythonBanner() {
        // python 3.10 on macOS, dual-stack — captured during the walk
        assertThat(ServeUrls.bannerPort(
                "Serving HTTP on :: port 8001 (http://[::]:8001/) ...", 8000))
                .isEqualTo(8001);
        // older/ipv4 spelling
        assertThat(ServeUrls.bannerPort(
                "Serving HTTP on 0.0.0.0 port 8003 ...", 8000))
                .isEqualTo(8003);
    }

    @Test
    @DisplayName("php's banner names its address")
    void phpBanner() {
        assertThat(ServeUrls.bannerPort(
                "PHP 8.4.1 Development Server (http://127.0.0.1:8002) started",
                8000))
                .isEqualTo(8002);
    }

    @Test
    @DisplayName("a banner naming no port falls back honestly")
    void fallback() {
        assertThat(ServeUrls.bannerPort("Development Server started", 8000))
                .isEqualTo(8000);
    }

    @Test
    @DisplayName("probe and banner-read are WIRED, not just defined")
    void probeAndBannerReadAreWired() throws Exception {
        // The v1.318.0 lesson, applied on day one: a helper with green
        // tests and no call site is a payload without a gate. The probed
        // port and the banner-read announce are two halves of ONE truth —
        // un-wiring either one reintroduces a lie (a dead port in the
        // command, or a wrong port on the ⇄ chip).
        String runDevice = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/rack/devices/RunDevice.java"));
        assertThat(runDevice)
                .contains("FreePorts")
                .contains("bannerPort(");
        String tools = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../tools/src/main/java/org/nmox/studio/tools/npm/WebProjectCommands.java"));
        assertThat(tools).contains("FreePorts");
        String toolsAnnounce = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../tools/src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"));
        assertThat(toolsAnnounce)
                .as("the tools Run lane announces the banner's port too — the"
                        + " probe without this half registers a serving nothing"
                        + " listens on")
                .contains("bannerPort(");
    }
}
