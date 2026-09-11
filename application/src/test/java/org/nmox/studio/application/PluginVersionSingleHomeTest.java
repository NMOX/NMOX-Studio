package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A build-plugin version has one home: the root pom's {@code pluginManagement}.
 *
 * <p>A module that re-declares a plugin it only needs to CONFIGURE may also
 * re-declare the version, and then the two drift apart silently. That is not
 * hypothetical: Dependabot's maven group moved surefire 3.5.6 → 3.6.0, the
 * bump was absorbed by hand into the root pom, and {@code application/pom.xml}
 * kept its own {@code 3.5.6} — so the ONE module that runs the packaged-app
 * gates ran them on an older surefire than every other module, for two
 * releases, with a green build and a closed Dependabot PR.
 *
 * <p>Nothing could see it. Dependabot reads the root and reports the group
 * satisfied; the module's own pin is simply a different number in a different
 * file. This is the {@code OrgJsonVersionGateTest} shape (v1.50.0, eight
 * copies of one version literal) for plugins rather than dependencies, and the
 * rule is the same: **one home, and the build fails when a second appears.**
 *
 * <p>The population is derived from the root pom itself — every plugin whose
 * version it pins under {@code pluginManagement} — so a plugin added there
 * tomorrow is covered without touching this test.
 */
class PluginVersionSingleHomeTest {

    private static final Path REPO = Path.of("..");

    private static final Pattern PLUGIN = Pattern.compile(
            "<plugin>(.*?)</plugin>", Pattern.DOTALL);

    private static final Pattern ARTIFACT = Pattern.compile("<artifactId>([^<]+)</artifactId>");

    private static final Pattern VERSION = Pattern.compile("<version>([^<]+)</version>");

    @Test
    @DisplayName("the root pom pins plugin versions, so this gate has a population")
    void theRootIsTheHome() throws IOException {
        Map<String, String> pinned = rootPins();
        assertThat(pinned).as("plugins the root pom pins under pluginManagement")
                .hasSizeGreaterThan(3)
                .containsKey("maven-surefire-plugin");
    }

    @Test
    @DisplayName("no module pom re-declares a version the root already pins")
    void noModuleCarriesASecondPin() throws IOException {
        Map<String, String> pinned = rootPins();
        List<String> second = new ArrayList<>();
        for (Path pom : modulePoms()) {
            String body = Files.readString(pom, StandardCharsets.UTF_8);
            Matcher blocks = PLUGIN.matcher(body);
            while (blocks.find()) {
                String block = blocks.group(1);
                Matcher a = ARTIFACT.matcher(block);
                Matcher v = VERSION.matcher(block);
                if (!a.find() || !v.find()) {
                    continue;
                }
                String artifact = a.group(1);
                String version = v.group(1);
                String rootVersion = pinned.get(artifact);
                if (rootVersion == null) {
                    continue;
                }
                second.add(REPO.relativize(pom) + ": " + artifact + " pinned at " + version
                        + " while the root pins " + rootVersion
                        + (version.equals(rootVersion)
                                ? " — identical today, and free to drift tomorrow"
                                : " — ALREADY DRIFTED"));
            }
        }
        assertThat(second).as("a plugin version with a second home drifts silently; "
                + "delete the module's <version> and let the root pin apply").isEmpty();
    }

    /** Artifact → version, for every plugin the root pins under pluginManagement. */
    private static Map<String, String> rootPins() throws IOException {
        String body = Files.readString(REPO.resolve("pom.xml"), StandardCharsets.UTF_8);
        int open = body.indexOf("<pluginManagement>");
        assertThat(open).as("the root pom should declare pluginManagement").isGreaterThan(0);
        int close = body.indexOf("</pluginManagement>", open);
        assertThat(close).as("pluginManagement should be closed").isGreaterThan(open);
        Map<String, String> pins = new LinkedHashMap<>();
        Matcher blocks = PLUGIN.matcher(body.substring(open, close));
        while (blocks.find()) {
            String block = blocks.group(1);
            Matcher a = ARTIFACT.matcher(block);
            Matcher v = VERSION.matcher(block);
            if (a.find() && v.find()) {
                pins.put(a.group(1), v.group(1));
            }
        }
        return pins;
    }

    /** Every module pom in the reactor — the root's own is the home and is excluded. */
    private static List<Path> modulePoms() throws IOException {
        List<Path> poms = new ArrayList<>();
        try (var entries = Files.list(REPO)) {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList()) {
                Path pom = dir.resolve("pom.xml");
                if (Files.isRegularFile(pom)) {
                    poms.add(pom);
                }
            }
        }
        assertThat(poms).as("the reactor's module poms").hasSizeGreaterThan(5);
        return poms;
    }
}
