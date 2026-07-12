package org.nmox.studio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ledger 21 gate: the in-app update center, fed from GitHub releases.
 *
 * <p>Three files carry the contract and each drifts independently, so all
 * three are pinned here:
 * <ul>
 *   <li><b>layer.xml</b> registers the update provider. The catalog URL rides
 *       {@code /releases/latest/download/} — GitHub's stable 302 to the newest
 *       release's asset — so a shipped app follows new releases with no code
 *       change. Break the URL and every installed copy silently stops seeing
 *       updates; nothing else in the build would notice.</li>
 *   <li><b>release.yml</b> must actually publish what that URL promises:
 *       updates.xml plus the 11 module NBMs, as additional release assets.</li>
 *   <li><b>build-update-site.sh</b> must pin each NBM inside the catalog to
 *       its own release tag (ABSOLUTE URLs). The platform resolves relative
 *       distribution URLs against the pre-redirect /latest/ catalog URL
 *       (AutoupdateCatalogParser never sees the 302 target), so relative URLs
 *       would let a cached older catalog download newer NBM bytes and fail
 *       its own SHA-512 digests.</li>
 * </ul>
 */
class UpdateCenterTest {

    /** The one URL a shipped app dials. The registration and docs must agree on it. */
    private static final String CATALOG_URL =
            "https://github.com/NMOX/NMOX-Studio/releases/latest/download/updates.xml";

    /** Where each release's NBMs are pinned (script appends /v<version>/<file>.nbm). */
    private static final String PINNED_PREFIX =
            "https://github.com/NMOX/NMOX-Studio/releases/download/v";

    private static final Path LAYER =
            Path.of("src", "main", "resources", "org", "nmox", "studio", "ui", "layer.xml");
    private static final Path BUNDLE = Path.of(
            "src", "main", "resources", "org", "nmox", "studio", "ui", "resources", "Bundle.properties");
    private static final Path WORKFLOW = Path.of("..", ".github", "workflows", "release.yml");
    private static final Path SCRIPT = Path.of("..", "scripts", "build-update-site.sh");

    private static final List<String> PRODUCT_MODULES = List.of(
            "branding", "core", "ui", "editor", "project", "tools",
            "rack", "infra", "apiclient", "dbstudio", "web3");

