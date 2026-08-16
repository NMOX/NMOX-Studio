package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * The initializationOptions shim, generalized (v2.14.0 from the
 * v1.350.0 Deno original): some servers publish NOTHING unless the
 * initialize request carries server-specific options — {@code deno lsp}
 * needs {@code enable:true}, and Vue's language server (the 2.x line)
 * needs {@code typescript.tsdk} plus {@code vue.hybridMode:false},
 * both proven headlessly against the real binaries (empty options:
 * silence on planted errors; with them: the real diagnostics). The
 * platform's lsp-client never sets initializationOptions and offers no
 * seam for it, so this filter rewrites the client's FIRST outgoing
 * frame — the initialize request, always first by the LSP spec — to
 * merge the options in, then becomes a byte passthrough.
 *
 * <p>Threading: unlike the server→client direction (which needed a
 * pump thread — see the v1.349.0 history), the client side can buffer
 * inline. lsp4j's writer thread hands us header+body bytes in
 * arbitrary chunks; we hold them only until the first frame is
 * complete, then emit the rewritten frame plus any excess. Nothing
 * blocks waiting for another thread, so no pipe and no pump.
 */
final class InitOptionsInjector extends FilterOutputStream {

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final JSONObject options;
    private boolean injecting = true;

    /**
     * @param options top-level keys merged into
     *        {@code params.initializationOptions}; each key REPLACES a
     *        same-named existing one (the client sends none today, so
     *        in practice this authors the whole object)
     */
    InitOptionsInjector(OutputStream rawServerIn, JSONObject options) {
        super(rawServerIn);
        this.options = options;
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
        byte[] rewritten = mergeOptions(body, options);
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
     * Merges {@code options}' top-level keys into
     * {@code params.initializationOptions}, preserving whatever else
     * the body carries. A body that isn't the JSON we expect goes
     * through untouched — corrupting the protocol is strictly worse
     * than a disabled server.
     */
    static byte[] mergeOptions(String body, JSONObject options) {
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
            JSONObject existing = params.optJSONObject("initializationOptions");
            if (existing == null) {
                existing = new JSONObject();
                params.put("initializationOptions", existing);
            }
            for (String key : options.keySet()) {
                existing.put(key, options.get(key));
            }
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
