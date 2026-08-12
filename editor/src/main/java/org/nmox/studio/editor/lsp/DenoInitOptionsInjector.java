package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * The Deno LSP enablement shim: {@code deno lsp} boots, finds the
 * workspace's deno.json, and then publishes NO diagnostics unless the
 * initialize request carried {@code initializationOptions.enable=true}
 * — proven headlessly against deno 2.9.4 (empty options: silence on a
 * planted type error; enable=true: the real deno-ts TS2322). The
 * platform's lsp-client never sets initializationOptions and offers no
 * seam for it, so this filter rewrites the client's FIRST outgoing
 * frame — the initialize request, always first by the LSP spec — to
 * add the option, then becomes a byte passthrough for the session.
 *
 * <p>Threading: unlike the server→client direction (which needed a
 * pump thread — see the v1.349.0 history), the client side can buffer
 * inline. lsp4j's writer thread hands us header+body bytes in
 * arbitrary chunks; we hold them only until the first frame is
 * complete, then emit the rewritten frame plus any excess. Nothing
 * blocks waiting for another thread, so no pipe and no pump.
 */
final class DenoInitOptionsInjector extends FilterOutputStream {

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean injecting = true;

    DenoInitOptionsInjector(OutputStream rawServerIn) {
        super(rawServerIn);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (!injecting) {
            out.write(b, off, len);
            return;
        }
        pending.write(b, off, len);
        byte[] buf = pending.toByteArray();
        int headerEnd = indexOfBlankLine(buf);
        if (headerEnd < 0) {
            return; // headers incomplete
        }
        int bodyLen = contentLengthOf(new String(buf, 0, headerEnd, StandardCharsets.US_ASCII));
        int frameEnd = headerEnd + 4 + bodyLen;
        if (buf.length < frameEnd) {
            return; // body incomplete
        }
        String body = new String(buf, headerEnd + 4, bodyLen, StandardCharsets.UTF_8);
        byte[] rewritten = addEnableOption(body);
        out.write(("Content-Length: " + rewritten.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(rewritten);
        if (buf.length > frameEnd) {
            out.write(buf, frameEnd, buf.length - frameEnd);
        }
        out.flush();
        injecting = false;
        pending.reset();
    }

    /**
     * params.initializationOptions.enable = true, preserving whatever
     * else the body carries. A body that isn't the JSON we expect goes
     * through untouched — corrupting the protocol is strictly worse
     * than a disabled server.
     */
    static byte[] addEnableOption(String body) {
        try {
            JSONObject msg = new JSONObject(body);
            if (!"initialize".equals(msg.optString("method"))) {
                return body.getBytes(StandardCharsets.UTF_8);
            }
            JSONObject params = msg.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
                msg.put("params", params);
            }
            JSONObject options = params.optJSONObject("initializationOptions");
            if (options == null) {
                options = new JSONObject();
                params.put("initializationOptions", options);
            }
            options.put("enable", true);
            return msg.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException notJson) {
            return body.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static int indexOfBlankLine(byte[] buf) {
        for (int i = 0; i + 3 < buf.length; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n'
                    && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int contentLengthOf(String headers) throws IOException {
        for (String line : headers.split("\r\n")) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                try {
                    return Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException bad) {
                    throw new IOException("Bad Content-Length: " + line);
                }
            }
        }
        throw new IOException("Frame without Content-Length");
    }
}
