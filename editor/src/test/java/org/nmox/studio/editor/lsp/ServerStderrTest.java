package org.nmox.studio.editor.lsp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.4, "when something goes wrong": a language server's stderr went to
 * DISCARD, so a server that crashed on start left no trace in the log and
 * the user was told nothing until the platform gave up after five deaths.
 */
class ServerStderrTest {

    @Test
    @DisplayName("The tail keeps the last lines, each clipped: no server can grow the read")
    void tailIsBounded() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            out.append("line ").append(i).append('\n');
        }
        out.append("x".repeat(50_000)).append('\n');
        ServerStderr s = new ServerStderr("srv");
        s.drain(new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)));
        List<String> lines = s.lines();
        assertThat(lines).hasSize(ServerStderr.MAX_LINES);
        assertThat(lines.get(lines.size() - 2)).isEqualTo("line 99");
        assertThat(lines.get(lines.size() - 1)).hasSize(ServerStderr.MAX_LINE_CHARS);
    }

    @Test
    @DisplayName("An unexpected exit says what the server said; a clean stop or a TERM says nothing")
    void onlyACrashSpeaks() {
        ServerStderr s = new ServerStderr("gopls");
        s.add("gopls: fatal: cannot parse go.work");
        List<String> said = new ArrayList<>();
        s.exited(0, said::add);
        s.exited(143, said::add);
        assertThat(said).as("a shutdown the client asked for is not news").isEmpty();
        s.exited(2, said::add);
        assertThat(said).hasSize(1);
        assertThat(said.get(0)).contains("gopls").contains("2").contains("cannot parse go.work")
                .contains("restarts on the next use");
    }

    @Test
    @DisplayName("On Windows the platform's stop is TerminateProcess, exit 1: a stop, not a crash")
    void windowsStopIsNotACrash() {
        assertThat(ServerStderr.unexpected(1, true)).isFalse();
        assertThat(ServerStderr.unexpected(1, false)).as("elsewhere 1 is a crash").isTrue();
        assertThat(ServerStderr.unexpected(2, true)).isTrue();
    }

    @Test
    @DisplayName("A crash with nothing on stderr still speaks, without a colon to nowhere")
    void silentCrashStillSpeaks() {
        List<String> said = new ArrayList<>();
        new ServerStderr("clangd").exited(139, said::add);
        assertThat(said).containsExactly(ServerStderr.message("clangd", 139, null));
        assertThat(said.get(0)).contains("clangd").contains("139").doesNotContain(":  ");
    }

    @Test
    @DisplayName("A real process that dies on start reports its last stderr line")
    @DisabledOnOs(OS.WINDOWS)
    void realProcessCrashIsReported() throws Exception {
        Process p = new ProcessBuilder("sh", "-c",
                "echo 'starting' >&2; echo 'fatal: no tsconfig.json found' >&2; exit 3").start();
        CompletableFuture<String> said = new CompletableFuture<>();
        ServerStderr.watch(p, "typescript-language-server", said::complete);
        String message = said.get(20, TimeUnit.SECONDS);
        assertThat(message).contains("typescript-language-server").contains("3")
                .contains("fatal: no tsconfig.json found");
    }

    @Test
    @DisplayName("Gate: the launch drains stderr through ServerStderr, never DISCARD")
    void launchNeverDiscardsStderr() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertThat(src).doesNotContain("Redirect.DISCARD").contains("ServerStderr.watch(process");
    }
}
