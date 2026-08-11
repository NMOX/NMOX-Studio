package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
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
 * <p><b>Threading — the shape that matters.</b> The first cut of this
 * class assembled whole LSP frames ON THE CALLER'S THREAD, and the
 * caller is lsp4j's reader — which the platform blocks behind the
 * global {@code LSPBindings} class lock during initialize. One stall
 * in that arrangement froze the EDT of the whole IDE (observed live:
 * jstack showed focusGained → didOpen → getBindings BLOCKED behind
 * initServer's timedGet). This version is the DapProxy idiom instead:
 * a daemon pump thread parses frames at its own pace and writes the
 * (possibly rewritten) bytes into a pipe; consumers read the pipe with
 * ordinary partial-read semantics and can never be held hostage to
 * frame assembly.
 *
 * <p><b>Why the pipe is hand-rolled.</b> {@code PipedInputStream} pins
 * thread IDENTITIES: it remembers the last reader thread and the
 * writer throws "Pipe broken" the moment that thread is no longer
 * alive. lsp4j reads server output from an executor POOL whose threads
 * retire between messages, so a JDK pipe dies mid-session (observed
 * live: pump gone, tsserver deaf). {@link BytePipe} is the same
 * bounded producer/consumer contract with no liveness checks.
 */
final class LspRenameCapabilityFilter extends FilterInputStream {

    LspRenameCapabilityFilter(InputStream raw) {
        super(openPumped(raw));
    }

    private static InputStream openPumped(InputStream raw) {
        BytePipe pipe = new BytePipe();
        Thread pump = new Thread(() -> pump(raw, pipe), "nmox-lsp-rename-filter");
        pump.setDaemon(true);
        pump.start();
        return pipe;
    }

    private static void pump(InputStream raw, BytePipe feed) {
        boolean filtering = true;
        try {
            while (true) {
                if (!filtering) {
                    // zero-parse passthrough for the rest of the session
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = raw.read(buf)) >= 0) {
                        feed.put(buf, 0, n);
                    }
                    return;
                }
                String headers = readHeaders(raw);
                if (headers == null) {
                    return; // EOF
                }
                byte[] body = raw.readNBytes(contentLengthOf(headers));
                String text = new String(body, StandardCharsets.UTF_8);
                if (text.contains("\"capabilities\"")) {
                    byte[] rewritten = stripRenameProvider(text);
                    byte[] header = ("Content-Length: " + rewritten.length + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII);
                    feed.put(header, 0, header.length);
                    feed.put(rewritten, 0, rewritten.length);
                    filtering = false;
                } else {
                    byte[] header = (headers + "\r\n").getBytes(StandardCharsets.US_ASCII);
                    feed.put(header, 0, header.length);
                    feed.put(body, 0, body.length);
                }
            }
        } catch (IOException done) {
            // consumer closed or stream died: the pump's job is over
        } finally {
            feed.closeFeed();
        }
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

    /** Raw header block up to (not including) the blank line; null at EOF. */
    private static String readHeaders(InputStream raw) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
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

    /**
     * A bounded byte pipe with NO thread-identity coupling: any thread
     * may write, any thread may read, at any time. Closing the read
     * side makes the writer throw (so the pump exits); closing the
     * write side drains the buffer then reports EOF.
     */
    static final class BytePipe extends InputStream {

        private final byte[] buf = new byte[64 * 1024];
        private int head;      // next read index
        private int count;     // bytes buffered
        private boolean feedClosed;
        private boolean readClosed;

        synchronized void put(byte[] src, int off, int len) throws IOException {
            int remaining = len;
            int at = off;
            while (remaining > 0) {
                while (count == buf.length && !readClosed) {
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", ie);
                    }
                }
                if (readClosed) {
                    throw new IOException("read side closed");
                }
                int chunk = Math.min(remaining, buf.length - count);
                for (int i = 0; i < chunk; i++) {
                    buf[(head + count + i) % buf.length] = src[at + i];
                }
                count += chunk;
                at += chunk;
                remaining -= chunk;
                notifyAll();
            }
        }

        synchronized void closeFeed() {
            feedClosed = true;
            notifyAll();
        }

        @Override
        public synchronized int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public synchronized int read(byte[] dst, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            while (count == 0) {
                if (feedClosed || readClosed) {
                    return -1;
                }
                try {
                    wait();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", ie);
                }
            }
            int chunk = Math.min(len, count);
            for (int i = 0; i < chunk; i++) {
                dst[off + i] = buf[(head + i) % buf.length];
            }
            head = (head + chunk) % buf.length;
            count -= chunk;
            notifyAll();
            return chunk;
        }

        @Override
        public synchronized int available() {
            return count;
        }

        @Override
        public synchronized void close() {
            readClosed = true;
            notifyAll();
        }
    }
}
