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

    /** What {@link #runBounded} came back with. {@code exitCode} is -1 on timeout. */
    public record BoundedResult(int exitCode, String stdout, String stderr, boolean timedOut) {
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
        Thread outDrain = drain(p.getInputStream(), out, "nmox-bounded-out");
        Thread errDrain = drain(p.getErrorStream(), err, "nmox-bounded-err");
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
                snapshot(out), snapshot(err), !finished);
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
     */
    public static void killTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static Thread drain(InputStream stream, StringBuilder into, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) {
                    synchronized (into) {
                        into.append(buf, 0, n);
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
