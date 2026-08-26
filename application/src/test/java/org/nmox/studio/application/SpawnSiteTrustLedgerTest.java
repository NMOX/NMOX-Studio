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
            "CheckMyWorkAction.java",
            "GATED-BY-VALIDATION: catalog checkpoints execute argv the parser has "
            + "already law-checked (bare tool name, no shell — the device-file law) "
            + "in the pre-trusted ~/.nmox/learn home, at the learner's button press, "
            + "under runBounded's leash (v2.39.1)",
            "NewExperimentAction.java",
            "GATED: the experiment install — the product's own template in the pre-trusted "
            + "experiments home, at the user's explicit request; package.json guard; "
            + "the NewProjectDialog blessing verbatim (v2.36.0)",
            "RackDevice.java",
            "GATED-BY-CALLER: reachable only through CommandDevice.launch/launchWithEnv, "
            + "whose trustCheck gates before exec (v1.93.0)",
            "DockerPanelTopComponent.java",
            "BLESSED: argv is our own fixed docker verbs; a project Dockerfile builds "
            + "inside a container, not on the host",
            "NgSchematicAction.java",
            "GATED: requestTrust before ng generate — the CLI and the project's "
            + "schematics execute the repo's own code (v1.239.0)");

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

    /**
     * The second enumeration (v1.234.0 review): {@code
     * ProcessSupport.builder(} callers outside core. The v1.230.0 sass
     * spawn was correctly gated but INVISIBLE to the ledger above —
     * builder() is the lower door into the same room, and a future
     * edit could have ungated it without failing any build.
     */
    private static final Map<String, String> BUILDER_LEDGER = Map.of(
            "JsDebugServer.java",
            "GATED-BY-CALLER: every debug action requestTrusts before any spawn (v1.37.0)",
            "PrettierFormatter.java",
            "GATED: project-local .bin/prettier only when isTrusted; else the global tool (v1.102.0)",
            "SassCompiler.java",
            "GATED: resolveBinary checks isTrusted before the project-local .bin/sass (v1.230.0)",
            "DockerClient.java",
            "BLESSED: our own fixed docker verbs; project Dockerfiles build in a container, not the host",
            "CommandExecutor.java",
            "BLESSED-PRIMITIVE: deliberately un-gated (v1.103.0 law) — trust is the caller's job, "
            + "and the CommandExecutor.run ledger above enumerates those callers",
            "InteractiveProcess.java",
            "BLESSED: REPL interpreters from the learning catalog / ENGINE knob — the user's "
            + "chosen tool, not a project-controlled argv",
            "Web3StudioTopComponent.java",
            "GATED: forge build/test behind the TrustGate facade (v1.224.0)");

    @Test
    @DisplayName("every ProcessSupport.builder caller outside core is classified too")
    void everyBuilderSiteIsClassified() throws IOException {
        Set<String> found = new TreeSet<>();
        for (String module : new String[]{"editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> {
                            try {
                                return Files.readString(p).contains("ProcessSupport.builder(");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .forEach(p -> found.add(p.getFileName().toString()));
            }
        }
        assertThat(found)
                .as("a ProcessSupport.builder site missing from BUILDER_LEDGER is "
                        + "UNCLASSIFIED — gate it or bless it in writing first")
                .isEqualTo(new TreeSet<>(BUILDER_LEDGER.keySet()));
    }

    @Test
    @DisplayName("the builder ledger's GATED rows are true in source")
    void gatedBuilderFilesReallyGate() throws IOException {
        assertThat(Files.readString(Path.of(
                "../editor/src/main/java/org/nmox/studio/editor/sass/SassCompiler.java")))
                .contains("WorkspaceTrust.isTrusted");
        assertThat(Files.readString(Path.of(
                "../editor/src/main/java/org/nmox/studio/editor/format/PrettierFormatter.java")))
                .contains("WorkspaceTrust.isTrusted");
    }
}
