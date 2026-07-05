package org.nmox.studio.infra;

/**
 * Once-per-open listener bookkeeping — the extractable core of the
 * v1.36 designer lifecycle fix. Until that release the designer
 * attached its graph + rack listeners in the constructor and removed
 * them in componentClosed: the FIRST close killed autosave and
 * dirty-tracking for good, and nothing on reopen brought them back
 * (worse, a naive "attach in open" without this guard would stack a
 * second listener on the graph's CopyOnWriteArrayList and double-fire
 * every change).
 *
 * <p>{@link #open()} runs the attach action at most once per cycle;
 * {@link #close()} runs the detach action only when attached. Both are
 * idempotent. EDT-confined, like the window hooks that call them.
 */
final class ListenerLifecycle {

    private final Runnable attach;
    private final Runnable detach;
    private boolean attached;

    ListenerLifecycle(Runnable attach, Runnable detach) {
        this.attach = attach;
        this.detach = detach;
    }

    /** componentOpened: attaches once; a re-open after close re-attaches. */
    void open() {
        if (attached) {
            return;
        }
        attach.run();
        attached = true;
    }

    /** componentClosed: detaches what {@link #open()} attached, once. */
    void close() {
        if (!attached) {
            return;
        }
        detach.run();
        attached = false;
    }

    boolean attached() {
        return attached;
    }
}
