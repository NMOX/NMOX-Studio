package org.nmox.studio.editor.lsp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ledger-81 filter: tsserver's initialize response loses its
 * renameProvider in Angular workspaces so the platform routes rename
 * through ngserver alone — a wrong rewrite here corrupts the LSP
 * stream and kills ALL TypeScript intelligence, so every property is
 * pinned at the byte level.
 */
class LspRenameCapabilityFilterTest {

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    /** Reads one frame off the filtered stream: [headers, body]. */
    private static String[] readFrame(InputStream in) throws Exception {
        StringBuilder head = new StringBuilder();
        int state = 0, c;
        while (state != 4 && (c = in.read()) >= 0) {
            head.append((char) c);
            state = switch (state) {
                case 0 -> c == '\r' ? 1 : 0;
                case 1 -> c == '\n' ? 2 : 0;
                case 2 -> c == '\r' ? 3 : 0;
                default -> c == '\n' ? 4 : 0;
            };
        }
        int n = Integer.parseInt(head.toString().split(":")[1].trim());
        return new String[]{head.toString(),
            new String(in.readNBytes(n), StandardCharsets.UTF_8)};
    }

    private static final String INIT = """
            {"jsonrpc":"2.0","id":0,"result":{"capabilities":{\
            "renameProvider":true,"hoverProvider":true,\
            "definitionProvider":true}}}""";

    @Test
    @DisplayName("the initialize response loses renameProvider, Content-Length re-fixed, rest intact")
    void stripsRename() throws Exception {
        InputStream in = new LspRenameCapabilityFilter(
                new ByteArrayInputStream(frame(INIT)));
        String[] f = readFrame(in);
        JSONObject caps = new JSONObject(f[1])
                .getJSONObject("result").getJSONObject("capabilities");
        assertThat(caps.has("renameProvider")).isFalse();
        assertThat(caps.getBoolean("hoverProvider")).isTrue();
        assertThat(caps.getBoolean("definitionProvider")).isTrue();
        assertThat(f[1].getBytes(StandardCharsets.UTF_8).length)
                .as("the re-emitted Content-Length must match the new body")
                .isEqualTo(Integer.parseInt(f[0].split(":")[1].trim()));
        assertThat(in.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("frames before/after pass byte-identical; filtering stops after the rewrite")
    void passthrough() throws Exception {
        String notify = "{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\","
                + "\"params\":{\"message\":\"hi\"}}";
        String late = "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":"
                + "{\"capabilities\":{\"renameProvider\":true}}}";
        InputStream in = new LspRenameCapabilityFilter(new ByteArrayInputStream(
                concat(frame(notify), frame(INIT), frame(late))));
        assertThat(readFrame(in)[1]).isEqualTo(notify);
        assertThat(new JSONObject(readFrame(in)[1]).getJSONObject("result")
                .getJSONObject("capabilities").has("renameProvider")).isFalse();
        // AFTER the initialize rewrite the filter is a passthrough — a
        // later capabilities-shaped body keeps its renameProvider
        assertThat(readFrame(in)[1]).isEqualTo(late);
    }

    @Test
    @DisplayName("a capabilities-shaped body that isn't valid init JSON passes untouched")
    void hostileBody() {
        String odd = "{\"capabilities\": not json at all";
        assertThat(new String(LspRenameCapabilityFilter.stripRenameProvider(odd),
                StandardCharsets.UTF_8)).isEqualTo(odd);
        String noRename = "{\"result\":{\"capabilities\":{\"hoverProvider\":true}}}";
        assertThat(new String(LspRenameCapabilityFilter.stripRenameProvider(noRename),
                StandardCharsets.UTF_8)).isEqualTo(noRename);
    }

    @Test
    @DisplayName("the pipe outlives its reader threads — lsp4j reads from a POOL")
    void pipeSurvivesReaderThreadDeath() throws Exception {
        // PipedInputStream pins the last reader thread and kills the pipe
        // when that thread dies ("Pipe broken") — which is exactly what a
        // pool-fed lsp4j consumer does between messages. The hand-rolled
        // BytePipe must not care who reads or writes.
        LspRenameCapabilityFilter.BytePipe pipe = new LspRenameCapabilityFilter.BytePipe();
        pipe.put(new byte[]{'a', 'b'}, 0, 2);
        int[] first = new int[1];
        Thread reader1 = new Thread(() -> {
            try {
                first[0] = pipe.read();
            } catch (Exception e) {
                first[0] = -99;
            }
        });
        reader1.start();
        reader1.join();
        assertThat(first[0]).isEqualTo('a');
        // the first reader thread is DEAD; writes and reads must go on
        pipe.put(new byte[]{'c'}, 0, 1);
        assertThat(pipe.read()).isEqualTo('b');
        assertThat(pipe.read()).isEqualTo('c');
        pipe.closeFeed();
        assertThat(pipe.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("wiring gate: tsserver launches filtered ONLY in Angular workspaces")
    void wiringGate() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"));
        int ts = src.indexOf("class TypeScriptServer");
        int end = src.indexOf("class EslintServer");
        String body = src.substring(ts, end);
        assertThat(body)
                .as("the filter must key on the Angular workspace, not run globally")
                .contains("angularRootAbove(projectDir(lookup)) != null");
        assertThat(body).contains("launchNpm(lookup, angular,");
        assertThat(src)
                .as("launch() must actually wrap the stream when asked")
                .contains("new LspRenameCapabilityFilter(serverOut)");
    }
}
