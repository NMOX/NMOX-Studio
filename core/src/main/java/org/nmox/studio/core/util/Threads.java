package org.nmox.studio.core.util;

/**
 * The ONE place a raw thread is born in the product's main sources: every
 * background thread is a NAMED DAEMON. Named, because a thread dump is the
 * first instrument in an EDT-hang or exit-hang investigation (the v1.33.x
 * boots, the v2.15.0 orphan audit) and an anonymous {@code Thread-17} says
 * nothing; daemon, because a pump or watcher that outlives the platform's
 * shutdown must never be what keeps the JVM alive. Shutdown hooks are the
 * written exception (they must NOT be daemon) and stay on
 * {@code Runtime.addShutdownHook(new Thread(…, name))} — the gate
 * (DaemonThreadGateTest) reads that line shape as lawful and everything
 * else must come through here.
 */
public final class Threads {

    private Threads() {
    }

    /** An unstarted named daemon thread — the argument order of {@code new Thread(body, name)}. */
    public static Thread daemon(Runnable body, String name) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        return t;
    }

    /** {@link #daemon} and start it. */
    public static Thread startDaemon(Runnable body, String name) {
        Thread t = daemon(body, name);
        t.start();
        return t;
    }
}
