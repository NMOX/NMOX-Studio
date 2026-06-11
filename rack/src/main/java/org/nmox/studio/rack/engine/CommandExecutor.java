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
 * Runs external tool processes for rack devices: streams merged
 * stdout/stderr line by line to the device (for meters and LCDs) and
 * mirrors everything into a NetBeans Output window tab per device.
 */
public final class CommandExecutor {

    private CommandExecutor() {
    }

    /** A running process that can be killed from a STOP button. */
    public interface Handle {
        void kill();

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

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(dir)
                    .redirectErrorStream(true);
            pb.environment().putAll(env);
            process = pb.start();
        } catch (IOException ex) {
            String msg = "launch failed: " + ex.getMessage();
            if (out != null) {
                out.println(msg);
            }
            safeAccept(onLine, msg);
            onExit.accept(-1);
            return new Handle() {
                @Override
                public void kill() {
                }

                @Override
                public boolean isAlive() {
                    return false;
                }
            };
        }

        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out != null) {
                        out.println(line);
                    }
                    safeAccept(onLine, line);
                }
            } catch (IOException ignored) {
                // stream closes when the process dies or is killed
            }
            int code;
            try {
                code = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            if (out != null) {
                out.println("[exit " + code + "]");
            }
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
            public boolean isAlive() {
                return process.isAlive();
            }
        };
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
            InputOutput io = IOProvider.getDefault().getIO("Rack: " + tabName, false);
            io.select();
            return io;
        } catch (RuntimeException ex) {
            return null; // headless (tests) or window system not ready
        }
    }
}
