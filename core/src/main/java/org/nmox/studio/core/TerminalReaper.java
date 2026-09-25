package org.nmox.studio.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.openide.modules.OnStart;

/**
 * Hangs up the Terminal's shells when the IDE quits.
 *
 * <p>The platform's Terminal runs each shell under a small helper, the
 * native-execution module's {@code pty}, a child of the IDE's JVM that
 * holds the pseudo-terminal. Walked in 3.2 on the assembled app: after
 * Quit NMOX Studio, and after a TERM, the helper and its {@code zsh}
 * went on running under launchd with nothing attached — an invisible
 * shell per Terminal ever opened, still holding whatever it had started
 * (a dev server keeps its port). Four such pairs from two days earlier
 * were found on the machine that measured it.
 *
 * <p>Only the helper is ended, never the shell: when it exits the kernel
 * hangs up the session, so the shell and its jobs get SIGHUP exactly as
 * they would from closing a terminal window — which is also why a job
 * started with {@code nohup} survives, as it would anywhere else
 * (measured: TERM to the helper, and its {@code zsh} was gone within a
 * second). A process the IDE did not start is never touched: the census
 * is the JVM's own children.
 *
 * <p>A {@code git} still running under a Terminal gets a short grace
 * first: quitting hands every commit message the IDE is holding back to
 * the {@code nmox -w} waiting for it (the edit-request watcher's own
 * shutdown hook), and a hang-up arriving in the same instant would end
 * the {@code git commit} before it could use the message the user saved.
 */
@OnStart
public class TerminalReaper implements Runnable {

    /** Registered once per JVM; also the test seam that the hook exists. */
    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED =
            new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void run() {
        register();
    }

    /** Adds the shutdown hook, once. */
    static boolean register() {
        if (REGISTERED.compareAndSet(false, true)) {
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(TerminalReaper::hangUp, "nmox-terminal-reaper"));
            } catch (IllegalStateException alreadyStopping) {
                REGISTERED.set(false); // nothing left to protect
            }
        }
        return REGISTERED.get();
    }

    /** Ends every pty helper among this JVM's children; returns how many were asked. */
    static int hangUp() {
        return hangUp(ProcessHandle.current().children());
    }

    /** How long a git under a Terminal may take to finish once the IDE is quitting. */
    static final long GIT_GRACE_MS = 3_000;

    static int hangUp(Stream<ProcessHandle> children) {
        List<ProcessHandle> helpers = new ArrayList<>();
        children.forEach(h -> {
            if (isPtyHelper(h.info().command().orElse(null))) {
                helpers.add(h);
            }
        });
        awaitGit(helpers, GIT_GRACE_MS);
        helpers.forEach(ProcessHandle::destroy);
        return helpers.size();
    }

    /** Waits, at most {@code graceMs} in all, for every git beneath the helpers to exit. */
    static void awaitGit(List<ProcessHandle> helpers, long graceMs) {
        long deadline = System.currentTimeMillis() + graceMs;
        for (ProcessHandle helper : helpers) {
            for (ProcessHandle p : helper.descendants().toList()) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return;
                }
                if (!isGit(p.info().command().orElse(null))) {
                    continue;
                }
                try {
                    p.onExit().get(left, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException
                        | java.util.concurrent.TimeoutException stillRunning) {
                    return; // the grace is spent; the hang-up goes ahead
                }
            }
        }
    }

    /** Whether {@code command} is git itself (any install: Xcode's, Homebrew's, Git for Windows'). */
    static boolean isGit(String command) {
        if (command == null) {
            return false;
        }
        String name = command.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return "git".equals(name) || "git.exe".equalsIgnoreCase(name);
    }

    /**
     * Whether {@code command} is the native-execution module's pty helper:
     * a file named {@code pty} in a folder under {@code dlight_<user>} (it
     * is unpacked to {@code $TMPDIR/dlight_<user>/<hash>/<version>/pty}).
     */
    static boolean isPtyHelper(String command) {
        if (command == null) {
            return false;
        }
        String[] parts = command.replace('\\', '/').split("/");
        if (parts.length < 2 || !"pty".equals(parts[parts.length - 1])) {
            return false;
        }
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].startsWith("dlight_")) {
                return true;
            }
        }
        return false;
    }
}
