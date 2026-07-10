package org.nmox.studio.web3.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.38.0 startup audit found this tab's constructor kicking off an
 * ArtifactScanner tree walk (Files.walk over out/ + artifacts/) during
 * window-system load, while the tab sits hidden behind the selected one.
 * Off-EDT, so nothing froze — but a boot-time walk for a tree nobody can
 * see is the same shape as the v1.35.1 Docker-offer finding, and gets the
 * same fix: hidden tabs take a note, componentShowing serves it.
 */
class Web3BootGateTest {

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("a hidden tab never walks the artifact tree — rescan defers to componentShowing")
    void shouldDeferArtifactWalkToShowing() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java"),
                StandardCharsets.UTF_8);

        String rescan = method(source, "private void rescan()");
        assertThat(rescan.indexOf("isShowing()"))
                .as("rescan checks visibility before it walks")
                .isGreaterThan(0)
                .isLessThan(rescan.indexOf("RP.post"));
        assertThat(rescan).contains("rescanPending = true");

        String auto = method(source, "private void autoRescan()");
        assertThat(auto.indexOf("isShowing()"))
                .as("a build storm behind a hidden tab must coalesce to one deferred walk")
                .isGreaterThan(0)
                .isLessThan(auto.indexOf("RP.post"));

        assertThat(method(source, "protected void componentShowing()"))
                .as("first show serves the deferred walk")
                .contains("rescanPending")
                .contains("rescan()");
    }
}
