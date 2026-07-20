package org.nmox.studio.editor.debug.dap;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Debug Adapter Protocol wire format: {@code Content-Length: N\r\n\r\n}
 * followed by N bytes of UTF-8 JSON. One reader owns each stream — the
 * parser consumes exactly one frame per call and never buffers ahead, so
 * the stream stays valid for the next call.
 */
final class DapFrames {

    /**
     * The largest DAP frame we will buffer. Payloads carry
     * debuggee-derived data (console output, evaluated expressions,
     * variable dumps), so a debugged program doing
     * {@code console.log('x'.repeat(2e9))} makes the adapter emit a
     * multi-GB output event. {@code readNBytes(contentLength)} would
     * allocate it in one array → OutOfMemoryError takes down the IDE.
     * 64 MB is orders of magnitude past any real frame; oversize is a
     * protocol error that ends the session.
     */
    static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

    private DapFrames() {
    }

    /**
     * Reads one frame's JSON payload, or returns null on a clean EOF at a
     * frame boundary. EOF mid-frame is an error, not an end.
     */
    static String read(InputStream in) throws IOException {
        int contentLength = -1;
        while (true) {
            String line = readHeaderLine(in);
            if (line == null) {
                return null; // EOF before any header: peer closed cleanly
            }
            if (line.isEmpty()) {
                break; // blank line ends the header block
            }
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException malformed) {
                    // a garbage header is a protocol error, not a crash
                    throw new IOException("DAP frame with malformed Content-Length");
                }
            }
        }
        if (contentLength < 0) {
            throw new IOException("DAP frame without Content-Length header");
        }
        if (contentLength > MAX_FRAME_BYTES) {
            // refuse to allocate a debuggee-sized array (OOM DoS); ending
            // the session is the safe response to a runaway adapter frame
            throw new IOException("DAP frame over " + (MAX_FRAME_BYTES / (1024 * 1024))
                    + "MB (" + contentLength + " bytes) — ending session");
        }
        byte[] payload = in.readNBytes(contentLength);
        if (payload.length != contentLength) {
            throw new EOFException("DAP stream ended mid-frame ("
                    + payload.length + " of " + contentLength + " bytes)");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Writes one frame and flushes. Callers serialize access per stream. */
    static void write(OutputStream out, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + payload.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        out.write(header);
        out.write(payload);
        out.flush();
    }

    /**
     * One header line, byte-at-a-time — reading ahead would steal payload
     * bytes from the frame that follows. Returns null on EOF before any
     * byte; tolerates bare-LF line ends.
     */
    private static String readHeaderLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(32);
        int b = in.read();
        if (b < 0) {
            return null;
        }
        while (b >= 0 && b != '\n') {
            if (b != '\r') {
                sb.append((char) b);
            }
            b = in.read();
        }
        if (b < 0 && sb.length() > 0) {
            throw new EOFException("DAP stream ended mid-header");
        }
        return sb.toString();
    }
}
