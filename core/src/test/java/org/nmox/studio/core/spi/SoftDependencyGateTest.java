package org.nmox.studio.core.spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ledger-30 source gate: soft dependencies on the rack ride the
 * {@link ProjectAim}/{@link LiveServings} lookups, never a
 * {@code catch (… | LinkageError)} around a rack call. Every site the
 * v1.46.0 surgery converted is pinned at ZERO such catches, and the
 * files that legitimately keep some (platform-optional guards —
 * Keyring, NotificationDisplayer, editor kits — or rack UI classes
 * behind a HARD dependency) are pinned at their exact count, so a
 * regression to the old idiom fails this test rather than slipping in
 * as one more defensive catch.
 */
class SoftDependencyGateTest {

    /** A catch clause that lists LinkageError among its types. */
    private static final Pattern CATCH_LINKAGE =
            Pattern.compile("catch\\s*\\([^)]*LinkageError");

    /** Relative to the core module dir (where surefire runs this test). */
    private static Map<String, Integer> allowedCatches() {
        Map<String, Integer> allowed = new LinkedHashMap<>();
        // fully converted files: the idiom is GONE — keep it gone
        allowed.put("../apiclient/src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java", 0);
        allowed.put("../apiclient/src/main/java/org/nmox/studio/apiclient/api/ServingBridge.java", 0);
        allowed.put("../apiclient/src/main/java/org/nmox/studio/apiclient/api/BaseUrlOffer.java", 0);
        allowed.put("../apiclient/src/main/java/org/nmox/studio/apiclient/search/ApiRequestSearchProvider.java", 0);
        allowed.put("../web3/src/main/java/org/nmox/studio/web3/engine/ChainAutoConnect.java", 0);
        allowed.put("../web3/src/main/java/org/nmox/studio/web3/search/Web3SearchProvider.java", 0);
        allowed.put("../dbstudio/src/main/java/org/nmox/studio/dbstudio/search/DbSearchProvider.java", 0);
        allowed.put("../tools/src/main/java/org/nmox/studio/tools/npm/NpmExplorerTopComponent.java", 0);
        allowed.put("../tools/src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java", 0);
        allowed.put("../tools/src/main/java/org/nmox/studio/tools/npm/WebProjectOpenedHook.java", 0);
        allowed.put("../ui/src/main/java/org/nmox/studio/ui/actions/OpenFolderAction.java", 0);
        // mixed files: the remaining catches guard PLATFORM optionality
        // (NotificationDisplayer, editor kits, ConnectionManager, Keyring)
        // or rack UI/util classes behind a hard dependency — each carries a
        // KEPT/why comment at the site
        allowed.put("../web3/src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java", 1);
        allowed.put("../infra/src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java", 2);
        allowed.put("../dbstudio/src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java", 7);
        allowed.put("../project/src/main/java/org/nmox/studio/project/ProjectExplorerTopComponent.java", 4);
        return allowed;
    }

    @Test
    @DisplayName("converted sites stay converted: LinkageError catch counts are pinned per file")
    void linkageErrorCatchCountsArePinned() throws IOException {
        for (Map.Entry<String, Integer> entry : allowedCatches().entrySet()) {
            Path file = Path.of(entry.getKey());
            assertThat(file).as("gated file exists: " + entry.getKey()).exists();
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = CATCH_LINKAGE.matcher(source);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            assertThat(count)
                    .as(entry.getKey() + ": catch(LinkageError) sites — a new one "
                            + "means the soft-dependency idiom is creeping back; "
                            + "use ProjectAim/LiveServings lookups instead (ledger 30)")
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("absence contract: without a provider module, find() is null — the feature-off branch")
    void findReturnsNullWithoutProvider() {
        // core itself ships no provider (rack does); in this module's test
        // environment both lookups take exactly the branch consumers guard
        assertThat(ProjectAim.find()).isNull();
        assertThat(LiveServings.find()).isNull();
    }

    @Test
    @DisplayName("apiclient/web3/infra are rack-free: no main source names a rack package")
    void softModulesNeverNameRack() throws IOException {
        for (String module : new String[]{"apiclient", "web3", "infra"}) {
            Path sourceRoot = Path.of("..", module, "src", "main", "java");
            assertThat(sourceRoot).exists();
            try (Stream<Path> walk = Files.walk(sourceRoot)) {
                for (Path file : (Iterable<Path>) walk
                        .filter(p -> p.toString().endsWith(".java"))::iterator) {
                    assertThat(Files.readString(file, StandardCharsets.UTF_8))
                            .as(file + ": " + module + " dropped its rack dependency "
                                    + "in v1.46.0 — rack types must come back only "
                                    + "through a core.spi facade")
                            .doesNotContain("org.nmox.studio.rack");
                }
            }
        }
    }
}
