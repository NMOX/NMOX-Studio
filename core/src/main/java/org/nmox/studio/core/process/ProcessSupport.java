package org.nmox.studio.core.process;

import java.io.File;
import java.util.List;
import java.util.Map;

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
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(windows ? "NUL" : "/dev/null");
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
}