    private static String read(Path p) throws Exception {
        assertThat(p).as("%s must exist", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** The registration block for the update center, cut out of layer.xml. */
    private static String registrationBlock() throws Exception {
        String layer = read(LAYER);
        Matcher m = Pattern.compile(
                "<file name=\"org-nmox-studio-update-center\\.instance\">(.*?)</file>",
                Pattern.DOTALL).matcher(layer);
        assertThat(m.find())
                .as("layer.xml must register Services/AutoupdateType/"
                        + "org-nmox-studio-update-center.instance")
                .isTrue();
        // The file entry must actually sit under Services/AutoupdateType —
        // anywhere else the platform never sees it.
        int services = layer.indexOf("<folder name=\"Services\">");
        int autoupdateType = layer.indexOf("<folder name=\"AutoupdateType\">");
        assertThat(services).isNotNegative();
        assertThat(autoupdateType).isGreaterThan(services);
        assertThat(m.start()).isGreaterThan(autoupdateType);
        return m.group(1);
    }

    @Test
    @DisplayName("update center registered: exact /latest/ catalog URL, enabled, STANDARD, factory-instantiated")
    void layerRegistersTheUpdateCenter() throws Exception {
        String block = registrationBlock();

        // Attribute names verified against the shipped autoupdate-services
        // jar (AutoupdateCatalogFactory.createUpdateProvider reads url,
        // enabled, category, trusted; displayName rides the layer decorator).
        assertThat(block)
                .contains("<attr name=\"url\" stringvalue=\"" + CATALOG_URL + "\"/>")
                .contains("<attr name=\"enabled\" boolvalue=\"true\"/>")
                .contains("<attr name=\"trusted\" boolvalue=\"true\"/>")
                .contains("<attr name=\"category\" stringvalue=\"STANDARD\"/>")
                .contains("<attr name=\"instanceCreate\" methodvalue=\"org.netbeans.modules"
                        + ".autoupdate.updateprovider.AutoupdateCatalogFactory.createUpdateProvider\"/>")
                .contains("<attr name=\"instanceOf\" stringvalue=\"org.netbeans.spi"
                        + ".autoupdate.UpdateProvider\"/>");
    }

    @Test
    @DisplayName("the display name the Plugins Settings tab shows resolves from the resources Bundle")
    void displayNameResolves() throws Exception {
        assertThat(registrationBlock()).contains(
                "<attr name=\"displayName\" bundlevalue=\"org.nmox.studio.ui.resources.Bundle"
                        + "#Services/AutoupdateType/org-nmox-studio-update-center.instance\"/>");
        assertThat(read(BUNDLE))
                .contains("Services/AutoupdateType/org-nmox-studio-update-center.instance="
                        + "NMOX Studio Updates");
    }

    @Test
    @DisplayName("release workflow builds the catalog and uploads it + the NBMs as release assets")
    void releaseWorkflowShipsTheCatalog() throws Exception {
        String workflow = read(WORKFLOW);

        assertThat(workflow)
                .as("the linux lane must run the update-site script with the tag version")
                .contains("./scripts/build-update-site.sh \"${{ needs.version.outputs.version }}\"");
        // Line-anchored: "updates.xml.gz" contains "updates.xml" as a
        // substring, so a bare contains() would keep passing after the
        // plain catalog line was deleted (found by mutation).
        assertThat(workflow)
                .as("the catalog and NBMs must ride the linux artifact upload")
                .containsPattern("(?m)^\\s*target/netbeans_site/updates\\.xml$")
                .containsPattern("(?m)^\\s*target/netbeans_site/\\*\\.nbm$");
        assertThat(workflow)
                .as("the release step must attach them as assets — that is what "
                        + "the /latest/download/ URL redirects to")
                .containsPattern("(?m)^\\s*artifacts/\\*\\*/updates\\.xml$")
                .containsPattern("(?m)^\\s*artifacts/\\*\\*/\\*\\.nbm$");
    }

    @Test
    @DisplayName("the site script pins NBM URLs to their release tag and gates the module set")
    void scriptPinsAbsoluteUrlsAndGatesModules() throws Exception {
        String script = read(SCRIPT);

        assertThat(script)
                .as("NBMs must be pinned by absolute URL (relative would resolve "
                        + "against the floating /latest/ catalog URL)")
                .contains("-Dmaven.nbm.customDistBase=\"" + PINNED_PREFIX + "${VERSION}\"");
        assertThat(script)
                .as("the never-shipped sample template must be pruned + gated")
                .contains("NMOX.Studio.sample");
        for (String module : PRODUCT_MODULES) {
            assertThat(script)
                    .as("the 11-module gate must name '%s'", module)
                    .containsPattern("\\b" + module + "\\b");
        }
    }

    /**
     * Shape check on a locally generated catalog (scripts/build-update-site.sh).
     * Skipped when none has been generated — CI's release lane runs the same
     * gates in the script itself; this runs them against the parsed XML when a
     * developer builds a site locally.
     */
    @Test
    @DisplayName("a generated catalog lists all 11 modules at the root pom's spec.version, sample absent")
    void generatedCatalogShape() throws Exception {
        Path catalog = Path.of("..", "target", "netbeans_site", "updates.xml");
        assumeTrue(Files.exists(catalog),
                "no locally generated update site — run scripts/build-update-site.sh first");
        String xml = Files.readString(catalog, StandardCharsets.UTF_8);

        for (String module : PRODUCT_MODULES) {
            assertThat(xml).contains("codenamebase=\"org.nmox.NMOX.Studio." + module + "\"");
        }
        assertThat(xml).doesNotContain("org.nmox.NMOX.Studio.sample");

        Matcher count = Pattern.compile("<module codenamebase=").matcher(xml);
        int modules = 0;
        while (count.find()) {
            modules++;
        }
        assertThat(modules).as("exactly the 11 product modules").isEqualTo(11);

        // Catalog spec versions must match the tree that generated it —
        // an update center that lies about versions is worse than none.
        Matcher spec = Pattern.compile("<spec\\.version>(.+?)</spec\\.version>")
                .matcher(read(Path.of("..", "pom.xml")));
        assertThat(spec.find()).isTrue();
        String expected = spec.group(1);
        Matcher m = Pattern.compile("OpenIDE-Module-Specification-Version=\"([^\"]+)\"")
                .matcher(xml);
        while (m.find()) {
            assertThat(m.group(1))
                    .as("every catalog entry carries the root spec.version")
                    .isEqualTo(expected);
        }
    }
}
