package org.nmox.studio.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.util.Threads;
import org.openide.util.NbBundle;

/**
 * The one capped HTTP-body read (ledger 56). The unbounded-{@code ofString}
 * bug was fixed across seven sites in four releases, each inlining the same
 * mechanics: read at most a cap from an {@code ofInputStream} body, probe one
 * byte to learn whether the cap bit, decode. This class owns those mechanics;
 * the POLICY for a truncated body — flag it (API Studio), refuse it
 * (JSON-RPC, CouchDB), or shrug (display-only consoles) — deliberately stays
 * at each call site, because that is where the seven genuinely differ.
 *
 * <p><b>Bounded in time as well as size (3.4).</b> {@code java.net.http}'s
 * request timeout ends when the HEADERS arrive: a server that sends its
 * headers and then stops sending the body held every caller's read forever
 * (measured: every client still blocked at 90 s — KVASIR's lane and every
 * device queued behind it, CouchDB's Cancel, Contract Studio's Watch, a
 * thread per update check). So every read takes a deadline for the WHOLE
 * body, and when it expires a watchdog CLOSES the stream: a blocked read on
 * an {@code ofInputStream} body (or a socket) wakes only when its stream is
 * closed. The read then refuses by name with {@link StalledException}
 * ("the server stopped sending after N bytes"), never a silently short body.
 * Each caller passes its own deadline because each knows what its server
 * is — there is deliberately no overload without one.
 *
 * <p>The caller keeps the stream in its own try-with-resources: closing an
 * {@code ofInputStream} body aborts the rest of the transfer, and that
 * close belongs next to the {@code send()} it balances.
 */
public final class HttpBodies {

    /** The house ceiling for API-shaped responses (~8 MB): orders of
     *  magnitude past any legitimate payload the callers parse. */
    public static final int DEFAULT_CAP_BYTES = 8 * 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(HttpBodies.class.getName());

    /** One daemon thread fires every deadline; an alarm is a close, which is
     *  cheap and non-blocking on every stream the callers read. */
    private static final ScheduledExecutorService WATCHDOG = watchdog();

    /** A cancelled alarm leaves the queue at once: a poller reading every two seconds must not pile up a minute of dead alarms, each holding its stream. */
    private static ScheduledExecutorService watchdog() {
        java.util.concurrent.ScheduledThreadPoolExecutor ex = new java.util.concurrent.ScheduledThreadPoolExecutor(1,
                r -> Threads.daemon(r, "nmox-http-body-deadline"));
        ex.setRemoveOnCancelPolicy(true);
        return ex;
    }

    private HttpBodies() {
    }

    /** A capped read: the decoded text, the byte count actually read, and
     *  whether the source had more (the cap bit). */
    public record Capped(String text, int byteLength, boolean truncated) {
    }

    /**
     * The body stopped arriving before the read's deadline: the stream was
     * closed so the caller's thread is free, and what arrived is discarded
     * rather than handed on as if it were the whole body. The message is the
     * refusal a person reads, in their language.
     */
    public static final class StalledException extends IOException {

        private static final long serialVersionUID = 1L;

        private final long bytesReceived;
        private final transient Duration deadline;

        public StalledException(long bytesReceived, Duration deadline) {
            super(message(bytesReceived, deadline));
            this.bytesReceived = bytesReceived;
            this.deadline = deadline;
        }

        /** How many body bytes arrived before the server went quiet. */
        public long bytesReceived() {
            return bytesReceived;
        }

        /** The whole-body deadline that expired. */
        public Duration deadline() {
            return deadline;
        }

        private static String message(long bytes, Duration deadline) {
            return NbBundle.getMessage(HttpBodies.class, "HttpBodies.stalled",
                    bytes, Math.max(1, deadline.toSeconds()));
        }
    }

    /**
     * The body broke off mid-transfer: the headers (and a status) had
     * arrived, then the connection closed or reset before the body was
     * whole. {@code java.net.http} surfaces this as a bare
     * {@code IOException("closed")} with the real cause two levels down
     * ("fixed content-length: 1000, bytes received: 500"), which every
     * caller used to show verbatim — API Studio's "No route — closed" for a
     * server that had answered 200 (3.4). The message says what happened.
     */
    public static final class BrokenBodyException extends IOException {

