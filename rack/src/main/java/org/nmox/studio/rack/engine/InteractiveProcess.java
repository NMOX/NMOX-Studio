package org.nmox.studio.rack.engine;

import org.nmox.studio.core.util.Threads;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.nmox.studio.core.process.ProcessSupport;

/**
 * A long-lived child process you type INTO — the opposite of
 * {@link CommandExecutor}, which deliberately runs with an empty stdin
 * so a prompt can never hang it. A REPL is nothing but a prompt, so
 * this keeps stdin open: {@link #send} writes a line and flushes, the
 * two output streams pump to a line callback, and exit fires once.
 *
 * <p>PATH augmentation and the no-color/non-interactive environment
 * come from {@link ProcessSupport}; only the stdin wiring differs.
 */
public final class InteractiveProcess {

    /**
     * A rung of {@link #stop()}'s shutdown ladder, recorded in the order it
     * is taken. The escalation is the law — a child that traps TERM must be
     * force-killed rather than waited on forever — and this makes that law
     * an OUTCOME a test can read instead of a duration it has to time. An
     * elapsed-time assertion here measures the machine's scheduler, not this
     * class: the wall-clock version of the escalation test was seen at
     * 7,449 ms against a 5,000 ms bound on a loaded box and at 3,188 ms
     * alone minutes later, with the product behaving identically both times.
     */
    enum Rung {
        /** stdin closed — most REPLs quit on end-of-input. */
        EOF,
        /** SIGTERM to the tree — the child did not take the EOF. */
        TERM,
        /** SIGKILL to the tree — the child did not take the TERM either. */
        KILL
    }

    /** How long a REPL gets to quit on end-of-input before the TERM. */
    static final long EOF_GRACE_MS = 500;

    /** How long it then gets to honour the TERM before the KILL. */
    static final long TERM_GRACE_MS = 1_500;

    private final Process process;
    private final Writer stdin;
    private volatile boolean finished;
    private final List<Rung> rungs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile long eofGraceMs = EOF_GRACE_MS;
    private volatile long termGraceMs = TERM_GRACE_MS;

    private InteractiveProcess(Process process) {
        this.process = process;
        this.stdin = new java.io.OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Launches {@code command} in {@code dir}, streaming stdout and
     * stderr to {@code onOut}/{@code onErr} line by line and calling
     * {@code onExit} once with the exit code. Throws if the tool can't
     * start (missing binary, not executable) so the caller can show a
     * human message.
     */
    public static InteractiveProcess start(List<String> command, File dir,
            Consumer<String> onOut, Consumer<String> onErr, IntConsumer onExit)
            throws IOException {
        ProcessBuilder pb = ProcessSupport.builder(command)
                .redirectInput(ProcessBuilder.Redirect.PIPE);
        if (dir != null && dir.isDirectory()) {
            pb.directory(dir);
        }
        Process process = pb.start();
        InteractiveProcess session = new InteractiveProcess(process);
        session.pump(process.getInputStream(), onOut);
        session.pump(process.getErrorStream(), onErr);
        Thread waiter = Threads.daemon(() -> {
            int code;
            try {
                code = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            session.finished = true;
            onExit.accept(code);
        }, "nmox-repl-wait");
        waiter.start();
        return session;
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(InteractiveProcess.class.getName());

    private void pump(InputStream stream, Consumer<String> onLine) {
        Thread t = Threads.daemon(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        // REPLs color their banners (elm repl paints its
                        // greeting) - same scrub the command lanes get
                        onLine.accept(CommandExecutor.stripAnsi(line));
                    } catch (RuntimeException ex) {
                        // a throwing consumer must not kill the pump: with no
                        // reader the pipe fills, the interpreter blocks on
                        // write, and the REPL hangs with no diagnostic
                        LOGGER.log(java.util.logging.Level.WARNING,
                                "REPL line consumer failed; pump continues", ex);
                    }
                }
            } catch (IOException closed) {
                // the process ended and the pipe closed; the waiter reports exit
            }
        }, "nmox-repl-pump");
        t.start();
    }

    /** Writes one line to the REPL's stdin (a newline is appended). */
    public synchronized void send(String line) {
        if (finished) {
            return;
        }
        try {
            stdin.write(line);
            stdin.write('\n');
            stdin.flush();
        } catch (IOException ex) {
            // the REPL died between the liveness check and the write
        }
    }

    /** True while the child is still running. */
    public boolean isAlive() {
        return !finished && process.isAlive();
    }

    /**
     * Sends EOF then, failing a graceful goodbye, kills the tree — TERM
     * first, then KILL after a bounded grace, mirroring
     * CommandExecutor.killAndWait's ladder so a TERM-trapping REPL cannot
     * survive the shutdown reaper as an orphan. Called from panic() at JVM
     * shutdown, so it stays synchronous and bounded — every wait is a TIMED
     * {@code waitFor}, worst case {@link #EOF_GRACE_MS} +
     * {@link #TERM_GRACE_MS}. Idempotent — safe to call from dispose and the
     * shutdown reaper. The ladder it walked is readable afterwards through
     * {@link #rungsTaken()}.
     */
    public void stop() {
        if (finished) {
            return;
        }
        rungs.add(Rung.EOF);
        try {
            OutputStream out = process.getOutputStream();
            out.close(); // EOF: most REPLs quit on end-of-input
        } catch (IOException ignored) {
            // already gone
        }
        try {
            if (!process.waitFor(eofGraceMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                rungs.add(Rung.TERM);
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(termGraceMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    // shutdown hooks get no second chance: escalate to KILL
                    rungs.add(Rung.KILL);
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            rungs.add(Rung.KILL);
            process.destroyForcibly();
        }
    }

    /**
     * The rungs {@link #stop()} has taken, in order — the escalation read as
     * an outcome. Package-visible for the tests that hold the ladder's law;
     * shipping code never asks.
     */
    List<Rung> rungsTaken() {
        return List.copyOf(rungs);
    }

    /**
     * Shortens or lengthens this session's two graces. Package-visible and
     * never called by shipping code, which always gets {@link #EOF_GRACE_MS}
     * and {@link #TERM_GRACE_MS}: it exists so a test can reach a rung
     * without racing a scheduler — a long EOF grace makes "the child took
     * the EOF" unraceable, a short one makes "the child ignored it" quick.
     * The state is per session, so there is nothing to restore.
     */
    void gracesForTest(long eofMs, long termMs) {
        this.eofGraceMs = eofMs;
        this.termGraceMs = termMs;
    }
}
