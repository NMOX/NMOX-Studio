package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deno lsp enablement shim: the client's FIRST frame (the
 * initialize request) gains {@code initializationOptions.enable=true};
 * everything after is byte-identical passthrough. Getting a byte of
 * this wrong corrupts the whole LSP session, so the frame mechanics
 * are pinned exactly.
 */
class DenoInitOptionsInjectorTest {

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static final String INIT =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"processId\":42,\"rootUri\":\"file:///w\"}}";

    /** Splits captured bytes into [header, body] of the first frame. */
    private static String[] firstFrame(byte[] captured) {
        String all = new String(captured, StandardCharsets.UTF_8);
        int split = all.indexOf("\r\n\r\n");
        String header = all.substring(0, split);
        int len = Integer.parseInt(header.split(":")[1].trim());
        int bodyStart = split + 4;
        return new String[]{header, all.substring(bodyStart, bodyStart + len)};
    }

    @Test
    @DisplayName("the initialize request gains enable=true, Content-Length re-fixed")
    void injectsEnable() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DenoInitOptionsInjector out = new DenoInitOptionsInjector(sink)) {
            out.write(frame(INIT));
        }
        String[] f = firstFrame(sink.toByteArray());
        JSONObject msg = new JSONObject(f[1]);
        assertThat(msg.getJSONObject("params")
                .getJSONObject("initializationOptions").getBoolean("enable")).isTrue();
        assertThat(msg.getJSONObject("params").getInt("processId"))
                .as("the rest of the params survive").isEqualTo(42);
        assertThat(f[1].getBytes(StandardCharsets.UTF_8).length)
                .as("the emitted Content-Length must match the new body")
                .isEqualTo(Integer.parseInt(f[0].split(":")[1].trim()));
    }

    @Test
    @DisplayName("one-byte-at-a-time writes still assemble and rewrite the frame")
    void chunkedWrites() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DenoInitOptionsInjector out = new DenoInitOptionsInjector(sink);
        for (byte b : frame(INIT)) {
            out.write(b);
        }
        JSONObject msg = new JSONObject(firstFrame(sink.toByteArray())[1]);
        assertThat(msg.getJSONObject("params")
                .getJSONObject("initializationOptions").getBoolean("enable")).isTrue();
    }

    @Test
    @DisplayName("frames after initialize pass through byte-identical")
    void passthroughAfter() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DenoInitOptionsInjector out = new DenoInitOptionsInjector(sink);
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\","
                + "\"params\":{\"textDocument\":{\"uri\":\"file:///w/a.ts\"}}}";
        // both frames in ONE write: the excess beyond the first frame
        // must flush verbatim, not get re-parsed
        byte[] both = new byte[frame(INIT).length + frame(didOpen).length];
        System.arraycopy(frame(INIT), 0, both, 0, frame(INIT).length);
        System.arraycopy(frame(didOpen), 0, both, frame(INIT).length, frame(didOpen).length);
        out.write(both);
        String all = new String(sink.toByteArray(), StandardCharsets.UTF_8);
        assertThat(all).endsWith(new String(frame(didOpen), StandardCharsets.UTF_8));
        // and a LATER initialize-shaped body is untouched (filter is spent)
        sink.reset();
        out.write(frame(INIT));
        assertThat(sink.toByteArray()).isEqualTo(frame(INIT));
    }

    @Test
    @DisplayName("a first frame that isn't initialize passes untouched")
    void nonInitializeFirstFrame() {
        String odd = "{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}";
        assertThat(new String(DenoInitOptionsInjector.addEnableOption(odd),
                StandardCharsets.UTF_8)).isEqualTo(odd);
        String notJson = "definitely { not json";
        assertThat(new String(DenoInitOptionsInjector.addEnableOption(notJson),
                StandardCharsets.UTF_8)).isEqualTo(notJson);
    }

    @Test
    @DisplayName("wiring gate: DenoServer launches deno lsp injected, tsserver yields")
    void wiringGate() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"))
                .replace("\r\n", "\n");
        int deno = src.indexOf("class DenoServer");
        assertThat(deno).as("DenoServer exists").isPositive();
        String body = src.substring(deno, src.indexOf("class", deno + 20));
        assertThat(body)
                .as("deno lsp launches with the init-options injector")
                .contains("launch(lookup, List.of(\"deno\", \"lsp\"), true)");
        assertThat(body).contains("denoRootAbove(projectDir(lookup))");
        int ts = src.indexOf("class TypeScriptServer");
        String tsBody = src.substring(ts, src.indexOf("class DenoServer"));
        assertThat(tsBody)
                .as("tsserver yields the mime in Deno workspaces — two "
                        + "servers on one mime double-apply (the v1.349.0 law)")
                .contains("denoRootAbove(projectDir(lookup)) != null");
        assertThat(src)
                .as("launch() actually wraps the stream when asked")
                .contains("new DenoInitOptionsInjector(serverIn)");
    }
}
