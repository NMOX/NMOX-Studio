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

    static int hangUp(Stream<ProcessHandle> children) {
        List<ProcessHandle> helpers = new ArrayList<>();
        children.forEach(h -> {
            if (isPtyHelper(h.info().command().orElse(null))) {
                helpers.add(h);
            }
        });
        helpers.forEach(ProcessHandle::destroy);
        return helpers.size();
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
