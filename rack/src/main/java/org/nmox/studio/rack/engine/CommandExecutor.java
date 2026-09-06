package org.nmox.studio.rack.engine;

import org.nmox.studio.core.util.Threads;
import org.nmox.studio.core.process.ProcessSupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 * Runs external tool processes for rack devices: streams stdout and
 * stderr line by line to the device (for meters and LCDs), publishes
 * every line on the {@link RackBus} tagged with its stream, and mirrors
 * everything into a NetBeans Output window tab per device - stderr in
 * the error color, exactly as a terminal would.
 */
public final class CommandExecutor {

    private CommandExecutor() {
    }

    /** A running process that can be killed from a STOP button. */
    public interface Handle {
        void kill();

        /**
         * Kills synchronously: TERM, wait up to the grace period, then
         * KILL the whole tree and wait again. For shutdown paths (the
         * JVM exits when the hooks return, so async escalation threads
         * never get their turn) - guarantees no orphaned dev servers.
         */
        void killAndWait(long graceMillis);

        boolean isAlive();
    }

    /**
     * Every process this executor has spawned and not yet seen exit. One
     * JVM shutdown hook reaps the whole set — TERM to every tree, one
     * bounded grace for all of them together, then KILL the stragglers.
     *
     * Why this exists (2026-08-15, the orphan audit): the Rack's own
     * shutdown reaper panics only devices, so it covers the {@code running}
     * handle a device tracks — but several lanes hold a Handle OUTSIDE any
     * rack (the IDE's F6 Run button, NPM Explorer's run-script, the config
     * dialog's installs), and a dev server launched there outlived the JVM
     * on every exit, clean quit included ({@code python3 -m http.server}
     * instances were found orphaned after a {@code pkill}-stopped walk).
     * Registering at the one choke point every spawn already routes
     * through covers every caller, present and future; the Rack reaper
     * stays as the device-state half (double-kill is harmless).
     */
    private static final java.util.Set<Process> LIVE =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Test seam: proves the hook exists without waiting for a JVM exit. */
    static volatile boolean reaperHookRegistered;

