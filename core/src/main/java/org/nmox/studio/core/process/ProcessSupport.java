package org.nmox.studio.core.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One place to launch a developer tool correctly. Every process the IDE
 * spawns — build commands, lsof, docker — needs the same hardening, and
 * it used to be copy-pasted at each launch site (one copy even hardcoded
 * {@code /dev/null}, breaking on Windows). This is that preamble, once:
 *
 * <ul>
 *   <li>the command resolved against the augmented PATH — a Finder-launched
 *       IDE has a bare PATH with no node/npm/git on it;</li>
 *   <li>that same PATH in the child's environment;</li>
 *   <li>empty stdin from the OS null device, so an interactive prompt can't
 *       hang a process that has no terminal attached;</li>
 *   <li>the no-color / non-interactive environment that keeps output clean.</li>
 * </ul>
 */
public final class ProcessSupport {

    private ProcessSupport() {
    }

    /**
     * The per-stream capture ceiling for {@link #runBounded}, in chars
     * (~8 MB at 2 bytes/char). runBounded is for probes and one-shot tool
     * invocations that emit a line or two; a runaway or hostile child that
     * floods stdout — {@code yes}, a build stuck in a reload loop, a probe
     * that hit a spewing server — would otherwise grow the capture buffer
     * without limit until the timeout fires and OOM the whole IDE. Past this
     * ceiling the drains keep reading (so the child still can't deadlock on a
     * full pipe) but stop appending, and the result is flagged truncated.
     */
    static final int MAX_CAPTURE_CHARS = 4 * 1024 * 1024;

    /** The OS null device: {@code /dev/null}, or {@code NUL} on Windows. */
    public static File nullDevice() {
        // BaseUtilities, not Utilities: the latter lives in org-openide-util-ui,
        // which core deliberately does not depend on
        return new File(org.openide.util.BaseUtilities.isWindows() ? "NUL" : "/dev/null");
    }

    /** A hardened ProcessBuilder for a dev-tool command line. */
    public static ProcessBuilder builder(List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(ToolLocator.resolveCommand(command))
                .redirectInput(nullDevice());
        Map<String, String> env = pb.environment();
        env.put("PATH", ToolLocator.augmentedPath());
        env.put("npm_config_yes", "true");   // npx auto-confirms downloads
        env.put("GIT_TERMINAL_PROMPT", "0");  // git fails fast, never prompts
        env.put("NO_COLOR", "1");             // tools that honor it skip ANSI
        return pb;
    }

    /**
     * What {@link #runBounded} came back with. {@code exitCode} is -1 on
     * timeout; {@code truncated} is true if either stream hit
     * {@link #MAX_CAPTURE_CHARS} and the tail was dropped.
     */
    public record BoundedResult(int exitCode, String stdout, String stderr,
            boolean timedOut, boolean truncated) {
        public boolean ok() {
            return !timedOut && exitCode == 0;
        }
    }

