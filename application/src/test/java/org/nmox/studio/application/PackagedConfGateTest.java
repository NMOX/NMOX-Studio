package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module-opens law, gated on the PACKAGED application — not the
 * conf source file. Every distribution (DMG, deb, tar.gz, portable
 * zip, Windows installer, and dev launches of the assembled target)
 * wraps {@code target/nmoxstudio}, so the one etc/nmoxstudio.conf
 * asserted here is the one every launcher reads.
 *
 * <p>Runs in the integration-test phase (see the surefire execution in
 * this module's pom): the assembled app only exists after package, so
 * a plain test-phase run would gate a file that is not there yet.
 *
 * <p>The 1.195.0 smoke test found the platform's editor-settings
 * storage reflecting into {@code java.util.prefs.AbstractPreferences}
 * and failing with InaccessibleObjectException on every editor open,
 * because {@code java.prefs} was the one module the opens list never
 * covered. This gate pins the full set so a future conf edit can
 * neither drop an existing open nor lose the java.prefs one again.
 */
class PackagedConfGateTest {

    /** Every open the platform needs, including the java.prefs fix. */
    private static final List<String> REQUIRED_OPENS = List.of(
            "java.base/java.net",
            "java.base/java.lang",
            "java.base/java.lang.reflect",
            "java.base/java.io",
            "java.base/java.security",
            "java.base/java.util",
            "java.prefs/java.util.prefs",
            "java.desktop/javax.swing",
            "java.desktop/javax.swing.text",
            "java.desktop/java.awt",
            "java.desktop/sun.awt");

    @Test
    @DisplayName("The assembled app's conf opens every module the platform reflects into")
    void packagedConfCarriesEveryRequiredOpen() throws Exception {
        Path conf = Path.of("target", "nmoxstudio", "etc", "nmoxstudio.conf");
        assertThat(conf)
                .as("the assembled application's conf (integration-test runs after package)")
                .exists();
        String text = Files.readString(conf);
        String options = defaultOptionsLine(text);
        for (String open : REQUIRED_OPENS) {
            assertThat(options)
                    .as("default_options opens %s", open)
                    .contains("-J--add-opens=" + open + "=ALL-UNNAMED");
        }
    }

    private static String defaultOptionsLine(String conf) {
        for (String line : conf.split("\n")) {
            if (line.startsWith("default_options=")) {
                return line;
            }
        }
        throw new AssertionError("no default_options line in the packaged conf");
    }
}
