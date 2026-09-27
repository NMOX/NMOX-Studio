package org.nmox.studio.editor.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.Threads;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * What a language server said on its way down (3.4). The launch used to
 * send a server's stderr to {@code DISCARD}: a server that crashed left no
 * trace in NMOX's log, the user was told nothing, and one that crashed on
 * every start went dark for about a minute at a time until the platform's
 * own "Failed to start" after five deaths in sixty seconds. Now each
 * server's stderr is drained on a daemon thread into a bounded tail (the
 * last {@value #MAX_LINES} lines, each clipped at {@value #MAX_LINE_CHARS}
 * characters — a read no server can grow), and when the process exits
 * unexpectedly the tail goes to the log at WARNING and the last line to
 * the status line: "&lt;server&gt; stopped (exit N): &lt;line&gt; — it
 * restarts on the next use".
 *
 * <p>Draining also matters on its own: a server that writes a lot to an
 * undrained stderr pipe blocks when the pipe fills. DISCARD avoided that by
 * throwing the bytes away; this keeps the bound and the bytes that matter.
 */
final class ServerStderr {

    private static final Logger LOG = Logger.getLogger(ServerStderr.class.getName());

    static final int MAX_LINES = 20;
    static final int MAX_LINE_CHARS = 2_000;
    /** How much of the last line the status line shows; the log keeps the whole tail. */
    static final int STATUS_CHARS = 160;

    private final String server;
    private final Deque<String> tail = new ArrayDeque<>();

    ServerStderr(String server) {
        this.server = server;
    }

    /**
     * Drains {@code process}'s stderr on a daemon thread and reports an
     * unexpected exit through {@code status} (the status line in
     * production).
     */
    static ServerStderr watch(Process process, String server, Consumer<String> status) {
        ServerStderr stderr = new ServerStderr(server);
        Thread drain = Threads.startDaemon(() -> stderr.drain(process.getErrorStream()),
                "nmox-lsp-stderr-" + server);
        process.onExit().thenAccept(p -> {
            try {
                drain.join(2_000); // the last line usually arrives just before the exit
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            stderr.exited(p.exitValue(), status);
        });
        return stderr;
    }

    /** The production sink: the status line, plain text (a line of stderr is not markup). */
    static void toStatusLine(String message) {
        java.awt.EventQueue.invokeLater(() ->
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message)));
    }

    /** Reads to EOF, keeping the last lines; never holds more than the bound. */
    void drain(InputStream in) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder();
            int c;
            while ((c = r.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    if (line.length() > 0) {
                        add(line.toString());
                        line.setLength(0);
                    }
                } else if (line.length() < MAX_LINE_CHARS) {
                    line.append((char) c);
                }
            }
            if (line.length() > 0) {
                add(line.toString());
            }
        } catch (IOException closed) {
            // the process went away mid-line: what arrived is what there is
        }
    }

    synchronized void add(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        tail.addLast(trimmed);
        while (tail.size() > MAX_LINES) {
            tail.removeFirst();
        }
    }

    synchronized List<String> lines() {
        return new ArrayList<>(tail);
    }

    /**
     * A clean exit (0) is the client shutting the server down; 143 is a
     * SIGTERM someone sent on purpose (the platform stopping a hung
     * server, the IDE quitting). On Windows the platform's stop is
     * {@code Process.destroy()}, which is TerminateProcess with exit code 1,
     * so 1 there is a stop too — otherwise every ordinary stop would put a
     * crash on the status line (the 3.4 review, read from LSPBindings). The
     * cost is written here: a Windows server that crashes with exit 1 is not
     * announced. Anything else is a crash worth a word.
     */
    static boolean unexpected(int exitCode) {
        return unexpected(exitCode, org.openide.util.BaseUtilities.isWindows());
    }

    static boolean unexpected(int exitCode, boolean windows) {
        return exitCode != 0 && exitCode != 143 && !(windows && exitCode == 1);
    }

    void exited(int exitCode, Consumer<String> status) {
        if (!unexpected(exitCode)) {
            return;
        }
        List<String> said = lines();
        LOG.log(Level.WARNING, "Language server {0} exited with {1}; its last stderr lines:\n{2}",
                new Object[]{server, exitCode, said.isEmpty() ? "(none)" : String.join("\n", said)});
        status.accept(message(server, exitCode, said.isEmpty() ? null : said.get(said.size() - 1)));
    }

    /** The status-line sentence, in the reader's language. */
    static String message(String server, int exitCode, String lastLine) {
        String code = String.valueOf(exitCode);
        if (lastLine != null && lastLine.codePointCount(0, lastLine.length()) > STATUS_CHARS) {
            lastLine = lastLine.substring(0, lastLine.offsetByCodePoints(0, STATUS_CHARS)) + "\u2026";
        }
        return lastLine == null
                ? NbBundle.getMessage(ServerStderr.class, "ServerStderr_stopped", server, code)
                : NbBundle.getMessage(ServerStderr.class, "ServerStderr_stoppedSaying", server, code, lastLine);
    }
}
