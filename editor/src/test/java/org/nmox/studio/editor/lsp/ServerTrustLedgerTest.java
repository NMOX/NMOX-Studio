package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.lsp.ServerTrust.Reach;
import org.nmox.studio.editor.lsp.ServerTrust.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No language server that runs a project's code starts before the project
 * is trusted, and every server is written down as one that does or one that
 * does not (3.5.6).
 *
 * <p>Measured before the change: a scratch Cargo project whose
 * {@code build.rs} writes a marker file, opened in a folder nobody had
 * trusted. The marker appeared within a minute of {@code src/main.rs} being
 * opened. The editor starts rust-analyzer on file open, and rust-analyzer
 * builds.
 */
class ServerTrustLedgerTest {

    private static final Path SOURCE = Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java");

    @BeforeEach
    @AfterEach
    void reset() {
        ServerTrust.trusted = org.nmox.studio.rack.service.WorkspaceTrust::isTrusted;
        ServerTrust.forgetForTest();
        ServerTrust.tookRefusal();
    }

    /** Every binary a launch in LanguageServers names as a literal. */
    private static Set<String> launchedBinaries() throws Exception {
        String src = Files.readString(SOURCE, StandardCharsets.UTF_8);
        Set<String> binaries = new TreeSet<>();
        Matcher call = Pattern.compile("\\b(provide|launchFirst|launch)\\(").matcher(src);
        Pattern first = Pattern.compile("List\\.of\\(\\s*\"([^\"]+)\"");
        while (call.find()) {
            String statement = src.substring(call.start(), src.indexOf(';', call.end()));
            Matcher literal = first.matcher(statement);
            while (literal.find()) {
                binaries.add(literal.group(1));
            }
        }
        Matcher npm = Pattern.compile("launchNpm\\(\\s*\\w+,\\s*\"([^\"]+)\"").matcher(src);
        while (npm.find()) {
            binaries.add(npm.group(1));
        }
        // the two whose command is assembled from a resolved path
        Matcher reportedAs = Pattern.compile("\\),\\s*\"(ngserver|vue-language-server)\"\\)").matcher(src);
        while (reportedAs.find()) {
            binaries.add(reportedAs.group(1));
        }
        return binaries;
    }

    @Test
    @DisplayName("every binary the launches name is classified, with a reason, and nothing else is")
    void everyServerIsClassified() throws Exception {
        Set<String> launched = launchedBinaries();
        assertThat(launched.size()).as("binaries found in the source").isGreaterThan(55);
        assertThat(launched).contains("rust-analyzer", "typescript-language-server", "ngserver", "R", "perl");

        List<String> unclassified = new ArrayList<>();
        for (String binary : launched) {
            if (!ServerTrust.SERVERS.containsKey(binary)) {
                unclassified.add(binary);
            }
        }
        assertThat(unclassified)
                .as("language servers launched and not written down in ServerTrust: say whether each RUNS the "
                        + "project's code or only READS it, and why (until then each is held)")
                .isEmpty();
        Set<String> stale = new TreeSet<>(ServerTrust.SERVERS.keySet());
        stale.removeAll(launched);
        assertThat(stale).as("classified and no longer launched").isEmpty();
        ServerTrust.SERVERS.forEach((binary, entry) ->
                assertThat(entry.why().length()).as("the reason for " + binary).isGreaterThan(8));
    }

    @Test
    @DisplayName("a server that runs project code does not start in an untrusted project, and starts in a trusted one")
    void runsWaitsForTrust(@TempDir Path project) {
        File dir = project.toFile();
        ServerTrust.trusted = d -> false;
        assertThat(ServerTrust.decide("rust-analyzer", dir)).isEqualTo(Verdict.UNTRUSTED);
        assertThat(ServerTrust.decide("R", dir)).isEqualTo(Verdict.UNTRUSTED);
        assertThat(ServerTrust.decide("/somewhere/node_modules/.bin/svelteserver", dir))
                .as("a resolved path is known by its file name").isEqualTo(Verdict.UNTRUSTED);
        assertThat(ServerTrust.decide("typescript-language-server.cmd", dir))
                .as("npm's Windows shim is the same server").isEqualTo(Verdict.START);
        assertThat(ServerTrust.decide("/usr/local/lib/node_modules/.bin/vscode-json-language-server", dir))
                .as("and one that only reads is known by its file name too").isEqualTo(Verdict.START);

        ServerTrust.trusted = d -> d.equals(dir);
        assertThat(ServerTrust.decide("rust-analyzer", dir)).isEqualTo(Verdict.START);
    }

