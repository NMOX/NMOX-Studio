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

    /**
     * One daemon thread fires every deadline. It never closes a stream
     * itself: closing the JDK web server's request stream DRAINS it, which
     * waits on the very reader it means to free, and the one watchdog stuck
     * there stopped every deadline in the IDE (the 3.4 review: a stalled
     * Agent Port client). The close runs on a thread of its own.
     */
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
            this(bytesReceived, deadline, false);
        }

        StalledException(long bytesReceived, Duration deadline, boolean stillArriving) {
            super(stillArriving ? tooSlowMessage(bytesReceived, deadline) : message(bytesReceived, deadline));
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

        /** Bytes were still arriving when the ceiling ran out: the server did not stop, it was too slow. */
        private static String tooSlowMessage(long bytes, Duration deadline) {
            return NbBundle.getMessage(HttpBodies.class, "HttpBodies.tooSlow",
                    bytes, Math.max(1, deadline.toSeconds() * CEILING_FACTOR));
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
        Watch watch = new Watch(in, Math.max(1, deadline.toMillis()));
        watch.arm();
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
                watch.arrived(count);
            }
            boolean truncated = !eof && count == capBytes && in.read() != -1;
            if (watch.expired) {
                // the alarm closed the stream: some streams answer a close
                // with EOF rather than an exception, and a body cut short by
                // our own close is not the body
                throw new StalledException(count, deadline, watch.tooSlow);
            }
            return new Capped(new String(buf, 0, count, charset), count, truncated);
        } catch (StalledException stalled) {
            throw stalled;
        } catch (IOException failed) {
            if (watch.expired) {
                throw new StalledException(watch.received, deadline, watch.tooSlow);
            }
            if (Thread.currentThread().isInterrupted() || interrupted(failed)) {
                // a Cancel interrupts the reading worker and the JDK's body
                // stream answers with an IOException: that is the user's
                // verdict, not the server's broken body (the 3.4 review)
                java.io.InterruptedIOException cancelled = new java.io.InterruptedIOException("cancelled");
                cancelled.initCause(failed);
                throw cancelled;
            }
            throw BrokenBodyException.from(failed, watch.received);
        } finally {
            watch.disarm();
        }
    }

    /** {@link #read} with UTF-8 — what every JSON-speaking caller wants. */
    public static Capped readUtf8(InputStream in, int capBytes, Duration deadline)
            throws IOException {
        return read(in, capBytes, StandardCharsets.UTF_8, deadline);
    }

    /**
     * How many deadlines a body that keeps ARRIVING may take in all. The
     * deadline is an idle bound: it re-arms whenever bytes arrive, so a slow
     * but live download is not refused as "stopped" (the 3.4 review: at the
     * old whole-body reading, an 8 MB answer over a slow link was thrown away
     * with a sentence that said the server had gone quiet). A server that
     * trickles a byte just inside every deadline still ends here.
     */
    static final int CEILING_FACTOR = 4;

    private static boolean interrupted(Throwable failed) {
        for (Throwable t = failed; t != null; t = t.getCause()) {
            if (t instanceof InterruptedException || t instanceof java.io.InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    /** What the watchdog decides at one look at a read. */
    enum Look { WAIT, STOPPED, TOO_SLOW }

    /** One look's verdict; {@code waitNanos} is how long until the next look, for {@link Look#WAIT}. */
    record Verdict(Look look, long waitNanos) {
    }

    /**
     * The watchdog's whole decision, pure: no bytes for an idle period is
     * STOPPED, whichever side of the ceiling it happens on; bytes within
     * the idle period but the ceiling passed is TOO_SLOW; otherwise the next
     * look is when the first of the two could become true.
     *
     * <p>3.4.0 asked "did the byte count move since my last look?" instead
     * of "when did the last byte arrive?". The last look before the ceiling
     * can be a short one (the wait is clipped to the time left, and a late
     * timer shortens it further), and a body trickling steadily had usually
     * delivered nothing inside that sliver - so a server that was too slow
     * was said to have "stopped sending". Found as a one-in-many failure on
     * a loaded CI runner (3.4.1). The same question also let a silence run
     * to nearly two idle periods before it was noticed.
     */
    static Verdict look(long now, long lastByteAt, long ceilingAt, long idleNanos) {
        long idleFor = now - lastByteAt;
        if (idleFor >= idleNanos) {
            return new Verdict(Look.STOPPED, 0);
        }
        if (now >= ceilingAt) {
            return new Verdict(Look.TOO_SLOW, 0);
        }
        return new Verdict(Look.WAIT, Math.min(idleNanos - idleFor, ceilingAt - now));
    }

    /** The alarm for one read: closing the stream is what frees the reader. */
    private static final class Watch {

        private final InputStream in;
        private final long idleNanos;
        private final long ceilingAt;
        volatile boolean expired;
        volatile boolean tooSlow;
        volatile long received;
        private volatile long lastByteAt;
        private ScheduledFuture<?> alarm;
        private boolean done;

        Watch(InputStream in, long idleMillis) {
            this.in = in;
            this.idleNanos = TimeUnit.MILLISECONDS.toNanos(idleMillis);
            this.lastByteAt = System.nanoTime();
            this.ceilingAt = lastByteAt + idleNanos * CEILING_FACTOR;
        }

        /** The reader: {@code count} bytes are in, the last of them just now. */
        void arrived(long count) {
            received = count;
            lastByteAt = System.nanoTime();
        }

        synchronized void arm() {
            schedule(idleNanos);
        }

        private synchronized void schedule(long nanos) {
            if (!done) {
                // rounded UP: a look that fires a fraction early finds the
                // idle period not quite over and has to look again
                long millis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos + 999_999));
                alarm = WATCHDOG.schedule(this::check, millis, TimeUnit.MILLISECONDS);
            }
        }

        synchronized void disarm() {
            done = true;
            if (alarm != null) {
                alarm.cancel(false);
            }
        }

        /** On the watchdog: look again later while bytes keep arriving inside the ceiling, else expire. */
        private synchronized void check() {
            if (done) {
                return;
            }
            Verdict verdict = look(System.nanoTime(), lastByteAt, ceilingAt, idleNanos);
            if (verdict.look() == Look.WAIT) {
                schedule(verdict.waitNanos());
                return;
            }
            tooSlow = verdict.look() == Look.TOO_SLOW;
            expire();
        }

        private void expire() {
            expired = true;
            done = true;
            // never on the watchdog: a close can block (see WATCHDOG)
            Threads.startDaemon(() -> {
                try {
                    in.close();
                } catch (IOException | RuntimeException closeFailed) {
                    LOG.log(Level.FINE, "closing a stalled HTTP body", closeFailed);
                }
            }, "nmox-http-body-close");
        }
    }
}