    /**
     * Run a short tool command with a timeout that is actually real.
     *
     * The trap this exists to close: reading a child's output to EOF and only
     * then calling {@code waitFor(timeout)} means the timeout clock never
     * starts while the child sits silent with its pipe open — a wedged tool
     * hangs the caller forever. Here both streams drain on their own threads
     * while {@code waitFor} runs first on the caller's thread; on timeout the
     * child is forcibly killed, which closes both pipes and unblocks the
     * drains. Both streams are always consumed, so a chatty child can never
     * deadlock on a full pipe either.
     *
     * For long-lived or streaming processes (dev servers, REPLs) use
     * CommandExecutor / InteractiveProcess in the rack instead — this is for
     * probes and one-shot tool invocations.
     */
    public static BoundedResult runBounded(List<String> command, File workingDir, Duration timeout)
            throws IOException {
        ProcessBuilder pb = builder(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        java.util.concurrent.atomic.AtomicBoolean outCut =
                new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean errCut =
                new java.util.concurrent.atomic.AtomicBoolean();
        Thread outDrain = drain(p.getInputStream(), out, outCut, "nmox-bounded-out");
        Thread errDrain = drain(p.getErrorStream(), err, errCut, "nmox-bounded-err");
        boolean finished;
        try {
            finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            killTree(p);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for " + command.get(0), e);
        }
        if (!finished) {
            killTree(p);
        }
        joinQuietly(outDrain);
        joinQuietly(errDrain);
        return new BoundedResult(finished ? p.exitValue() : -1,
                snapshot(out), snapshot(err), !finished, outCut.get() || errCut.get());
    }

    private static String snapshot(StringBuilder sb) {
        synchronized (sb) {
            return sb.toString();
        }
    }

    /**
     * Kill the child AND its descendants — descendants first. Killing only
     * the direct child is not enough: a shell that spawned (rather than
     * exec'd) its command — dash on Linux, any {@code cmd &} — leaves a
     * grandchild holding the pipe's write end, and the read side never
     * sees EOF; likewise a debug server whose debuggee must not outlive it.
     * Same lesson the rack's killAndWait descendant sweep encodes.
     *
     * <p>Known limit, Windows only: a grandchild spawned THROUGH an MSYS
     * shell (Git Bash) is invisible to this sweep — MSYS implements exec by
     * starting a new Windows process and exiting the old one, which breaks
     * the parent-PID chain {@link ProcessHandle#descendants()} walks (the
     * same reason taskkill /T fails on Git-Bash trees; a real fix needs Job
     * Objects, outside pure Java). Native Windows process trees — node,
     * npm.cmd→cmd→node, docker — keep intact chains and are swept. Either
     * way runBounded still returns on time: its drain joins are bounded, so
     * a surviving grandchild costs up to 2×5s of tail-drain wait, no hang.
     */
    public static void killTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    /**
     * {@link #killTree}, then wait — boundedly — for the whole tree to have
     * actually exited. destroyForcibly is asynchronous: it returns while the
     * OS is still tearing the process down, and on Windows the dying process
     * keeps its open files and working directory locked until it is truly
     * gone — a caller that deletes those files next (a debug session's
     * teardown, JUnit's @TempDir cleanup) races that lock and loses. The
     * descendant snapshot is taken BEFORE the kill, while the parent-PID
     * chain is still walkable.
     *
     * @return true when every process in the tree is confirmed dead within
     *         the timeout; false means something may still be exiting (the
     *         caller can proceed, but file locks may linger)
     */
    public static boolean killTreeAndWait(Process p, Duration timeout) {
        List<ProcessHandle> tree = new java.util.ArrayList<>(p.descendants().toList());
        tree.add(p.toHandle());
        tree.forEach(ProcessHandle::destroyForcibly);
        long deadline = System.nanoTime() + timeout.toNanos();
        for (ProcessHandle ph : tree) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                break;
            }
            try {
                ph.onExit().get(left, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException ignored) {
                // ExecutionException cannot really happen (onExit completes
                // normally); timeout falls through to the aliveness verdict
            }
        }
        return tree.stream().noneMatch(ProcessHandle::isAlive);
    }

    private static Thread drain(InputStream stream, StringBuilder into,
            java.util.concurrent.atomic.AtomicBoolean truncated, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) {
                    synchronized (into) {
                        // Append up to the ceiling, then keep looping to drain
                        // the pipe to EOF (discarding the rest) so a chatty child
                        // still can't deadlock — but the heap can't grow without
                        // bound and OOM the IDE. See MAX_CAPTURE_CHARS.
                        int room = MAX_CAPTURE_CHARS - into.length();
                        if (room >= n) {
                            into.append(buf, 0, n);
                        } else {
                            if (room > 0) {
                                into.append(buf, 0, room);
                            }
                            truncated.set(true);
                        }
                    }
                }
            } catch (IOException ignored) {
                // pipe closed by the kill path — the drain's job is done
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t) {
        try {
            // After exit or forcible kill the pipe is closed/closing; this is
            // just letting the last buffered bytes land. Daemon thread — if it
            // somehow outlives the wait, it cannot pin the JVM.
            t.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
