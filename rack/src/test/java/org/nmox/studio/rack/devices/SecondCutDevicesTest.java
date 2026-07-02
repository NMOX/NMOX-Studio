package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HELM, BEACON, PRISM: the second-cut devices honor their faceplates -
 * ssh is BatchMode-only argv, the sentinel's verdict gates on runway,
 * and the bundle gate weighs real bytes against a real budget.
 */
class SecondCutDevicesTest {

    @Test
    @DisplayName("HELM builds a BatchMode ssh argv; empty host or command builds nothing")
    void helmBuildsSsh() {
        SshDevice helm = new SshDevice();
        assertThat(helm.buildCommand()).isNull();
        helm.applyState(Map.of("host", "deploy@203.0.113.7",
                "command", "systemctl restart app"));
        assertThat(helm.buildCommand()).containsExactly("ssh",
                "-o", "BatchMode=yes", "-o", "ConnectTimeout=10",
                "deploy@203.0.113.7", "systemctl", "restart", "app");
    }

    @Test
    @DisplayName("BEACON verdict: down always fails; floor gates only measured runway")
    void beaconVerdict() {
        assertThat(BeaconDevice.verdict(false, 90, 0)).isFalse();
        assertThat(BeaconDevice.verdict(true, 90, 30)).isTrue();
        assertThat(BeaconDevice.verdict(true, 12, 30)).isFalse();
        assertThat(BeaconDevice.verdict(true, -1, 30))
                .as("plain http has no cert: floor doesn't apply").isTrue();
        assertThat(BeaconDevice.verdict(true, 5, 0))
                .as("floor off: reachability decides").isTrue();
    }

    @Test
    @DisplayName("PRISM weighs a tree and formats budgets honestly")
    void prismWeighs(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("assets"));
        Files.write(dir.resolve("app.js"), new byte[120_000]);
        Files.write(dir.resolve("assets/style.css"), new byte[30_000]);
        assertThat(BundleSizeDevice.totalBytes(dir.toFile())).isEqualTo(150_000);
        assertThat(BundleSizeDevice.totalBytes(dir.resolve("missing").toFile())).isEqualTo(-1);
        assertThat(BundleSizeDevice.human(150_000)).isEqualTo("150 KB");
        assertThat(BundleSizeDevice.human(1_500_000)).isEqualTo("1.5 MB");

        BundleSizeDevice prism = new BundleSizeDevice();
        prism.applyState(Map.of("max", "1")); // 250 KB
        assertThat(prism.maximumBytes()).isEqualTo(250_000);
    }
}
