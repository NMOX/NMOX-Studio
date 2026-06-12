package org.nmox.studio.rack.engine;

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
            // resolve the tool against the augmented path: a Finder-launched
            // IDE has a bare PATH with no node/npm/git on it
            ProcessBuilder pb = new ProcessBuilder(ToolLocator.resolveCommand(command))
                    .directory(dir)
                    // no terminal is attached: an interactive prompt would
                    // hang forever, so give prompts an empty stdin instead
                    .redirectInput(devNull());
            pb.environment().put("PATH", ToolLocator.augmentedPath());
            // belt and braces against prompts and ANSI art:
            pb.environment().put("npm_config_yes", "true");   // npx auto-confirms downloads
            pb.environment().put("GIT_TERMINAL_PROMPT", "0"); // git fails fast, never prompts
            pb.environment().put("NO_COLOR", "1");            // tools that honor it skip ANSI
            pb.environment().putAll(env);
            process = pb.start();
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
        Thread errPump = new Thread(
                () -> pumpStream(process.getErrorStream(), err, true, tabName, dir, onLine),
                "nmox-rack-errpump-" + tabName);
        errPump.setDaemon(true);
        errPump.start();

        Thread pump = new Thread(() -> {
            pumpStream(process.getInputStream(), out, false, tabName, dir, onLine);
            int code;
            try {
                errPump.join(5_000);
                code = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            if (out != null) {
                out.println("[exit " + code + "]");
            }
            RackBus.publish(tabName, "[exit " + code + "]", code != 0);
            onExit.accept(code);
        }, "nmox-rack-pump-" + tabName);
        pump.setDaemon(true);
        pump.start();

        return new Handle() {
            @Override
            public void kill() {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                // escalate if it ignores SIGTERM
                new Thread(() -> {
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
            return tool.toUpperCase() + " NOT FOUND — install it, or launch the IDE "
                    + "from a terminal so your PATH carries it";
        }
        if (raw.contains("error=13") || raw.contains("Permission denied")) {
            return tool.toUpperCase() + " IS NOT EXECUTABLE — check its permissions";
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
    private static void pumpStream(java.io.InputStream stream, OutputWriter writer,
            boolean isErr, String tabName, File dir, Consumer<String> onLine) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
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
                safeAccept(onLine, clean);
                RackBus.publish(tabName, clean, isErr);
            }
        } catch (IOException ignored) {
            // stream closes when the process dies or is killed
        }
    }

    /** ANSI CSI/OSC escape sequences, e.g. color codes from vite/vitest. */
    private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile(
            "\\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)?)");

    /** Removes ANSI escapes so LCDs and the output window show clean text. */
    static String stripAnsi(String line) {
        return line.indexOf('\u001B') < 0 ? line : ANSI.matcher(line).replaceAll("");
    }

    /** The platform's empty input stream (no terminal is attached). */
    private static File devNull() {
        return new File(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "NUL" : "/dev/null");
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
