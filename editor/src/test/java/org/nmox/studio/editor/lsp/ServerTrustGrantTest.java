package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.lsp.ServerTrust.Reach;
import org.nmox.studio.editor.lsp.ServerTrust.Verdict;
import org.openide.util.NbBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a project is trusted, the servers that were waiting for it start for
 * the files already open, and pyright, measured, does not wait at all
 * (3.5.10).
 */
class ServerTrustGrantTest {

    private final List<String> said = new ArrayList<>();
    private int restarts;
    private boolean restartWorks = true;
    private java.util.function.Consumer<Runnable> realLane;
    private java.util.function.Consumer<String> realStatus;

    @BeforeEach
    void seams() {
        realLane = ServerTrust.lane;
        realStatus = ServerTrust.status;
        ServerTrust.forgetForTest();
        ServerTrust.tookRefusal();
        ServerTrust.trusted = dir -> false;
        ServerTrust.noticeFirst = dir -> true;
        ServerTrust.lane = Runnable::run;
        ServerTrust.status = said::add;
        ServerTrust.restart = () -> {
            restarts++;
            return restartWorks;
        };
        System.setProperty("nmox.shots.dir", "the notification is not under test"); // log only
    }

    @AfterEach
    void theRealOnes() {
        System.clearProperty("nmox.shots.dir");
        ServerTrust.trusted = org.nmox.studio.rack.service.WorkspaceTrust::isTrusted;
        ServerTrust.noticeFirst = org.nmox.studio.rack.service.TrustNotices::firstFor;
        ServerTrust.restart = ServerRestart::reopenEditorsInServers;
        ServerTrust.lane = realLane;
        ServerTrust.status = realStatus;
        ServerTrust.forgetForTest();
        ServerTrust.tookRefusal();
    }

    @Test
    @DisplayName("a grant on the project a server was refused for restarts the servers, once, and says so")
    void aGrantRestarts(@TempDir Path project) {
        File dir = project.toFile();
        assertThat(ServerTrust.refuses("rust-analyzer", dir)).isTrue();

        assertThat(ServerTrust.granted(dir)).isTrue();
        assertThat(restarts).isEqualTo(1);
        assertThat(said).containsExactly(NbBundle.getMessage(ServerTrust.class, "ServerTrust_started"));

        assertThat(ServerTrust.granted(dir)).as("nothing waits any more").isFalse();
        assertThat(restarts).isEqualTo(1);
    }

    @Test
    @DisplayName("a grant on a folder above the project counts: a repository trusted from the git chip starts its project's servers")
    void aGrantAboveCounts(@TempDir Path repo) {
        File project = repo.resolve("packages/app").toFile();
        assertThat(ServerTrust.refuses("rust-analyzer", project)).isTrue();

        assertThat(ServerTrust.granted(repo.toFile())).isTrue();
        assertThat(restarts).isEqualTo(1);
    }

    @Test
    @DisplayName("a grant somewhere else restarts nothing: not for a sibling whose name begins the same, not for a folder below")
    void aGrantElsewhereDoesNothing(@TempDir Path root) {
        File project = root.resolve("app").toFile();
        assertThat(ServerTrust.refuses("rust-analyzer", project)).isTrue();

        assertThat(ServerTrust.granted(root.resolve("ap").toFile())).isFalse();
        assertThat(ServerTrust.granted(root.resolve("application").toFile())).isFalse();
        assertThat(ServerTrust.granted(new File(project, "src")))
                .as("trusting a folder inside the project does not trust the project").isFalse();
        assertThat(ServerTrust.granted(null)).isFalse();
        assertThat(restarts).isZero();
        assertThat(said).isEmpty();

        assertThat(ServerTrust.granted(project)).as("and the project is still waiting").isTrue();
    }

    @Test
    @DisplayName("a grant with nothing refused restarts nothing: no server is started for a folder nobody opened a file in")
    void nothingWaitingNothingStarted(@TempDir Path project) {
        assertThat(ServerTrust.refuses("gopls", project.toFile())).as("a server that reads was never refused").isFalse();
        assertThat(ServerTrust.granted(project.toFile())).isFalse();
        assertThat(restarts).isZero();
    }

    @Test
    @DisplayName("when the platform's way to send the open files again is not there, the user is told to reopen, by name")
    void whenTheRestartCannotBeDone(@TempDir Path project) {
        restartWorks = false;
        assertThat(ServerTrust.refuses("/opt/tools/bin/rust-analyzer", project.toFile())).isTrue();

        assertThat(ServerTrust.granted(project.toFile())).isTrue();
        assertThat(said).containsExactly(NbBundle.getMessage(ServerTrust.class, "ServerTrust_reopen", "rust-analyzer"));
    }