    static {
        try {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(CommandExecutor::reapLiveNow, "nmox-exec-reaper"));
            reaperHookRegistered = true;
        } catch (IllegalStateException ignored) {
            // already shutting down; nothing to protect
        }
    }

    /**
     * The shutdown hook's body, callable directly by tests. Snapshot every
     * live tree first, TERM them all, then spend ONE bounded grace across
     * the whole set (hooks are the last code that runs — a per-process
     * grace would let a rack full of servers exhaust the walk's patience),
     * and KILL whatever ignored the TERM.
     */
    static void reapLiveNow() {
        java.util.List<ProcessHandle> tree = new java.util.ArrayList<>();
        for (Process p : LIVE) {
            if (p.isAlive()) {
                p.descendants().forEach(tree::add);
                tree.add(p.toHandle());
            }
        }
        tree.forEach(ProcessHandle::destroy);
        long deadline = System.currentTimeMillis() + 1_500;
        for (ProcessHandle h : tree) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                break;
            }
            try {
                h.onExit().get(left, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException ignored) {
                // fall through to the forced pass
            }
        }
        for (ProcessHandle h : tree) {
            if (h.isAlive()) {
                h.destroyForcibly();
            }
        }
    }

    /** Test seam: the processes the reaper currently tracks. */
    static java.util.Set<Process> liveSnapshot() {
        return java.util.Set.copyOf(LIVE);
    }

    /**
     * Launches a command asynchronously.
     *
     * @param tabName output window tab title (usually the device title)
     * @param dir     working directory
     * @param env     extra environment variables (may be empty)
     * @param command the command line
     * @param onLine  called for every output line (worker thread!)
     * @param onExit  called once with the exit code, or -1 if launch failed
     */
    public static Handle run(String tabName, File dir, Map<String, String> env,
            List<String> command, Consumer<String> onLine, IntConsumer onExit) {

        InputOutput io = getIO(tabName);
        OutputWriter out = io == null ? null : io.getOut();
        if (out != null) {
            out.println("$ " + String.join(" ", command));
        }
        // lifecycle markers travel the bus too, so the flight recorder
        // (and an all-tap MONITOR) sees launches, not just output
        RackBus.publish(tabName, "$ " + String.join(" ", command), false);

        Process process;
        try {
            // shared hardening (PATH augment, empty stdin, no-color/non-interactive
            // env); see ProcessSupport. Per-launch env overrides go on top.
            ProcessBuilder pb = ProcessSupport.builder(command).directory(dir);
            pb.environment().putAll(env);
            process = pb.start();
            // the JVM-exit reaper tracks every spawn until the OS reports
            // it gone; onExit rides the JDK's process-reaper thread, so
            // the set shrinks even for lanes that never call waitFor
            LIVE.add(process);
            Process spawned = process;
            process.onExit().thenRun(() -> LIVE.remove(spawned));
        } catch (IOException ex) {
            String msg = friendlyLaunchFailure(command, ex);
            if (out != null) {
                out.println(msg);
            }
            safeAccept(onLine, msg);
            RackBus.publish(tabName, msg, true);
            onExit.accept(-1);
            return new Handle() {
                @Override
                public void kill() {
                }

                @Override
                public void killAndWait(long graceMillis) {
                }

                @Override
                public boolean isAlive() {
                    return false;
                }
            };
        }

        OutputWriter err = io == null ? null : io.getErr();
        Thread errPump = Threads.daemon(
                () -> pumpStream(process.getErrorStream(), err, true, tabName, dir, onLine),
                "nmox-rack-errpump-" + tabName);
        errPump.start();

        // a kill through this handle is a DELIBERATE end — the user's ■,
        // a row's Stop, Stop All, a re-run's replace — and the exit line
        // says so (v2.84.0): the flight recorder reads it as STOPPED, not a
        // failure, so run_history stays honest and last_failure/ORACLE never
        // treat a user's own stop as something to explain
        java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
        Thread pump = Threads.daemon(() -> {
            pumpStream(process.getInputStream(), out, false, tabName, dir, onLine);
            int code;
            try {
                errPump.join(5_000);
                code = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            String verdict = "[exit " + code + "]" + (stopped.get() ? " stopped" : "");
            if (out != null) {
                out.println(verdict);
            }
            RackBus.publish(tabName, verdict, code != 0 && !stopped.get());
            onExit.accept(code);
        }, "nmox-rack-pump-" + tabName);
        pump.start();

        return new Handle() {
            @Override
            public void kill() {
                stopped.set(true);
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                // escalate if it ignores SIGTERM
                Threads.daemon(() -> {
                    try {
                        if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                            process.descendants().forEach(ProcessHandle::destroyForcibly);
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }, "nmox-rack-kill").start();
            }

            @Override
            public void killAndWait(long graceMillis) {
                stopped.set(true);
                java.util.List<ProcessHandle> tree = new java.util.ArrayList<>();
                process.descendants().forEach(tree::add);
                tree.forEach(ProcessHandle::destroy);
                process.destroy();
                try {
                    if (!process.waitFor(graceMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        tree.forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                        process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    // descendants may outlive the parent's exit; sweep them
                    for (ProcessHandle h : tree) {
                        if (h.isAlive()) {
                            h.destroyForcibly();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public boolean isAlive() {
                return process.isAlive();
            }
        };
    }

    /**
     * Translates launch IOExceptions into something a developer can act
     * on: the usual cause is simply that the tool is not installed or
     * not on the PATH the IDE sees.
     */
    static String friendlyLaunchFailure(List<String> command, IOException ex) {
        String tool = command.isEmpty() ? "?" : command.get(0);
        String raw = String.valueOf(ex.getMessage());
        if (raw.contains("error=2") || raw.contains("No such file")) {
            return tool.toUpperCase(java.util.Locale.ROOT) + " NOT FOUND — install it, or launch the IDE "
                    + "from a terminal so your PATH carries it";
        }
        if (raw.contains("error=13") || raw.contains("Permission denied")) {
            return tool.toUpperCase(java.util.Locale.ROOT) + " IS NOT EXECUTABLE — check its permissions";
        }
        return "launch failed: " + raw;
    }

    /**
     * Drains one of the process's streams: ANSI-stripped lines go to the
     * device callback (devices parse markers from either stream - vite
     * logs to stderr), onto the rack bus tagged with their origin, and
     * into the Output window, where stderr prints in the error color and
     * file:line references become clickable links.
     */
    /**
     * Per-line ceiling for {@link #readLineBounded}. The pump has no output
     * accumulator (each line dispatches and drops — the streaming design),
     * but {@code readLine()} itself buffered one logical line unbounded: a
     * pathological child emitting gigabytes with no line terminator grew a
     * single String until OOM (ledger 60). 200k chars is far beyond any
     * honest log line and caps the worst case at ~400 KB per pump.
     */
    static final int MAX_LINE_CHARS = 200_000;

    private static void pumpStream(java.io.InputStream stream, OutputWriter writer,
            boolean isErr, String tabName, File dir, Consumer<String> onLine) {
        boolean portExplained = false;
        boolean nodeFloorExplained = false;
        boolean stripTypesExplained = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = readLineBounded(reader, MAX_LINE_CHARS)) != null) {
                String clean = stripAnsi(line);
                if (writer != null) {
                    FileLink.Location loc = FileLink.find(clean, dir);
                    if (loc != null) {
                        try {
                            writer.println(clean, FileLink.opener(loc));
                        } catch (IOException ex) {
                            writer.println(clean);
                        }
                    } else {
                        writer.println(clean);
                    }
                }
                // The one failure every beginner hits eventually, translated
                // where EVERY lane's output flows (v1.264.0): the rack's
                // DevServerDevice had its own LCD message since v1.11, but
                // the IDE Run button — the lane the status bar tells a new
                // user to press — showed only node's raw errno dump. Once
                // per pump so a stack trace can't repeat the explanation.
                if (!portExplained && looksLikePortInUse(clean)) {
                    portExplained = true;
                    String human = friendlyPortInUse(clean);
                    if (writer != null) {
                        writer.println(human);
                    }
                    RackBus.publish(tabName, human, isErr);
                }
                // The second wall of the same class (v1.318.0, found by the
                // Angular walk in the shipped app): a toolchain that
                // hard-refuses the user's Node. npm install sails through —
                // npm doesn't enforce engines — so the refusal appears only
                // at first Run, as raw red CLI text. Angular's CLI and Vite
                // both print a "requires ... Node.js version" line; translate
                // it once with the concrete way out.
                if (!nodeFloorExplained && looksLikeNodeTooOld(clean)) {
                    nodeFloorExplained = true;
                    String human = friendlyNodeTooOld();
                    if (writer != null) {
                        writer.println(human);
                    }
                    RackBus.publish(tabName, human, isErr);
                }
                // The third wall (v2.69.0, futures F7): a .ts entry runs
                // through Node's own type stripping, and a Node older than
                // 22.6 refuses the flag with "bad option" — the same
                // beginner-facing raw red text, translated once.
                String stripWall = !stripTypesExplained
                        ? org.nmox.studio.core.util.NodeTypeStripping.wall(clean) : null;
                if (stripWall != null) {
                    stripTypesExplained = true;
                    if (writer != null) {
                        writer.println(stripWall);
                    }
                    RackBus.publish(tabName, stripWall, isErr);
                }
                safeAccept(onLine, clean);
                RackBus.publish(tabName, clean, isErr);
            }
        } catch (IOException ignored) {
            // stream closes when the process dies or is killed
        }
    }

    /** True when a tool line reports the address-already-in-use failure. */
    static boolean looksLikePortInUse(String line) {
        return line.contains("EADDRINUSE")
                || line.toLowerCase(java.util.Locale.ROOT)
                        .contains("address already in use");
    }

    /**
     * The plain-language translation, with the port number when the line
     * carries one ("...0.0.0.0:8080", "port: 8080").
     */
    static String friendlyPortInUse(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?::|port:? )(\\d{2,5})\\b").matcher(line);
        String port = m.find() ? m.group(1) : null;
        return "\u21b3 " + (port != null ? "Port " + port : "This port")
                + " is already being used by another program \u2014 maybe an"
                + " earlier run that is still going. Stop that program and Run"
                + " again, or change the port. (Task Rack \u25b8 SONAR shows"
                + " who owns every port.)";
    }

    /**
     * True when a tool line reports that the running Node.js is below the
     * tool's minimum. Pinned to the two spellings measured live (both name
     * the same 20.19/22.12 floor): the Angular CLI's
     * {@code "The Angular CLI requires a minimum Node.js version of ..."}
     * and Vite's {@code "Vite requires Node.js version 20.19+ ..."}. The
     * shared shape is {@code requires ... Node.js version}; npm's
     * {@code EBADENGINE} WARNING is deliberately out — installs proceed
     * past it, and a warning that didn't stop anything needs no rescue.
     */
    static boolean looksLikeNodeTooOld(String line) {
        return line.contains("Node.js version")
                && line.contains("requires");
    }

    /**
     * The plain-language way out. The refusing tool's own line (printed
     * just above this one) already names the exact minimum, so this
     * message carries the ACTIONS: how to get a newer Node, and where to
     * see which Node the IDE found.
     */
    static String friendlyNodeTooOld() {
        return "↳ Your Node.js is older than this tool's minimum (the"
                + " line above names it). Install a newer Node —"
                + " nvm install --lts, or brew install node — then Run"
                + " again. (Tools ▸ Environment Doctor shows which node"
                + " the IDE found.)";
    }

    /**
     * {@code readLine} with a ceiling: same terminator handling
     * ({@code \n}, {@code \r}, {@code \r\n}), but a line that exceeds
     * {@code max} chars is returned truncated (marked so the log is honest)
     * and the remainder of that physical line is drained and discarded —
     * the child keeps writing into a moving pipe (no deadlock) while the
     * IDE's memory stays bounded. Package-private for the flood test.
     */
    static String readLineBounded(java.io.BufferedReader reader, int max)
            throws IOException {
        StringBuilder sb = new StringBuilder(120);
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                return sb.toString();
            }
            if (c == '\r') {
                // swallow the \n of a \r\n pair without consuming a lone \r's successor
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.reset();
                }
                return sb.toString();
            }
            if (sb.length() >= max) {
                // ceiling hit: discard the rest of this physical line,
                // still reading so the child never blocks on a full pipe
                while ((c = reader.read()) != -1 && c != '\n') {
                    if (c == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                        break;
                    }
                }
                return sb.append(" …[line truncated]").toString();
            }
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString(); // EOF: last unterminated line
    }

    /** ANSI CSI/OSC escape sequences, e.g. color codes from vite/vitest. */
    private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile(
            "\\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)?)");

    /** Removes ANSI escapes so LCDs and the output window show clean text. */
    static String stripAnsi(String line) {
        return line.indexOf('\u001B') < 0 ? line : ANSI.matcher(line).replaceAll("");
    }

    private static void safeAccept(Consumer<String> onLine, String line) {
        try {
            onLine.accept(line);
        } catch (RuntimeException ignored) {
            // a misbehaving device must not stall the output pump
        }
    }

    private static InputOutput getIO(String tabName) {
        try {
            // never select(): stealing focus on every run is hostile, and a
            // select before the window system settles detaches the output
            // into a broken floating window. The failure toast offers an
            // explicit way in instead.
            return IOProvider.getDefault().getIO("Rack: " + tabName, false);
        } catch (RuntimeException ex) {
            return null; // headless (tests) or window system not ready
        }
    }

    /** Focuses a device's output tab - for explicit user requests only. */
    public static void showOutput(String tabName) {
        try {
            IOProvider.getDefault().getIO("Rack: " + tabName, false).select();
        } catch (RuntimeException ignored) {
        }
    }
}
