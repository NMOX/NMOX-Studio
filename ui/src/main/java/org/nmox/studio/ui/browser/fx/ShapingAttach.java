package org.nmox.studio.ui.browser.fx;

import java.lang.reflect.InvocationTargetException;

/**
 * Attaches the shaping agent to the IDE from a separate process (v2.171.0).
 *
 * <p>A JVM may only attach to itself when it was started with
 * {@code -Djdk.attach.allowAttachSelf=true}. The launcher conf carries that flag
 * since v2.165.0, but the conf ships only with the installers: an install that
 * reached v2.165.0 or later through the in-app update center never received it,
 * so the Browser painted unshaped text there. Another process needs no such
 * permission, so {@link ComplexTextShaping} runs this class with the IDE's own
 * runtime: {@code java -cp <agent jar> ShapingAttach <pid> <agent jar>}. The
 * attach API is reached reflectively because this module is compiled without
 * {@code jdk.attach}.
 */
public final class ShapingAttach {

    private ShapingAttach() {
    }

    /** {@code <pid> <agent jar>}; exits 0 when the agent was loaded, 2 when it was not. */
    public static void main(String[] args) {
        System.exit(attach(args) ? 0 : 2);
    }

    static boolean attach(String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("usage: ShapingAttach <pid> <agent jar>");
            return false;
        }
        try {
            Class<?> vm = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object machine = vm.getMethod("attach", String.class).invoke(null, args[0]);
            // the load decides the answer, never the detach: a detach that throws
            // once the agent is IN (the target tearing the pipe down, say) used to
            // be reported as a refusal, so the caller repaired a Browser that was
            // already shaped — and it replaced the load's own exception, which is
            // the one that says WHY a real refusal happened
            vm.getMethod("loadAgent", String.class).invoke(machine, args[1]);
            try {
                vm.getMethod("detach").invoke(machine);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                System.err.println("agent loaded; detach failed: " + ex);
            }
            return true;
        } catch (InvocationTargetException ex) {
            System.err.println("attach refused: " + ex.getCause());
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            System.err.println("attach unavailable: " + ex);
            return false;
        }
    }
}
