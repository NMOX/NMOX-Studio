package org.nmox.studio.ui.browser.fx;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Switches WebKit's own text path on in a REAL bundled runtime (v2.173.0).
 *
 * <p>Everything else about the switch is proven on stand-ins, because CI's JavaFX
 * comes from Maven jars whose WebKit is a different build. This test runs only
 * when the test JVM IS a runtime the release bundles ({@code -Dnmox.webkit.live=true}
 * with that runtime as the test JVM): the Windows job in
 * {@code windows-installer-check.yml} links one exactly as the release does, and
 * macOS runs it against the installed app's runtime. It is the one place the
 * library path, the symbol, the offsets and the foreign-function call are all
 * exercised together — the path was wrong for Windows in v2.172.0 and nothing
 * caught it.
 */
class WebKitTextPathLiveTest {

    @Test
    @DisplayName("the bundled runtime's WebKit is a known build, and its code path switches from Simple to Auto exactly once")
    void switchesTheRealLibrary() {
        assumeTrue(Boolean.getBoolean("nmox.webkit.live"), "runs against a bundled runtime only");
        Path javaHome = Path.of(System.getProperty("java.home"));
        WebKitTextPath.Build build = WebKitTextPath.knownBuild(javaHome);
        assertThat(build).as("the runtime at %s carries a known WebKit library", javaHome).isNotNull();
        assertThat(javaHome.resolve(build.library())).exists();
        assertThat(WebKitTextPath.switchOn(javaHome, build)).as("Simple -> Auto")
                .isEqualTo(WebKitTextPath.Switched.ON);
        // And only once: the state no longer reads Simple, so the setter is left alone.
        // UNCONFIRMED, not UNTOUCHED — WebKit IS on its own path now, and the caller
        // must not repair the simple path over it (v2.174.0).
        assertThat(WebKitTextPath.switchOn(javaHome, build)).as("already Auto")
                .isEqualTo(WebKitTextPath.Switched.UNCONFIRMED);
    }
}
