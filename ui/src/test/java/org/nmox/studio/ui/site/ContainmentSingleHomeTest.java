package org.nmox.studio.ui.site;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 111's structural half, ui side: the served docroot and the
 * Browser's page-to-source resolver route through the one guard and
 * keep no rule of their own. The behavioural walks live beside each
 * surface ({@code SiteServerTest}, {@code PageSourceResolverTest});
 * this catches a fifth copy, which no behavioural test can see while
 * the copy still agrees.
 */
class ContainmentSingleHomeTest {

    private static final String[][] POPULATION = {
        {"site", "SiteServer.java"},
        {"browser/devtools", "PageSourceResolver.java"},
    };

    private static String source(String... parts) throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "ui", String.join("/", parts)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("both ui containment calls name core.util.Containment")
    void oneHome() throws Exception {
        assertThat(source("site", "SiteServer.java"))
                .as("the served docroot rides the ONE guard")
                .contains("Containment.resolve(root, path.substring(1))");
        assertThat(source("browser/devtools", "PageSourceResolver.java"))
                .as("page-to-source rides the ONE guard")
                .contains("Containment.resolve(projectDir, candidate)");
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
