package org.nmox.studio.ui.browser.fx;

import java.lang.instrument.Instrumentation;

/**
 * The agent {@link ComplexTextShaping} attaches to its own process (v2.165.0).
 *
 * <p>Its only job is to hand over the {@link Instrumentation} the JVM gives an
 * agent. The attach loads this class a second time, through the system class
 * loader from a jar written at runtime, so the installer reads the field through
 * that loader rather than from this copy.
 */
public final class ShapingAgent {

    /** Set by the JVM's call to {@link #agentmain}; read reflectively. */
    public static volatile Instrumentation instrumentation;

    private ShapingAgent() {
    }

    /** Called by the JVM when the agent is attached. */
    public static void agentmain(String args, Instrumentation inst) {
        instrumentation = inst;
    }
}
