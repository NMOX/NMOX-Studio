package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every window icon and splash the platform can show has an NMOX twin.
 *
 * <p>The first walk taken on Windows (3.5) photographed the Apache NetBeans
 * cube in the title bar of a product whose installer, shortcuts and launcher
 * all wear the NMOX mark. The branding carried {@code frame.gif},
 * {@code frame32.gif} and {@code frame48.gif} — the three names anyone would
 * list. The platform asks for more: under a dark look and feel it tries each
 * name's {@code _dark} twin first and finds one in its own {@code core.jar},
 * and it hands the window three large PNG icons besides. macOS draws no icon
 * in a title bar and takes its Dock icon from the launcher, so no walk taken
 * on a Mac could see it.
 *
 * <p>The population is derived from the platform's own jar, so a size or a
 * variant the platform adds tomorrow fails here until it is branded.
 *
 * <p>Runs with the packaged-app gates: the cluster exists only after
 * {@code package}.
 */
class WindowIconBrandingGateTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final String STARTUP = "org/netbeans/core/startup/";
    /** A frame icon or a splash, in any size or theme the platform ships. */
    private static final Pattern ART = Pattern.compile(
            Pattern.quote(STARTUP) + "((?:frame|splash)[^/]*)\\.(gif|png)");

    private static Set<String> entries(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.stream().map(e -> e.getName()).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    @DisplayName("each frame icon and splash in the platform's core.jar has a branded twin")
    void everyPlatformIconIsBranded() throws IOException {
        Set<String> platform = entries(CLUSTER.resolve(Path.of("platform", "core", "core.jar")));
        Set<String> branded = entries(CLUSTER.resolve(
                Path.of("nmoxstudio", "core", "locale", "core_nmoxstudio.jar")));
        Set<String> wanted = new TreeSet<>();
        for (String name : platform) {
            Matcher m = ART.matcher(name);
            if (m.matches()) {
                wanted.add(STARTUP + m.group(1) + "_nmoxstudio." + m.group(2));
            }
        }
        assertThat(wanted).as("frame icons and splashes the platform ships")
                .hasSizeGreaterThanOrEqualTo(9)
                .contains(STARTUP + "frame_dark_nmoxstudio.gif", STARTUP + "frame256_nmoxstudio.png");
        assertThat(branded).as("the branding jar").containsAll(wanted);
    }
}
