package org.nmox.studio.ui.site;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 111's structural half, ui side: the served docroot, the
 * Browser's page-to-source resolver and the Angular schematic's target
 * folder route through the one guard and keep no rule of their own. The
 * behavioural walks live beside each surface ({@code SiteServerTest},
 * {@code PageSourceResolverTest}, {@code NgSchematicTest}); this catches
 * a copy, which no behavioural test can see while the copy still agrees.
 *
 * <p>This population is HAND-KEPT and so cannot prove itself complete —
 * it was not: {@code NgSchematic} sat outside it the day this gate
 * shipped, in front of a trust-gated spawn. Completeness is {@code
 * ContainmentLedgerTest}'s job, which DERIVES the census from every
 * module's sources; what stays here is the other half of the two-proof
 * law (v1.321.0) — that each routed surface still calls the guard.
 */
class ContainmentSingleHomeTest {

    private static final String[][] POPULATION = {
        {"site", "SiteServer.java"},
        {"browser/devtools", "PageSourceResolver.java"},
        {"actions", "NgSchematic.java"},
    };

    private static String source(String... parts) throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "ui", String.join("/", parts)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("every ui containment call names core.util.Containment")
    void oneHome() throws Exception {
        assertThat(source("site", "SiteServer.java"))
                .as("the served docroot rides the ONE guard")
                .contains("Containment.resolve(root, path.substring(1))");
        assertThat(source("browser/devtools", "PageSourceResolver.java"))
                .as("page-to-source rides the ONE guard")
                .contains("Containment.resolve(projectDir, candidate)");
        assertThat(source("actions", "NgSchematic.java"))
                .as("the schematic's target folder — a spawn's cwd — rides the ONE guard")
                .contains("Containment.resolve(root, relative.trim())");
    }

    @Test
    @DisplayName("and neither re-rolls the decision")
    void noSecondRule() throws Exception {
        for (String[] file : POPULATION) {
            for (String line : source(file).split("\n")) {
                boolean resolvesAChild = line.contains("new File(")
                        && (line.contains("getCanonicalFile()")
                        || line.contains("getCanonicalPath()")
                        || line.contains(".normalize()"));
                assertThat(resolvesAChild)
                        .as(file[1] + " must not carry a second containment rule —"
                                + " one decision, one home (ledger 111): " + line.trim())
                        .isFalse();
            }
        }
    }
}
