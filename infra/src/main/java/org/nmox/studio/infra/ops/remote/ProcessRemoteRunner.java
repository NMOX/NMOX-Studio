package org.nmox.studio.infra.ops.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.util.Threads;

/**
 * The real {@link RemoteRunner}: spawns the user's own {@code ssh} with a
 * fixed, Hub-built argument vector (see {@link SshTarget}), streams stdin
 * to it when asked, reads stdout and stderr on daemon threads so neither
 * pipe can wedge the child, and kills the whole tree when the leash fires.
 *
 * <p>Nothing of the project runs on THIS machine here: the local process
 * is always {@code ssh}, and what it carries runs on a server the user
 * named and confirmed. That is why these spawns are blessed rather than
 * trust-gated in the spawn ledger.
 */
public final class ProcessRemoteRunner implements RemoteRunner {

    /** Lines kept for the result's tail, so a failure can be reported with its last words. */
    static final int TAIL_LINES = 12;
    /** A line longer than this is cut; a remote command printing one endless line cannot hold the heap. */
    static final int MAX_LINE_CHARS = 16_000;

    private final String knownHostsFile;

    public ProcessRemoteRunner(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    @Override
    public Result run(SshTarget target, String remoteCommand, InputStream stdin,
            Consumer<String> onLine, Duration timeout) {
        List<String> argv = target.sshCommand(knownHostsFile, remoteCommand);
        ProcessBuilder pb = ProcessSupport.builder(argv);
        if (stdin != null) {
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        }
        Deque<String> tail = new ArrayDeque<>();
        Consumer<String> sink = line -> {
            synchronized (tail) {
                tail.addLast(line);
                while (tail.size() > TAIL_LINES) {
                    tail.removeFirst();
                }
            }
            onLine.accept(line);
        };
        Process p;
        try {
            p = pb.start();
        } catch (IOException cannotStart) {
            String why = "ssh could not start: " + cannotStart.getMessage();
            sink.accept(why);
            return new Result(-1, why, false);
        }
        Thread out = Threads.startDaemon(() -> drain(p.getInputStream(), sink), "nmox-ops-ssh-out");
        Thread err = Threads.startDaemon(() -> drain(p.getErrorStream(), sink), "nmox-ops-ssh-err");
        Thread feeder = null;
        if (stdin != null) {
            feeder = Threads.startDaemon(() -> feed(stdin, p.getOutputStream()), "nmox-ops-ssh-in");
        }
        boolean finished;
        try {
            finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            ProcessSupport.killTreeAndWait(p, Duration.ofSeconds(3));
            join(out);
            join(err);
            join(feeder);
            String why = "stopped after " + timeout.toSeconds() + " s with no end";
            sink.accept(why);
            return new Result(-1, joined(tail), true);
        }
        join(out);
        join(err);
        join(feeder);
        return new Result(p.exitValue(), joined(tail), false);
    }

    private static void feed(InputStream from, OutputStream to) {
        try (from; to) {
            from.transferTo(to);
        } catch (IOException closed) {
            // the remote side ended early (a refused tar, a dead link): the
            // exit code and the tail say so; nothing to add here
        }
    }

    private static void drain(InputStream in, Consumer<String> onLine) {
        StringBuilder line = new StringBuilder();
        boolean cut = false;
        try (in) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                for (int i = 0; i < chunk.length(); i++) {
                    char c = chunk.charAt(i);
                    if (c == '\n') {
                        onLine.accept(line.toString());
                        line.setLength(0);
                        cut = false;
                    } else if (c != '\r') {
                        if (line.length() < MAX_LINE_CHARS) {
                            line.append(c);
                        } else if (!cut) {
                            line.append('…');
                            cut = true;
                        }
                    }
                }
            }
        } catch (IOException ended) {
            // the pipe closed under us: what was read is delivered below
        }
        if (line.length() > 0) {
            onLine.accept(line.toString());
        }
    }

    private static void join(Thread t) {
        if (t == null) {
            return;
        }
        try {
            t.join(2_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String joined(Deque<String> tail) {
        synchronized (tail) {
            return String.join("\n", tail);
        }
    }
}
