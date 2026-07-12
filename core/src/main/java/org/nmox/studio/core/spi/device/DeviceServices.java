package org.nmox.studio.core.spi.device;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * What the host does for the device: processes, signals, and the
 * serving registry. All methods are safe from any thread.
 *
 * @since 1.55
 */
public interface DeviceServices {

    /**
     * Runs a tool command (argv, no shell) in the project directory
     * with the project's dotenv and the rack's env overrides applied,
     * streaming merged stdout/stderr line by line. One process per
     * device: a second call stops the first. Output rides the monitor
     * bus and the flight recorder automatically, and the orphan
     * guarantee covers the process on Stop All, project switch, and
     * IDE exit.
     *
     * <p><b>Workspace trust gates every launch.</b> If the user has not
     * trusted the project folder (and declines the prompt), nothing is
     * spawned and {@code onExit} receives {@code -1}.
     */
    void exec(List<String> command, Consumer<String> onLine, IntConsumer onExit);

    /** Stops the device's running process, if any (kills the whole tree). */
    void stop();

    /** True while the device's process is alive. */
    boolean isRunning();

    /** Fires a TRIGGER out of an OUT port declared in the descriptor. */
    void emitTrigger(String portId, boolean ok);

    /** Sends a DATA line out of an OUT port declared in the descriptor. */
    void emitData(String portId, String text);

    /** Sets a GATE level on an OUT port declared in the descriptor. */
    void emitGate(String portId, boolean high);

    /** The rack's current project directory. */
    File projectDir();

    /**
     * Announces a live URL to the Serving Registry — the status-line
     * chip, Quick Search's Live Servers, and the quality gates all see
     * it. Withdraw when serving stops; the host withdraws on dispose.
     */
    void announceServing(String url);

    /** Withdraws this device's serving announcement. */
    void withdrawServing();
}
