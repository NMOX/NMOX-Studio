package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * The ledger-81 cure: with tsserver AND ngserver both bound to
 * {@code text/typescript} in an Angular workspace, the platform's
 * rename refactoring collects BOTH servers' edit sets and applies both
 * — the class declaration renamed twice while the template renamed
 * once (proven live, {@code headingheading}). ngserver is the rename
 * authority there (it rewrites class AND template), so this filter
 * strips {@code renameProvider} from TSSERVER's initialize response,
 * making the platform route rename through ngserver alone.
 *
 * <p>Mechanically: an InputStream over the server's stdout that parses
 * LSP frames ({@code Content-Length: N\r\n\r\n{json}}) until it has
 * rewritten the first frame whose result carries {@code capabilities},
 * then degrades to a zero-copy passthrough. JSON surgery rides
 * org.json — string splicing on protocol traffic is how corruptions
 * are born.
 */
final class LspRenameCapabilityFilter extends InputStream {

    private final InputStream raw;
    private byte[] pending = new byte[0];
    private int pos;
    private boolean filtering = true;

    LspRenameCapabilityFilter(InputStream raw) {
        this.raw = raw;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (pos < pending.length) {
            int n = Math.min(len, pending.length - pos);
            System.arraycopy(pending, pos, b, off, n);
            pos += n;
            return n;
        }
        if (!filtering) {
            return raw.read(b, off, len);
        }
        byte[] frame = nextFrame();
        if (frame == null) {
            return -1;
        }
        pending = frame;
        pos = 0;
        return read(b, off, len);
    }

    /** One whole re-emitted frame (headers + body), or null at EOF. */
    private byte[] nextFrame() throws IOException {
        String headers = readHeaders();
        if (headers == null) {
            return null;
        }
        int contentLength = contentLengthOf(headers);
        byte[] body = raw.readNBytes(contentLength);
        if (body.length < contentLength) {
            // truncated stream: emit what we have verbatim and stop filtering
            filtering = false;
            return concat(headers, body);
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.contains("\"capabilities\"")) {
            byte[] rewritten = stripRenameProvider(text);
            filtering = false;
            return frame(rewritten);
        }
        return concat(headers, body);
    }

    /** Removes result.capabilities.renameProvider; body unchanged on any surprise. */
    static byte[] stripRenameProvider(String body) {
        try {
            JSONObject msg = new JSONObject(body);
            JSONObject result = msg.optJSONObject("result");
            JSONObject caps = result == null ? null : result.optJSONObject("capabilities");
            if (caps != null && caps.has("renameProvider")) {
                caps.remove("renameProvider");
                return msg.toString().getBytes(StandardCharsets.UTF_8);
            }
        } catch (RuntimeException notJson) {
            // a capabilities-shaped body that isn't the initialize result:
            // pass it through untouched rather than corrupt the protocol
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] frame(byte[] body) {
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] concat(String headers, byte[] body) {
        byte[] h = (headers + "\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[h.length + body.length];
        System.arraycopy(h, 0, out, 0, h.length);
        System.arraycopy(body, 0, out, h.length, body.length);
        return out;
    }

    /** Raw header block up to (not including) the blank line; null at EOF. */
    private String readHeaders() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0; // counts the \r\n\r\n terminator
        int c;
        while ((c = raw.read()) >= 0) {
            buf.write(c);
            state = switch (state) {
                case 0 -> c == '\r' ? 1 : 0;
                case 1 -> c == '\n' ? 2 : 0;
                case 2 -> c == '\r' ? 3 : 0;
                default -> c == '\n' ? 4 : 0;
            };
            if (state == 4) {
                String all = buf.toString(StandardCharsets.US_ASCII);
                return all.substring(0, all.length() - 2); // keep one \r\n
            }
        }
        return null;
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

    @Override
    public void close() throws IOException {
        raw.close();
    }
}
