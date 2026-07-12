package org.nmox.studio.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 23 gate: org.json's VERSION STRING is centralized in one root
 * {@code <orgjson.version>} property so Dependabot bumps all eight module
 * copies in a single PR. The eight copies themselves STAY — module
 * classloaders make a shared org.json wrapper ClassCastException territory
 * (ledger items 3 and 23); only the version literal is DRY.
 *
 * <p>This test makes a re-hardcode structurally loud: every module that
 * declares org.json must reference {@code ${orgjson.version}}, and none may
 * carry a hardcoded numeric version. Surefire runs the application module in
 * its own directory, so sibling poms are reached via {@code ../<module>}.
 */
class OrgJsonVersionGateTest {

    /** The eight modules that wrap their own org.json copy. */
    private static final List<String> MODULES = List.of(
            "core", "rack", "tools", "editor",
            "apiclient", "dbstudio", "web3", "infra");

    /** Captures the &lt;version&gt; declared inside the org.json dependency. */
    private static final Pattern ORGJSON_VERSION = Pattern.compile(
            "<groupId>org\\.json</groupId>\\s*"
            + "<artifactId>json</artifactId>\\s*"
            + "<version>([^<]+)</version>",
            Pattern.DOTALL);

    @Test
    @DisplayName("every module's org.json version references ${orgjson.version}, never a literal")
    void everyModuleReferencesTheProperty() throws Exception {
        for (String module : MODULES) {
            Path pom = Path.of("..", module, "pom.xml");
            assertThat(pom).as("%s must have a pom", module).exists();
            String text = Files.readString(pom, StandardCharsets.UTF_8);

            Matcher m = ORGJSON_VERSION.matcher(text);
            assertThat(m.find())
                    .as("%s declares an org.json dependency with a version", module)
                    .isTrue();
            assertThat(m.group(1).trim())
                    .as("%s/pom.xml org.json version must reference the root property, "
                            + "not a hardcoded literal", module)
                    .isEqualTo("${orgjson.version}");
        }
    }

    @Test
    @DisplayName("the root pom declares orgjson.version as a concrete version")
    void rootPomDeclaresTheProperty() throws Exception {
        String root = Files.readString(Path.of("..", "pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<orgjson\\.version>([^<]+)</orgjson\\.version>").matcher(root);
        assertThat(m.find()).as("root pom must declare <orgjson.version>").isTrue();
        assertThat(m.group(1).trim())
                .as("orgjson.version is the single concrete version all eight copies resolve to")
                .matches("\\d{8}(\\.\\d+)?");
    }
}
