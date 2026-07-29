package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled-runtime laws for the embedded browser (v1.199.0): every
 * jlink site — the shared bundle-jre.sh (macOS DMG, linux tar.gz/deb)
 * and the Windows lane's inline step — must carry OpenJFX jmods at a
 * PINNED version with a sha256 the build verifies before unpacking,
 * and must assert javafx.web made it into the image. A runtime that
 * silently lost JavaFX would ship a browser window that can never
 * light up; a hash mismatch must abort, not ship.
 */
class BundledRuntimeGateTest {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    @Test
    @DisplayName("bundle-jre.sh pins OpenJFX, verifies its hash, and gates javafx.web into the image")
    void bundleJreCarriesOpenJfx() throws Exception {
        String s = Files.readString(Path.of("..", "packaging", "tools", "bundle-jre.sh"));
        assertThat(s).contains("FX_VERSION=");
        // one pinned hash per supported platform triple
        for (String platform : new String[]{"osx-aarch64", "osx-x64", "linux-x64"}) {
            assertThat(s).as("platform %s is pinned", platform).contains(platform);
        }
        Matcher hashes = SHA256.matcher(s);
        int count = 0;
        while (hashes.find()) {
            count++;
        }
        assertThat(count).as("a sha256 per platform").isGreaterThanOrEqualTo(3);
        assertThat(s).as("hash verified before unpack").contains("sha256 mismatch");
        assertThat(s).as("the image must actually contain the engine")
                .contains("javafx.web");
        assertThat(s).as("FX jmods join the jlink module path")
                .contains("--module-path \"$JDK/jmods:$FX_DIR\"");
    }

    @Test
    @DisplayName("the Windows lane's inline jlink carries the same OpenJFX law")
    void windowsLaneCarriesOpenJfx() throws Exception {
        String s = Files.readString(Path.of("..", ".github", "workflows", "release.yml"));
        assertThat(s).contains("windows-x64_bin-jmods");
        assertThat(s).as("hash verified").contains("OpenJFX jmods sha256 mismatch");
        assertThat(s).as("image gated on the engine")
                .contains("javafx.web");
    }
}