        private static final long serialVersionUID = 1L;
        private static final java.util.regex.Pattern CONTENT_LENGTH =
                java.util.regex.Pattern.compile(
                        "content-length: (\\d{1,18}), bytes received: (\\d{1,18})");

        private final long bytesReceived;
        private final long bytesExpected;

        public BrokenBodyException(long bytesReceived, long bytesExpected, Throwable cause) {
            super(bytesExpected >= 0
                    ? NbBundle.getMessage(HttpBodies.class, "HttpBodies.brokenOf",
                            bytesReceived, bytesExpected)
                    : NbBundle.getMessage(HttpBodies.class, "HttpBodies.broken", bytesReceived),
                    cause);
            this.bytesReceived = bytesReceived;
            this.bytesExpected = bytesExpected;
        }

        /** Body bytes that arrived before the break. */
        public long bytesReceived() {
            return bytesReceived;
        }

        /** The declared Content-Length, or -1 when the server declared none. */
        public long bytesExpected() {
            return bytesExpected;
        }

        /** Reads the declared length out of the JDK's cause chain when it is there. */
        static BrokenBodyException from(IOException failed, long counted) {
            int depth = 0;
            for (Throwable t = failed; t != null && depth < 8; t = t.getCause(), depth++) {
                String m = t.getMessage();
                if (m == null) {
                    continue;
                }
                java.util.regex.Matcher match = CONTENT_LENGTH.matcher(m);
                if (match.find()) {
                    return new BrokenBodyException(Long.parseLong(match.group(2)),
                            Long.parseLong(match.group(1)), failed);
                }
            }
            return new BrokenBodyException(counted, -1, failed);
        }
    }

    /**
     * Reads at most {@code capBytes}, probes one more byte for truncation,
     * decodes with {@code charset} — all within {@code deadline}. Never reads
     * past cap+1 bytes, so a gigabyte stream costs the cap, not the stream;
     * never waits past the deadline, so a stalled server costs the deadline,
     * not the thread.
     *
     * @throws StalledException when the deadline expired before the body
     *         ended; the stream has been closed
     * @throws BrokenBodyException when the transfer broke off mid-body
     */
    public static Capped read(InputStream in, int capBytes, Charset charset, Duration deadline)
            throws IOException {
        Objects.requireNonNull(deadline, "every body read carries a deadline");
        Watch watch = new Watch(in);
        ScheduledFuture<?> alarm = WATCHDOG.schedule(watch::expire,
                Math.max(1, deadline.toMillis()), TimeUnit.MILLISECONDS);
        try {
            byte[] buf = new byte[Math.min(Math.max(capBytes, 0), 8192)];
            int count = 0;
            boolean eof = false;
            while (count < capBytes) {
                if (count == buf.length) {
                    buf = Arrays.copyOf(buf, (int) Math.min((long) capBytes, buf.length * 2L));
                }
                int n = in.read(buf, count, buf.length - count);
                if (n < 0) {
                    eof = true;
                    break;
                }
                count += n;
                watch.received = count;
            }
            boolean truncated = !eof && count == capBytes && in.read() != -1;
            if (watch.expired) {
                // the alarm closed the stream: some streams answer a close
                // with EOF rather than an exception, and a body cut short by
                // our own close is not the body
                throw new StalledException(count, deadline);
            }
            return new Capped(new String(buf, 0, count, charset), count, truncated);
        } catch (StalledException stalled) {
            throw stalled;
        } catch (IOException failed) {
            if (watch.expired) {
                throw new StalledException(watch.received, deadline);
            }
            throw BrokenBodyException.from(failed, watch.received);
        } finally {
            alarm.cancel(false);
        }
    }

    /** {@link #read} with UTF-8 — what every JSON-speaking caller wants. */
    public static Capped readUtf8(InputStream in, int capBytes, Duration deadline)
            throws IOException {
        return read(in, capBytes, StandardCharsets.UTF_8, deadline);
    }

    /** The alarm for one read: closing the stream is what frees the reader. */
    private static final class Watch {

        private final InputStream in;
        volatile boolean expired;
        volatile long received;

        Watch(InputStream in) {
            this.in = in;
        }

        void expire() {
            expired = true;
            try {
                in.close();
            } catch (IOException | RuntimeException closeFailed) {
                LOG.log(Level.FINE, "closing a stalled HTTP body", closeFailed);
            }
        }
    }
}
