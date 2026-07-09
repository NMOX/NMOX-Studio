package org.nmox.studio.editor.debug.dap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DapFramesTest {

    @Test
    @DisplayName("write then read round-trips a frame")
    void shouldRoundTrip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DapFrames.write(out, "{\"seq\":1}");
        String read = DapFrames.read(new ByteArrayInputStream(out.toByteArray()));
        assertThat(read).isEqualTo("{\"seq\":1}");
    }

    @Test
    @DisplayName("consecutive frames parse one at a time without over-reading")
    void shouldReadBackToBackFrames() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DapFrames.write(out, "{\"a\":1}");
        DapFrames.write(out, "{\"b\":2}");
        InputStream in = new ByteArrayInputStream(out.toByteArray());
        assertThat(DapFrames.read(in)).isEqualTo("{\"a\":1}");
        assertThat(DapFrames.read(in)).isEqualTo("{\"b\":2}");
        assertThat(DapFrames.read(in)).isNull();
    }

    @Test
    @DisplayName("multibyte UTF-8 payload lengths are byte lengths, not char counts")
    void shouldHandleUtf8Payloads() throws IOException {
        String json = "{\"msg\":\"héllo wörld — ünïcode\"}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DapFrames.write(out, json);
        String header = out.toString(StandardCharsets.UTF_8);
        assertThat(header).startsWith(
                "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length);
        assertThat(DapFrames.read(new ByteArrayInputStream(out.toByteArray())))
                .isEqualTo(json);
    }

    @Test
    @DisplayName("extra headers are tolerated, Content-Length case-insensitive")
    void shouldTolerateExtraHeaders() throws IOException {
        byte[] raw = ("content-length: 7\r\nX-Other: x\r\n\r\n{\"a\":1}")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(DapFrames.read(new ByteArrayInputStream(raw))).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("clean EOF at a frame boundary reads as null; mid-frame EOF throws")
    void shouldDistinguishCleanFromDirtyEof() throws IOException {
        assertThat(DapFrames.read(new ByteArrayInputStream(new byte[0]))).isNull();
        byte[] truncated = "Content-Length: 100\r\n\r\n{\"partial\":true"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DapFrames.read(new ByteArrayInputStream(truncated)))
                .isInstanceOf(EOFException.class);
    }

    @Test
    @DisplayName("a large payload survives intact")
    void shouldHandleLargePayload() throws IOException {
        String json = "{\"data\":\"" + "x".repeat(300_000) + "\"}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DapFrames.write(out, json);
        assertThat(DapFrames.read(new ByteArrayInputStream(out.toByteArray())))
                .isEqualTo(json);
    }
}
