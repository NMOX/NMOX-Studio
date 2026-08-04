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
        // The FX jmods are ALWAYS on the module path; the JDK's own half is
        // conditional, because a JDK built with --enable-linkable-runtime
        // (JEP 493, JDK 24+) ships no jmods and jlink links the platform
        // modules from the running image instead — Temurin 25 on the linux
        // and macOS runners is that build, and assuming jmods there is what
        // made v1.253.0 ship zero assets.
        assertThat(s).as("FX jmods join the jlink module path")
                .contains("--module-path \"${JDK_MODULE_PATH}$FX_DIR\"");
        assertThat(s).as("the JDK half of the module path is conditional")
                .contains("if [ -d \"$JDK/jmods\" ]");
        assertThat(s).as("a jmods-less JDK names the platform modules from the "
                        + "JDK ITSELF — a hand-picked subset silently ships a "
                        + "narrower runtime (measured 39 modules vs 76)")
                .contains("--list-modules");
    }

    @Test
    @DisplayName("the Windows lane's inline jlink carries the same OpenJFX law")
    void windowsLaneCarriesOpenJfx() throws Exception {
        String s = Files.readString(Path.of("..", ".github", "workflows", "release.yml"));
        assertThat(s).contains("windows-x64_bin-jmods");
        assertThat(s).as("hash verified").contains("OpenJFX jmods sha256 mismatch");
        assertThat(s).as("image gated on the engine")
                .contains("javafx.web");
        assertThat(s).as("the windows lane survives a jmods-less JDK too "
                        + "(JEP 493): windows Temurin still ships them today, "
                        + "but the branch must exist before it doesn't")
                .contains("Test-Path \"$env:JAVA_HOME\\jmods\"");
    }

    /**
     * The named FX modules can only be granted native access where they
     * actually exist, so the grant is written by whichever script installs
     * the FX-carrying runtime — never by the base conf, which also ships in
     * the portable zip that runs on the host's own usually-FX-less JDK
     * (naming an absent module prints "Unknown module" on every launch).
     * Both jlink sites install the same runtime, so both must write it, or
     * one OS ships a Browser that a future JDK blocks from loading WebKit.
     */
    @Test
    @DisplayName("both jlink sites grant native access to the FX modules they install")
    void bothLanesGrantFxNativeAccess() throws Exception {
        String grant = "--enable-native-access=ALL-UNNAMED,javafx.graphics,javafx.web";
        assertThat(Files.readString(Path.of("..", "packaging", "tools", "bundle-jre.sh")))
                .as("bundle-jre.sh (macOS DMG, linux tar.gz/deb) extends the grant")
                .contains(grant);
        assertThat(Files.readString(Path.of("..", ".github", "workflows", "release.yml")))
                .as("the windows lane extends the same grant")
                .contains(grant);
    }
}
