package org.nmox.studio.application;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 20 gate: module OpenIDE spec versions track the product release
 * train, not the frozen {@code 1.0-SNAPSHOT} reactor version.
 *
 * <p>The mechanism (root pom, nbm-maven-plugin pluginManagement comment):
 * every module's {@code src/main/nbm/manifest.mf} declares
 * {@code OpenIDE-Module-Specification-Version: ${spec.version}}, the
 * filter-nbm-source-manifest execution interpolates the root
 * {@code <spec.version>} property into {@code target/nbm-manifest/manifest.mf},
 * and {@code nbm:manifest} keeps source-manifest entries verbatim
 * (conditionallyAddAttribute). The release workflow stamps the tag's version
 * over the property in the same steps that stamp branding's currentVersion.
 *
 * <p>These tests make a half-bump structurally impossible:
 * <ul>
 *   <li>every shipped module JAR on this module's test classpath (all 11 —
 *       the exact set the application assembles) must carry the injected
 *       {@code ${spec.version}} byte-for-byte, so the wiring can't silently
 *       fall back to the pom-derived 1.0 for any one module;</li>
 *   <li>every source manifest must carry the literal placeholder, so no
 *       module can drift to a hardcoded number that a future property bump
 *       would leave behind.</li>
 * </ul>
 */
class SpecVersionGateTest {

    /** The 11 modules the application ships (see application/pom.xml deps). */
    private static final Map<String, String> CODENAME_TO_DIR = Map.ofEntries(
            Map.entry("org.nmox.NMOX.Studio.branding", "branding"),
            Map.entry("org.nmox.NMOX.Studio.core", "core"),
            Map.entry("org.nmox.NMOX.Studio.ui", "ui"),
            Map.entry("org.nmox.NMOX.Studio.editor", "editor"),
            Map.entry("org.nmox.NMOX.Studio.project", "project"),
            Map.entry("org.nmox.NMOX.Studio.tools", "tools"),
            Map.entry("org.nmox.NMOX.Studio.rack", "rack"),
            Map.entry("org.nmox.NMOX.Studio.infra", "infra"),
            Map.entry("org.nmox.NMOX.Studio.apiclient", "apiclient"),
            Map.entry("org.nmox.NMOX.Studio.dbstudio", "dbstudio"),
            Map.entry("org.nmox.NMOX.Studio.web3", "web3"));

    private static final String PLACEHOLDER_LINE =
            "OpenIDE-Module-Specification-Version: ${spec.version}";

    private static String expectedSpecVersion() {
        String v = System.getProperty("spec.version");
        assertThat(v)
                .as("surefire must inject ${spec.version} (application/pom.xml "
                        + "systemPropertyVariables)")
                .isNotNull();
        return v;
    }

    @Test
    @DisplayName("spec.version is a release-train version, not the frozen 1.0")
    void specVersionIsReleaseShaped() {
        assertThat(expectedSpecVersion())
                .matches("\\d+\\.\\d+\\.\\d+")
                .isNotEqualTo("1.0.0");
    }

    @Test
    @DisplayName("all 11 shipped module jars carry OpenIDE-Module-Specification-Version = ${spec.version}")
    void everyModuleJarCarriesTheProductSpecVersion() throws Exception {
        String expected = expectedSpecVersion();

        Map<String, String> found = new TreeMap<>();
        Enumeration<URL> manifests = Thread.currentThread().getContextClassLoader()
                .getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            URL url = manifests.nextElement();
            try (InputStream in = url.openStream()) {
                Attributes main = new Manifest(in).getMainAttributes();
                String codeName = main.getValue("OpenIDE-Module");
                if (codeName != null && codeName.startsWith("org.nmox.")) {
                    found.put(codeName,
                            main.getValue("OpenIDE-Module-Specification-Version"));
                }
            }
        }

        assertThat(found.keySet())
                .as("every shipped NMOX module must be on the application's "
                        + "test classpath so this gate sees its manifest")
                .containsExactlyInAnyOrderElementsOf(CODENAME_TO_DIR.keySet());
        found.forEach((codeName, spec) -> assertThat(spec)
                .as("%s OpenIDE-Module-Specification-Version", codeName)
                .isEqualTo(expected));
    }

    @Test
    @DisplayName("every source manifest declares the ${spec.version} placeholder, never a hardcoded number")
    void everySourceManifestDeclaresThePlaceholder() throws Exception {
        // NMOX-Studio-sample rides the same scheme (dev template, not shipped).
        List<String> dirs = new java.util.ArrayList<>(CODENAME_TO_DIR.values());
        dirs.add("NMOX-Studio-sample");
        for (String dir : dirs) {
            Path mf = Path.of("..", dir, "src", "main", "nbm", "manifest.mf");
            assertThat(mf).as("%s must carry a source manifest", dir).exists();
            String text = Files.readString(mf, StandardCharsets.UTF_8);
            assertThat(text)
                    .as("%s/src/main/nbm/manifest.mf must declare the "
                            + "interpolated placeholder", dir)
                    .contains(PLACEHOLDER_LINE);
            assertThat(text.lines()
                    .filter(l -> l.startsWith("OpenIDE-Module-Specification-Version"))
                    .count())
                    .as("%s must declare the spec version exactly once", dir)
                    .isEqualTo(1);
        }
    }
}
