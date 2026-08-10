package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The probed-port fix stays WIRED, not just defined (v1.321.0, the
 * night-arc review's find over v1.320.0).
 *
 * <p>{@code FreePortsTest} proves the core seam diverges when the port
 * is busy, and {@code BannerPortTest} proves the announce parse — but
 * nothing referenced {@code firstFreeFrom} at the SPAWN sites, so a
 * mutant reverting a lane to its pinned constant passed the whole
 * suite whenever 8000 happened to be free, which is most machines most
 * of the time. The v1.318.0 lesson in mirror image: there, a predicate
 * with green tests and no call site; here, call sites with green seam
 * tests and no gate. Both halves of the v1.320.0 design are pinned —
 * the PROBE at the spawn, and the BANNER-truth read at the announce —
 * because either alone re-opens a bug: probe without banner-truth
 * announces a dead port (the v1.93.0 serving-truth class), banner-truth
 * without the probe dies on the busy port the probe exists to dodge.
 */
class ProbedPortWiringGateTest {

    @Test
    @DisplayName("RunDevice probes both fixed-port lanes and announces from the banner")
    void runDeviceLanesAreProbedAndBannerTrue() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/devices/RunDevice.java"));
        // the STATIC (python) lane and the PHP lane each probe
        assertThat(src.split("FreePorts\\s*\\n?\\s*\\.firstFreeFrom\\(", -1).length - 1)
                .as("both fixed-port lanes (static python, php -S) probe for a"
                        + " free port instead of pinning one")
                .isGreaterThanOrEqualTo(2);
        // and each announce reads the port the server's own banner names
        assertThat(src.split("ServeUrls\\.bannerPort\\(", -1).length - 1)
                .as("both announce sites read the banner's port — announcing"
                        + " the old constant while the server bound the next"
                        + " port up registers a serving nothing listens on")
                .isGreaterThanOrEqualTo(2);
    }
}
