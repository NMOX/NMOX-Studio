package org.nmox.studio.rack.devices;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A quiet helper for short status probes (current git branch, version
 * checks). Unlike CommandExecutor it opens no Output tab and is meant
 * for commands that finish in milliseconds.
 */
final class CommandProbe {

    private CommandProbe() {
    }

    static void run(File dir, List<String> command, Consumer<String> onLine, IntConsumer onExit) {
        Thread t = new Thread(() -> {
            int code = -1;
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        org.nmox.studio.rack.engine.ToolLocator.resolveCommand(command))
                        .directory(dir)
                        .redirectErrorStream(true);
                pb.environment().put("PATH", org.nmox.studio.rack.engine.ToolLocator.augmentedPath());
                pb.environment().put("GIT_TERMINAL_PROMPT", "0");
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        onLine.accept(line);
                    }
                }
                code = p.waitFor();
            } catch (IOException ex) {
                // command unavailable: report failure code
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            onExit.accept(code);
        }, "nmox-rack-probe");
        t.setDaemon(true);
        t.start();
    }
}
