package org.nmox.studio.editor.lsp;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rustup-proxy honesty check: rustup puts a rust-analyzer PROXY on
 * PATH that exists and executes even when the component was never
 * added, then dies with "Unknown binary ... in official toolchain".
 * Without the probe, a Rust developer gets zero intelligence AND zero
 * notification — launch() succeeds, so reportMissing never fires. The
 * probe turns both "missing" and "broken proxy" into the same one-click
 * install notification (the catalog's {@code rustup component add
 * rust-analyzer} was runnable all along; it just never had a trigger).
 */
class RustAnalyzerProbeTest {

    @Test
    @DisplayName("a command that exits zero passes the probe; a missing one fails it")
    void probeDiscriminates() {
        String java = new File(new File(System.getProperty("java.home"), "bin"),
                "java").getAbsolutePath();
        assertThat(LanguageServers.RustServer.versionExitsZero(
                List.of(java, "-version"))).isTrue();
        assertThat(LanguageServers.RustServer.versionExitsZero(
                List.of("definitely-not-a-tool-9x7", "--version"))).isFalse();
    }

    @Test
    @DisplayName("a command that runs but exits NONZERO fails the probe — the proxy case")
    void brokenProxyFails() {
        String java = new File(new File(System.getProperty("java.home"), "bin"),
                "java").getAbsolutePath();
        // java with a bogus flag executes and exits nonzero — exactly the
        // shape of rustup's component-less proxy
        assertThat(LanguageServers.RustServer.versionExitsZero(
                List.of(java, "--definitely-not-a-flag"))).isFalse();
    }

    @Test
    @DisplayName("wiring gate: RustServer refuses to claim the mime until the probe passes")
    void wiringGate() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"))
                .replace("\r\n", "\n");
        int rust = src.indexOf("class RustServer");
        assertThat(rust).isPositive();
        String body = src.substring(rust, src.indexOf("class", rust + 20));
        assertThat(body)
                .as("the launch is gated on the probe, and a failed probe "
                        + "reports the missing server (one-click rustup hint)")
                .contains("if (!analyzerAnswers())")
                .contains("reported(null, \"rust-analyzer\")");
    }
}
