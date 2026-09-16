package org.nmox.studio.ui.browser.fx;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The agent {@link ComplexTextShaping} attaches to its own process (v2.165.0).
 *
 * <p>Its only job is to hand over the {@link Instrumentation} the JVM gives an
 * agent, ONCE. The attach loads this class a second time, through the system
 * class loader from a jar written at runtime, so the installer claims it through
 * that loader rather than from this copy. A handle that can rewrite any class
 * is not left in a static field for anyone to read: it waits in a one-slot
 * handoff and is gone once claimed.
 */
public final class ShapingAgent {

    private static final BlockingQueue<Instrumentation> HANDOFF = new ArrayBlockingQueue<>(1);

    private ShapingAgent() {
    }

    /** Takes the Instrumentation the attach delivered, or null; called reflectively, once. */
    public static Instrumentation claim() {
        return HANDOFF.poll();
    }

    /** Called by the JVM when the agent is attached. */
    public static void agentmain(String args, Instrumentation inst) {
        // a newer attach supersedes an unclaimed one: the slot holds the latest
        while (!HANDOFF.offer(inst)) {
            HANDOFF.poll();
        }
    }
}
