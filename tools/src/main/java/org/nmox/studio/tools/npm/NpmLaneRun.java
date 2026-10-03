package org.nmox.studio.tools.npm;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nmox.studio.rack.service.WorkspaceTrust;

/**
 * One script on the NPM Service lane, answered with its EXIT CODE — for a
 * caller that must know whether the script ran and how it ended before it
 * starts something else (a VS Code task chain: a {@code dependsOn} that
 * names an {@code npm}-type task).
 *
 * <p>{@link NpmService#runScript} answers with the script's output, and
 * completes NORMALLY in three cases where nothing ran at all: Workspace
 * Trust was declined, the project's own install is still running, or its
 * dependencies are not installed (the lane says which on the status line
 * and offers the install). To a chain those are not "exit 0". This class
 * asks the same three questions itself, in the lane's own package where
 * {@link InstallGuard} can be read, and answers {@link #NOT_RUN} for them;
 * the lane still does the saying. Nothing here spawns: the lane does, with
 * its own trust gate, Output tab and toolbar ■.
 */
public final class NpmLaneRun {

    /** The script did not run: trust was declined, or the lane refused it and said why. */
    public static final int NOT_RUN = Integer.MIN_VALUE;

    /** The lane's failure message carries the exit code; read back here, pinned by a test against the lane. */
    private static final Pattern EXIT = Pattern.compile("^Command failed with exit code: (-?\\d+)");

    private NpmLaneRun() {
    }

    /**
     * Runs {@code script} of the package in {@code dir} on the NPM
     * Service lane; completes with its exit code, or {@link #NOT_RUN}.
     * Reads {@code package.json} (bounded) and may raise the trust
     * prompt, so call it off the EDT.
     */
    public static CompletableFuture<Integer> runScript(File dir, String script) {
        return runScript(dir, script, null);
    }

    /**
     * {@link #runScript(File, String)} with a listener on every line the
     * script prints (3.6.0): a VS Code npm task that declares a problem
     * matcher has its output read as it comes. {@code lines} is called on
     * the lane's output thread, and never when the script did not run.
     */
    public static CompletableFuture<Integer> runScript(File dir, String script,
            java.util.function.Consumer<String> lines) {
        // the lane's own first question, asked here so a No is known as a No
        if (!WorkspaceTrust.requestTrust(dir)) {
            return CompletableFuture.completedFuture(NOT_RUN);
        }
        boolean walled = InstallGuard.installing(dir) || InstallGuard.needsInstall(dir);
        NpmService npm = NpmService.getDefault();
        return npm.runScript(dir, script, npm.detectPackageManager(dir), lines)
                .handle((output, failed) -> exitOf(walled, failed));
    }

    /** The exit a lane run ended with: the code of a failure, {@link #NOT_RUN} behind a wall, else zero. */
    static int exitOf(boolean walled, Throwable failed) {
        if (failed != null) {
            Matcher m = EXIT.matcher(String.valueOf(failed.getMessage()));
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    return code == 0 ? 1 : code;
                } catch (NumberFormatException tooLong) {
                    return 1;
                }
            }
            return 1;
        }
        return walled ? NOT_RUN : 0;
    }
}
