package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.216.0 arc-review fixes on the ide-run serving lane, pinned.
 */
class ArcReviewServingTest {

    @Test
    @DisplayName("stripAnsi removes whole escape sequences, ESC byte included")
    void stripAnsiRemovesEscByte() {
        // the old strip removed "[36m" but left \u001B, and the URL
        // pattern's char class swallowed the stray byte into the URL
        String colored = "\u001B[32m  Local:\u001B[0m   \u001B[36mhttp://localhost:5173/\u001B[39m";
        String plain = WebProjectActionProvider.stripAnsi(colored);
        assertThat(plain).doesNotContain("\u001B");
        assertThat(plain).contains("http://localhost:5173/");
        // non-color CSI (cursor/erase) goes too
        assertThat(WebProjectActionProvider.stripAnsi("\u001B[2K\u001B[1Gready"))
                .isEqualTo("ready");
    }

    @Test
    @DisplayName("a URL scan on stripped text yields a clean URL")
    void cleanUrlAfterStrip() {
        String line = WebProjectActionProvider.stripAnsi(
                "\u001B[36mhttp://localhost:3000/\u001B[39m and text");
        assertThat(org.nmox.studio.rack.devices.ServeUrls.firstLocalUrl(line))
                .isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("even an unstripped ESC cannot ride into the captured URL")
    void serveUrlsExcludesControlChars() {
        // belt to stripAnsi's suspenders: the pattern itself refuses
        // control characters in the URL tail
        assertThat(org.nmox.studio.rack.devices.ServeUrls.firstLocalUrl(
                "http://localhost:5173/\u001B[39m"))
                .isEqualTo("http://localhost:5173/");
    }

    @Test
    @DisplayName("each Run owns its serving id — two runs of one project cannot collide")
    void servingIdIsPerInvocation() throws IOException {
        // Source-gated: keyed on the path alone, a second Run replaced
        // the first's registration and whichever exited first
        // deregistered the OTHER's live server — a truthful serving
        // erased (the inverse of the v1.93.0 phantom).
        String src = Files.readString(
                Path.of("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"),
                StandardCharsets.UTF_8);
        assertThat(src).contains("RUN_SEQ.incrementAndGet()");
        int idAt = src.indexOf("\"ide-run:\"");
        int seqAt = src.indexOf("RUN_SEQ.incrementAndGet()");
        assertThat(idAt).isGreaterThan(0);
        assertThat(seqAt).as("the sequence is part of the id expression")
                .isGreaterThan(idAt);
    }

    @Test
    @DisplayName("servingUrlFor: banner → pinned URL, Local: → scanned URL, chatter → null")
    void servingUrlBranches() {
        // the STATIC lane's banner has no localhost URL to scan
        assertThat(WebProjectActionProvider.servingUrlFor(
                "Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ..."))
                .isEqualTo("http://localhost:8000/");
        // a normal dev-server banner rides the shared scan, ANSI and all
        assertThat(WebProjectActionProvider.servingUrlFor(
                "\u001B[32m  Local:\u001B[0m   \u001B[36mhttp://localhost:5173/\u001B[39m"))
                .isEqualTo("http://localhost:5173/");
        // ordinary output announces nothing
        assertThat(WebProjectActionProvider.servingUrlFor("compiled 12 modules")).isNull();
        // a Network: LAN address is deliberately not local
        assertThat(WebProjectActionProvider.servingUrlFor(
                "  Network: http://192.168.1.7:5173/")).isNull();
    }

    @Test
    @DisplayName("the STATIC banner announces the pinned URL the scan can never find")
    void staticBannerAnnouncePinned() throws IOException {
        String src = Files.readString(
                Path.of("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"),
                StandardCharsets.UTF_8);
        // python prints "Serving HTTP on 0.0.0.0 port 8000" — no
        // localhost URL in it, so without this branch the STATIC lane
        // (the classic-web persona's whole world) never registered
        assertThat(src).contains("Serving HTTP");
        assertThat(src).contains("WebProjectCommands.STATIC_PORT");
    }
}
