package org.nmox.studio.editor.lsp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A language server's problems reach the bus Action Items reads (3.1.0),
 * and the bytes the platform's client reads are the server's bytes.
 */
class DiagnosticsTapTest {

    private final Map<String, List<DiagnosticsBus.Problem>> seen = new ConcurrentHashMap<>();
    private final DiagnosticsBus.Listener listener = (tool, problems) -> {
        if (DiagnosticsTap.isLspTool(tool)) {
            seen.put(tool, problems);
        }
    };

    @BeforeEach
    void listen() {
        DiagnosticsBus.addListener(listener);
    }

    @AfterEach
    void stop() {
        DiagnosticsBus.removeListener(listener);
    }

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] head = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + body.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        return out;
    }

    private static String publish(File file, JSONArray diagnostics) {
        return new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "textDocument/publishDiagnostics")
                .put("params", new JSONObject().put("uri", file.toURI().toString()).put("diagnostics", diagnostics))
                .toString();
    }

    private static JSONObject diagnostic(int line, int severity, String message, Object code) {
        JSONObject d = new JSONObject()
                .put("range", new JSONObject()
                        .put("start", new JSONObject().put("line", line).put("character", 2))
                        .put("end", new JSONObject().put("line", line).put("character", 5)))
                .put("severity", severity)
                .put("message", message);
        if (code != null) {
            d.put("code", code);
        }
        return d;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    /** Reads everything through the tap in chunks of {@code chunk}; returns what the client saw. */
    private static byte[] drain(InputStream in, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[chunk];
        int n;
        while ((n = in.read(buf, 0, chunk)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Reads exactly {@code n} bytes, leaving the stream open (EOF withdraws a server's rows). */
    private static byte[] readN(InputStream in, int n, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[chunk];
        while (out.size() < n) {
            int got = in.read(buf, 0, Math.min(chunk, n - out.size()));
            if (got < 0) {
                break;
            }
            out.write(buf, 0, got);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("every byte reaches the client unchanged, whatever the chunking")
    void passthrough(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("a.ts").toFile();
        byte[] stream = concat(
                frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{}}}"),
                frame(publish(f, new JSONArray().put(diagnostic(3, 1, "Cannot find name 'x'.", 2304)))),
                frame("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":{\"type\":3,\"message\":\"é\"}}"));
        for (int chunk : new int[]{1, 7, 4096}) {
            try (DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "tsserver")) {
                assertThat(drain(tap, chunk)).as("chunk %d", chunk).isEqualTo(stream);
            }
        }
    }

    @Test
    @DisplayName("publishDiagnostics becomes rows: line one-based, errors and warnings only, code kept")
    void rows(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("app.ts").toFile();
        JSONArray ds = new JSONArray()
                .put(diagnostic(0, 1, "Cannot find name 'x'.\nDid you mean 'y'?", 2304))
                .put(diagnostic(9, 2, "'z' is declared but never used.", 6133))
                .put(diagnostic(4, 3, "information", null))
                .put(diagnostic(5, 4, "hint", null));
        byte[] stream = frame(publish(f, ds));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream),
                "/usr/local/bin/typescript-language-server");
        readN(tap, stream.length, 16);
        DiagnosticsTap.awaitIdle();
        List<DiagnosticsBus.Problem> ps = seen.get("lsp:typescript");
        assertThat(ps).extracting(DiagnosticsBus.Problem::line).containsExactly(1, 10);
        assertThat(ps.get(0).message()).isEqualTo("Cannot find name 'x'. (2304)");
        assertThat(ps.get(0).error()).isTrue();
        assertThat(ps.get(1).error()).isFalse();
        assertThat(ps.get(0).file()).isEqualTo(f);
        tap.close();
    }

    @Test
    @DisplayName("an empty publish clears the file, and a closed server withdraws everything")
    void clearsAndWithdraws(@TempDir Path tmp) throws IOException {
        File a = tmp.resolve("a.rs").toFile();
        File b = tmp.resolve("b.rs").toFile();
        byte[] stream = concat(
                frame(publish(a, new JSONArray().put(diagnostic(1, 1, "mismatched types", "E0308")))),
                frame(publish(b, new JSONArray().put(diagnostic(2, 2, "unused variable", null)))),
                frame(publish(a, new JSONArray())));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "rust-analyzer");
        byte[] buf = new byte[stream.length];
        int read = 0;
        while (read < stream.length) {
            read += tap.read(buf, read, stream.length - read);
        }
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:rust-analyzer")).extracting(DiagnosticsBus.Problem::file).containsExactly(b);
        assertThat(tap.read()).isEqualTo(-1);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:rust-analyzer")).isEmpty();
    }

    @Test
    @DisplayName("a malformed frame is passed through and the stream carries on")
    void malformed(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("m.go").toFile();
        byte[] stream = concat(
                "Content-Length: 5\r\n\r\n{nope".getBytes(StandardCharsets.US_ASCII),
                frame(publish(f, new JSONArray().put(diagnostic(0, 1, "undefined: foo", null)))));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "gopls");
        assertThat(readN(tap, stream.length, 3)).isEqualTo(stream);
        DiagnosticsTap.awaitIdle();
        // "{nope" never mentions publishDiagnostics, so it is skipped before parsing;
        // the next frame is read as usual
        assertThat(seen.get("lsp:gopls")).extracting(DiagnosticsBus.Problem::message)
                .containsExactly("undefined: foo");
        tap.close();
    }

    @Test
    @DisplayName("an oversized body is not buffered, and the frame after it is still read")
    void oversize(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("big.py").toFile();
        byte[] huge = new byte[DiagnosticsTap.MAX_BODY + 1];
        java.util.Arrays.fill(huge, (byte) ' ');
        byte[] stream = concat(
                ("Content-Length: " + huge.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), huge,
                frame(publish(f, new JSONArray().put(diagnostic(6, 1, "expected ':'", null)))));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "pylsp");
        assertThat(readN(tap, stream.length, 65536)).hasSize(stream.length);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:pylsp")).extracting(DiagnosticsBus.Problem::line).containsExactly(7);
        tap.close();
    }

    @Test
    @DisplayName("a frame whose body is unreadable is skipped, and the frames after it in the same read are read")
    void badBodyThenGoodFramesInOneRead(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("r.ts").toFile();
        byte[] stream = concat(
                frame("{\"method\":\"textDocument/publishDiagnostics\", not json"),
                frame(publish(f, new JSONArray().put(diagnostic(0, 1, "first", null)))),
                frame(publish(f, new JSONArray().put(diagnostic(1, 1, "second", null)))));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "resync");
        assertThat(readN(tap, stream.length, stream.length)).isEqualTo(stream);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:resync")).extracting(DiagnosticsBus.Problem::message).containsExactly("second");
        tap.close();
    }

    @Test
    @DisplayName("headers ending in a bare LF LF are read, as lsp4j itself reads them")
    void bareLfHeaders(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("lf.ts").toFile();
        byte[] body = publish(f, new JSONArray().put(diagnostic(2, 2, "lf", null))).getBytes(StandardCharsets.UTF_8);
        byte[] stream = concat(("Content-Length: " + body.length + "\n\n").getBytes(StandardCharsets.US_ASCII), body);
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "lf");
        assertThat(readN(tap, stream.length, 5)).isEqualTo(stream);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:lf")).extracting(DiagnosticsBus.Problem::line).containsExactly(3);
        tap.close();
    }

    @Test
    @DisplayName("a method name with an escaped slash is still read")
    void escapedSlash(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("e.php").toFile();
        String json = publish(f, new JSONArray().put(diagnostic(0, 1, "php", null)))
                .replace("textDocument/publishDiagnostics", "textDocument\\/publishDiagnostics");
        assertThat(json).contains("\\/publish");
        byte[] stream = frame(json);
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "intelephense");
        readN(tap, stream.length, 64);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:intelephense")).extracting(DiagnosticsBus.Problem::message).containsExactly("php");
        tap.close();
    }

    @Test
    @DisplayName("a frame recorded after the server ended cannot bring its problems back")
    void endedTapRecordsNothing(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("late.ts").toFile();
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(new byte[0]), "late");
        tap.close();
        DiagnosticsTap.record(tap, "lsp:late", f.toURI().toString(),
                List.of(new DiagnosticsBus.Problem(f, 1, "ghost", true)));
        DiagnosticsTap.awaitIdle();
        assertThat(seen.getOrDefault("lsp:late", List.of())).isEmpty();
    }

    @Test
    @DisplayName("skipped bytes go through the parser too")
    void skipIsWatched(@TempDir Path tmp) throws IOException {
        File f = tmp.resolve("s.ts").toFile();
        byte[] stream = frame(publish(f, new JSONArray().put(diagnostic(0, 1, "skipped", null))));
        DiagnosticsTap tap = new DiagnosticsTap(new ByteArrayInputStream(stream), "skipper");
        assertThat(tap.skip(stream.length)).isEqualTo(stream.length);
        DiagnosticsTap.awaitIdle();
        assertThat(seen.get("lsp:skipper")).extracting(DiagnosticsBus.Problem::message).containsExactly("skipped");
        tap.close();
    }

    @Test
    @DisplayName("a server's name in Action Items drops the path, the extension and the -language-server tail")
    void toolNames() {
        assertThat(DiagnosticsTap.toolFor("/x/node_modules/.bin/typescript-language-server")).isEqualTo("lsp:typescript");
        assertThat(DiagnosticsTap.toolFor("C:\\npm\\vscode-eslint-language-server.cmd".replace('\\', File.separatorChar)))
                .isEqualTo("lsp:vscode-eslint");
        assertThat(DiagnosticsTap.toolFor("rust-analyzer")).isEqualTo("lsp:rust-analyzer");
        assertThat(DiagnosticsTap.toolFor("stylelint-lsp")).isEqualTo("lsp:stylelint");
        assertThat(DiagnosticsTap.fileOf("untitled:Untitled-1")).isNull();
    }

    @Test
    @DisplayName("the squiggle layer leaves language servers to the client, and every launch is tapped")
    void wiring() throws IOException {
        String squiggler = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/diagnostics/RackSquiggler.java"));
        assertThat(squiggler).contains("DiagnosticsTap.isLspTool(tool)");
        String servers = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"));
        assertThat(servers).contains("new DiagnosticsTap(process.getInputStream(), command.get(0))");
        assertThat(servers.split("LanguageServerDescription\\.create\\(", -1)).as("one launch seam").hasSize(2);
    }
}