    @Test
    @DisplayName("a file in no project is not waiting for any folder")
    void noProjectIsNotWaiting(@TempDir Path anywhere) {
        assertThat(ServerTrust.refuses("perl", null)).isTrue();
        assertThat(ServerTrust.granted(anywhere.toFile())).isFalse();
        assertThat(ServerTrust.granted(new File("/"))).isFalse();
        assertThat(restarts).isZero();
    }

    @Test
    @DisplayName("pyright reads: it starts in an untrusted project and for a lone file (measured, scripts/probes/pyright-trust)")
    void pyrightReads(@TempDir Path project) {
        assertThat(ServerTrust.reach("pyright-langserver")).isEqualTo(Reach.READS);
        assertThat(ServerTrust.decide("pyright-langserver", project.toFile())).isEqualTo(Verdict.START);
        assertThat(ServerTrust.decide("pyright-langserver", null))
                .as("a lone Python script gets its server").isEqualTo(Verdict.START);
        assertThat(ServerTrust.SERVERS.get("pyright-langserver").why())
                .as("the reason names the measurement, so the next reader can run it again")
                .contains("measured").contains("scripts/probes/pyright-trust");
        assertThat(Path.of("../scripts/probes/pyright-trust/run.sh")).exists();
    }

    @Test
    @DisplayName("the platform still has the method this asks for: its name, static, public, no arguments")
    void thePlatformsMethodIsThere() throws Exception {
        java.lang.reflect.Method method = Class.forName(ServerRestart.HANDLER).getMethod(ServerRestart.METHOD);
        assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("wiring: the grant is listened for, and the notification's click only asks")
    void wiring() throws Exception {
        // comments stripped (3.5.13): a commented-out line is not wiring
        String src = java.nio.file.Files.readString(
                Path.of("src/main/java/org/nmox/studio/editor/lsp/ServerTrust.java"),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
        assertThat(src).contains("org.nmox.studio.rack.service.WorkspaceTrust.addGrantListener(ServerTrust::granted);");
        int ask = src.indexOf("private static void ask(File projectDir) {");
        assertThat(src.substring(ask, src.indexOf("\n    }\n", ask)))
                .as("the click asks; what follows a yes is the listener's, the same for every door")
                .contains("WorkspaceTrust.requestTrust(projectDir);")
                .doesNotContain("setStatusText");
        assertThat(src).as("a refusal records who waits, before deciding whether to speak")
                .contains("WAITING.putIfAbsent(projectDir, binaryOf(command));");
    }

    @Test
    @DisplayName("a grant through Workspace Trust itself reaches the restart: the listener is really registered")
    void aRealGrantReachesTheRestart(@TempDir Path project) throws Exception {
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest(); // the scratch store, never the developer's
        File dir = project.toFile();
        assertThat(ServerTrust.refuses("rust-analyzer", dir)).isTrue();
        ServerTrust.tookRefusal();
        assertThat(restarts).isZero();

        org.nmox.studio.rack.service.WorkspaceTrust.trust(dir);

        assertThat(restarts).as("no test called granted(): the trust store's own listener did").isEqualTo(1);
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
    }

    @Test
    @DisplayName("eslint and stylelint in an untrusted project are among the waiters: a grant starts them too")
    void theLintersWait(@TempDir Path project) throws Exception {
        File dir = project.toFile();
        assertThat(ServerTrust.refuses("vscode-eslint-language-server", dir)).isTrue();
        ServerTrust.tookRefusal();
        assertThat(ServerTrust.granted(dir)).as("3.5.10 returned null before registering them").isTrue();
        assertThat(restarts).isEqualTo(1);

        assertThat(ServerTrust.refuses("stylelint-lsp", dir)).isTrue();
        ServerTrust.tookRefusal();
        assertThat(ServerTrust.granted(dir)).isTrue();
        assertThat(restarts).isEqualTo(2);
    }

    @Test
    @DisplayName("a folder whose name holds a control character still gets its notice, and nothing is thrown at the caller")
    void aHostileNameDoesNotThrow(@TempDir Path parent) throws Exception {
        System.clearProperty("nmox.shots.dir"); // the notification IS under test here
        File dir = java.nio.file.Files.createDirectories(parent.resolve("re\u0007po")).toFile();
        org.assertj.core.api.Assertions.assertThatCode(() -> ServerTrust.refuses("rust-analyzer", dir))
                .doesNotThrowAnyException();
        ServerTrust.tookRefusal();
    }

    @Test
    @DisplayName("the platform's method can be called from here, and says so: a miss would be answered false")
    void thePlatformsMethodCanBeCalled() {
        assertThat(ServerRestart.reopenEditorsInServers())
                .as("no editor is open, so nothing starts; the call itself must go through").isTrue();
    }
}
