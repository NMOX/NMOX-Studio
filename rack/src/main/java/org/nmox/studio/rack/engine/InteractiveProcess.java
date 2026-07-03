package org.nmox.studio.rack.engine;

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

    private final Process process;
    private final Writer stdin;
    private volatile boolean finished;

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
        Thread waiter = new Thread(() -> {
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
        waiter.setDaemon(true);
        waiter.start();
        return session;
    }

    private void pump(InputStream stream, Consumer<String> onLine) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            } catch (IOException closed) {
                // the process ended and the pipe closed; the waiter reports exit
            }
        }, "nmox-repl-pump");
        t.setDaemon(true);
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
     * Sends EOF then, failing a graceful goodbye, kills the tree.
     * Idempotent — safe to call from dispose and the shutdown reaper.
     */
    public void stop() {
        if (finished) {
            return;
        }
        try {
            OutputStream out = process.getOutputStream();
            out.close(); // EOF: most REPLs quit on end-of-input
        } catch (IOException ignored) {
            // already gone
        }
        try {
            if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
