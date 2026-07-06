package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.process.ProcessSupport;

/**
 * A quiet helper for short status probes (current git branch, version
 * checks). Unlike CommandExecutor it opens no Output tab and is meant
 * for commands that finish in milliseconds. Runs are bounded: a wedged
 * probe (git waiting on a dead network mount) is killed after
 * {@link #TIMEOUT} instead of leaking a thread and a child process on
 * every project switch.
 */
final class CommandProbe {

    private static final Logger LOGGER = Logger.getLogger(CommandProbe.class.getName());

    /** Probes are `git branch`-sized; anything slower is wedged, not busy. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private CommandProbe() {
    }

    static void run(File dir, List<String> command, Consumer<String> onLine, IntConsumer onExit) {
        Thread t = new Thread(() -> {
            int code = -1;
            try {
                // ProcessSupport.runBounded: PATH augment, GIT_TERMINAL_PROMPT=0,
                // null-device stdin, both streams drained, waitFor FIRST with a
                // forcible kill on timeout — the whole probe preamble, once.
                ProcessSupport.BoundedResult r =
                        ProcessSupport.runBounded(command, dir, TIMEOUT);
                // the old probe merged stderr into stdout; preserve line-by-line
                // delivery of both (probes are tiny-output), stdout first
                deliverLines(r.stdout(), onLine);
                deliverLines(r.stderr(), onLine);
                code = r.timedOut() ? -1 : r.exitCode();
            } catch (IOException ex) {
                // command unavailable: report failure code
            } finally {
                // onExit fires exactly once, even when a line consumer threw
                onExit.accept(code);
            }
        }, "nmox-rack-probe");
        t.setDaemon(true);
        t.start();
    }

    /** Splits captured output into lines; a throwing consumer skips nothing. */
    private static void deliverLines(String output, Consumer<String> onLine) {
        if (output.isEmpty()) {
            return;
        }
        for (String line : output.split("\n")) {
            String clean = line.endsWith("\r")
                    ? line.substring(0, line.length() - 1) : line;
            try {
                onLine.accept(clean);
            } catch (RuntimeException ex) {
                // the CommandExecutor.safeAccept law: a misbehaving consumer
                // must not swallow the remaining lines or the exit callback
                LOGGER.log(Level.WARNING, "Probe line consumer failed", ex);
            }
        }
    }
}
