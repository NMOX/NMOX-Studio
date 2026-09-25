package org.nmox.studio.rack.mcp;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.openide.windows.OnShowing;

/**
 * "Start when NMOX Studio starts" (3.2): once the main window is SHOWING,
 * start the Agent Port — but only when the user ticked BOTH boxes in its
 * dialog, Keep this address and token and Start when NMOX Studio starts.
 * Default off, so nothing listens unless the user asked.
 *
 * <p>Zero boot cost on the common path: two preference reads. The start
 * itself (a keychain read and a loopback bind — no process is spawned, so
 * the zero-boot-spawns law is untouched) rides the Agent Port's own lane
 * off the EDT, and the status-line chip appears through its usual poll.
 * Never under the docs forge: a screenshot run photographs a fresh install.
 */
@OnShowing
public final class AgentPortAutostart implements Runnable {

    private final Supplier<AgentPortKeep> keep;
    private final Consumer<AgentPortKeep> starter;

    /** The platform's constructor: the real preferences, the real lane. */
    public AgentPortAutostart() {
        this(AgentPortKeep::production, k -> AgentPortAction.start(k, false));
    }

    /** The seam: tests hand in their own preferences and count the starts. */
    AgentPortAutostart(Supplier<AgentPortKeep> keep, Consumer<AgentPortKeep> starter) {
        this.keep = keep;
        this.starter = starter;
    }

    @Override
    public void run() {
        if (System.getProperty("nmox.shots.dir") != null) {
            return;
        }
        AgentPortKeep k = keep.get();
        if (k.autostart()) {
            starter.accept(k);
        }
    }
}
