package org.nmox.studio.editor.lsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A server the catalog offers is a server something can launch.
 *
 * <p>The Language Servers panel renders a row per catalog entry, with a status
 * and — for the ones that can be installed — a button that installs it. Two
 * entries had no launcher anywhere: Cairo and Move. A user who followed the
 * panel's own instructions and installed either got no intelligence, because
 * nothing in {@code LanguageServers} ever started the binary, and the health
 * report that would have said so could never fire for them either. 41 of 43
 * entries had a provider; these two were catalogued in v1.134.0 and v1.137.0,
 * whose changelog entries claim both servers outright.
 *
 * <p>That is the v1.189.0 law at the scale of a feature: a claim the code does
 * not back. The gate derives both sides — the catalog's binaries from the
 * catalog, the launched commands from the provider source — so a forty-fourth
 * entry added without a launcher fails on the commit that adds it.
 */
class CataloguedServersHaveLaunchersTest {

    /**
     * Binaries the catalog lists for INSTALL guidance without claiming to run
     * them itself. Each needs a reason, or it is a hole rather than an
     * exemption; there are none today.
     */
    private static final List<String> NOT_LAUNCHED_HERE = List.of();

    private static String providerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp",
                "LanguageServers.java"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the catalog is populated, so the law below is not vacuously green")
    void theCatalogIsPopulated() {
        assertThat(LanguageServerCatalog.all())
                .as("an empty catalog would make the check meaningless")
                .hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("every catalogued server's binary is named by a launcher — the panel never offers what nothing starts")
    void everyCatalogueEntryHasALauncher() throws Exception {
        String src = providerSource();
        List<String> orphans = new ArrayList<>();
        for (LanguageServerCatalog.Server server : LanguageServerCatalog.all()) {
            String binary = server.binary();
            if (NOT_LAUNCHED_HERE.contains(binary)) {
                continue;
            }
            if (!src.contains("\"" + binary + "\"")) {
                orphans.add(binary + " (" + server.language() + ")");
            }
        }
        assertThat(orphans)
                .as("the Language Servers panel renders a row and an install for each of these, "
                        + "and nothing launches them")
                .isEmpty();
    }

    @Test
    @DisplayName("the two that were orphaned are launched now, by the commands their catalog rows document")
    void cairoAndMoveAreLaunched() throws Exception {
        String src = providerSource();
        assertThat(src)
                .as("scarb serves Cairo's LSP through a subcommand, the shape `gleam lsp` already uses")
                .contains("List.of(\"scarb\", \"cairo-language-server\")");
        assertThat(src).contains("List.of(\"move-analyzer\")");
    }
}
