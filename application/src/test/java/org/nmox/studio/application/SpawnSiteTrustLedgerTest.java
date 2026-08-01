package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spawn-site trust ledger (v1.224.0): every {@code
 * CommandExecutor.run(} caller in the product's main sources must be
 * CLASSIFIED here — either it gates the spawn with Workspace Trust
 * (directly or via its one calling chain), or the argv is provably not
 * project-controlled and the blessing is written down. A NEW caller
 * fails this test until a human classifies it, because the v1.223.0
 * find proved the alternative: Run Focused Test spawned project
 * runners ungated for ~190 releases, missed by two dedicated security
 * sweeps that each fixed every site they knew about. Enumeration
 * beats recollection.
 */
class SpawnSiteTrustLedgerTest {

    /**
     * file basename → why it may spawn. GATED = calls
     * WorkspaceTrust/TrustGate before the spawn; GATED-BY-CALLER = the
     * only path to it gates; BLESSED = argv is not project-controlled,
     * reason stated.
     */
    private static final Map<String, String> LEDGER = Map.of(
            "WebProjectActionProvider.java",
            "GATED: requestTrust before Run/Build/Test/Clean (v1.103.0)",
            "NpmService.java",
            "GATED: requestTrust before script runs; fixed-tool npm ls/--version excluded (v1.103.0)",
            "RunFocusedTestAction.java",
            "GATED: requestTrust before the runner spawn (v1.223.0)",
            "ProjectConfigDialog.java",
            "GATED: requestTrust before npm add/remove — installs run project lifecycle scripts (v1.224.0)",
            "LanguageServerInstaller.java",
            "GATED: requestTrust on the project-local branch; global installs are our own fixed argv in $HOME (v1.224.0)",
            "NewProjectDialog.java",
            "GATED: template scaffold + install in a directory the wizard itself just created — "
            + "trust pre-granted in place (v1.62.0 blessing)",
            "RackDevice.java",
            "GATED-BY-CALLER: reachable only through CommandDevice.launch/launchWithEnv, "
            + "whose trustCheck gates before exec (v1.93.0)",
            "DockerPanelTopComponent.java",
            "BLESSED: argv is our own fixed docker verbs; a project Dockerfile builds "
            + "inside a container, not on the host");

    @Test
    @DisplayName("every CommandExecutor.run caller is classified in the trust ledger")
    void everySpawnSiteIsClassified() throws IOException {
        Set<String> found = new TreeSet<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> {
                            try {
                                return Files.readString(p).contains("CommandExecutor.run(");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .forEach(p -> found.add(p.getFileName().toString()));
            }
        }
        // CommandExecutor.java itself hosts the primitive, not a call site
        found.remove("CommandExecutor.java");
        assertThat(found)
                .as("a spawn site missing from the ledger is UNCLASSIFIED — decide "
                        + "whether its argv is project-controlled, gate it or bless "
                        + "it IN THE LEDGER, and only then add it here")
                .isEqualTo(new TreeSet<>(LEDGER.keySet()));
    }

    @Test
    @DisplayName("the GATED classifications are true: each gated file references the trust service")
    void gatedFilesReallyGate() throws IOException {
        Map<String, String> paths = Map.of(
                "WebProjectActionProvider.java", "../tools/src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java",
                "NpmService.java", "../tools/src/main/java/org/nmox/studio/tools/npm/NpmService.java",
                "RunFocusedTestAction.java", "../editor/src/main/java/org/nmox/studio/editor/testing/RunFocusedTestAction.java",
                "ProjectConfigDialog.java", "../rack/src/main/java/org/nmox/studio/rack/projectstudio/ProjectConfigDialog.java",
                "LanguageServerInstaller.java", "../editor/src/main/java/org/nmox/studio/editor/lsp/LanguageServerInstaller.java");
        for (Map.Entry<String, String> e : paths.entrySet()) {
            assertThat(Files.readString(Path.of(e.getValue())))
                    .as(e.getKey() + " is classified GATED — the gate must exist in its source")
                    .contains("WorkspaceTrust.requestTrust");
        }
        // web3's forge buttons gate through the soft-dependency facade
        assertThat(Files.readString(Path.of(
                "../web3/src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java")))
                .as("Contract Studio's forge build/test execute the repo's Foundry "
                        + "project (ffi = host commands) — gated via TrustGate (v1.224.0)")
                .contains("TrustGate.find()");
        // and the rack publishes the facade's one implementation
        assertThat(Files.readString(Path.of(
                "../rack/src/main/java/org/nmox/studio/rack/service/RackTrustGate.java")))
                .contains("@ServiceProvider(service = TrustGate.class)");
    }
}