    @Test
    @DisplayName("a server that only reads starts anywhere: untrusted, or with no project at all")
    void readsStartsAnywhere(@TempDir Path project) {
        ServerTrust.trusted = d -> false;
        for (String binary : List.of("vscode-json-language-server", "vscode-html-language-server",
                "vscode-css-language-server", "gopls", "clangd", "bash-language-server")) {
            assertThat(ServerTrust.decide(binary, project.toFile())).as(binary).isEqualTo(Verdict.START);
            assertThat(ServerTrust.decide(binary, null)).as(binary + ", a file in no project").isEqualTo(Verdict.START);
        }
    }

    @Test
    @DisplayName("held, and unknown, are treated as running")
    void heldIsNotStarted(@TempDir Path project) {
        ServerTrust.trusted = d -> false;
        assertThat(ServerTrust.reach("gleam")).isEqualTo(Reach.HELD);
        assertThat(ServerTrust.decide("gleam", project.toFile())).isEqualTo(Verdict.UNTRUSTED);
        assertThat(ServerTrust.reach("a-server-added-tomorrow")).isEqualTo(Reach.HELD);
        assertThat(ServerTrust.decide("a-server-added-tomorrow", project.toFile())).isEqualTo(Verdict.UNTRUSTED);
    }

    @Test
    @DisplayName("a file in no project gets no server that runs code: there is nothing to trust")
    void noProjectNoRunner() {
        ServerTrust.trusted = d -> true;
        assertThat(ServerTrust.decide("perl", null)).isEqualTo(Verdict.NO_PROJECT);
        assertThat(ServerTrust.decide("racket", null)).isEqualTo(Verdict.NO_PROJECT);
    }

    @Test
    @DisplayName("TypeScript's server waits only where the workspace brings its own TypeScript")
    void typescriptWaitsForItsPayload(@TempDir Path project) throws Exception {
        File dir = project.toFile();
        ServerTrust.trusted = d -> false;
        assertThat(ServerTrust.decide("typescript-language-server", dir))
                .as("a clone before npm install: the user's own TypeScript serves it").isEqualTo(Verdict.START);
        assertThat(ServerTrust.decide("typescript-language-server", null))
                .as("a lone file").isEqualTo(Verdict.START);

        Files.createDirectories(project.resolve("node_modules/typescript/lib"));
        assertThat(ServerTrust.decide("typescript-language-server", dir))
                .as("the server would load this TypeScript, and nobody has trusted it").isEqualTo(Verdict.UNTRUSTED);
        ServerTrust.trusted = d -> true;
        assertThat(ServerTrust.decide("typescript-language-server", dir)).isEqualTo(Verdict.START);
    }

    @Test
    @DisplayName("a refusal is remembered for the caller on this thread, once")
    void aRefusalIsNotReportedAsMissing(@TempDir Path project) {
        ServerTrust.trusted = d -> false;
        System.setProperty("nmox.shots.dir", "the notification is not under test"); // log only
        try {
            assertThat(ServerTrust.refuses("rust-analyzer", project.toFile())).isTrue();
            assertThat(ServerTrust.tookRefusal()).as("the caller learns it was a refusal").isTrue();
            assertThat(ServerTrust.tookRefusal()).as("and only once").isFalse();
            assertThat(ServerTrust.refuses("gopls", project.toFile())).isFalse();
            assertThat(ServerTrust.tookRefusal()).isFalse();
        } finally {
            System.clearProperty("nmox.shots.dir");
        }
    }

    @Test
    @DisplayName("wiring: the launch asks before it resolves or spawns anything, and a refused server is not reported missing")
    void theLaunchAsksFirst() throws Exception {
        String src = Files.readString(SOURCE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int launch = src.indexOf("static LanguageServerProvider.LanguageServerDescription launch(Lookup lookup,\n"
                + "            List<String> command, org.json.JSONObject initOptions) {");
        assertThat(launch).as("the one method every server is launched by").isPositive();
        String body = src.substring(launch, src.indexOf("\n    }\n", launch));
        int asks = body.indexOf("if (ServerTrust.refuses(command.get(0), dir)) {");
        assertThat(asks).as("the launch asks").isPositive();
        assertThat(body.indexOf("ToolLocator.resolveCommand(")).as("before it resolves the command").isGreaterThan(asks);
        assertThat(body.indexOf("new ProcessBuilder(")).as("and before it builds a process").isGreaterThan(asks);

        int reported = src.indexOf("static LanguageServerProvider.LanguageServerDescription reported(");
        String reports = src.substring(reported, src.indexOf("\n    }\n", reported));
        assertThat(reports).contains("result == null && !ServerTrust.tookRefusal()");
    }
}
