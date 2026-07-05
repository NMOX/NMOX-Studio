package org.nmox.studio.apiclient.api;

import java.io.File;
import java.util.Objects;

/**
 * The pure decision core behind following the rack's mid-session
 * re-aims: which {@code projectChanged} events start a workspace load,
 * and which finished loads may bind. It owns the one truth of "which
 * project dir the workspace is bound to" — saves and the file pulse aim
 * at {@link #boundDir()}.
 *
 * <p>The storm laws, pinned by tests:
 * <ul>
 *   <li><b>Equality guard</b> — a re-aim to the already-bound dir is a
 *       no-op; nothing reloads.</li>
 *   <li><b>Bounded reaction</b> — a storm of projectChanged events for
 *       one new aim starts exactly one load.</li>
 *   <li><b>Newest aim wins</b> — a load that finishes after the rack
 *       aimed somewhere else never binds.</li>
 * </ul>
 *
 * <p>Call pattern (decisions on one thread — the EDT): ask
 * {@link #shouldLoad} on every projectChanged with the rack's current
 * dir; when it says yes, read the new workspace OFF the EDT, then ask
 * {@link #shouldApply} back on the EDT before binding the result.
 */
public final class ProjectRebind {

    private File bound;
    private File loading;

    public ProjectRebind(File initiallyBound) {
        this.bound = initiallyBound;
    }

    /** The dir the workspace is currently bound to — saves land here. */
    public synchronized File boundDir() {
        return bound;
    }

    /**
     * A projectChanged arrived and the rack now aims at {@code current}:
     * true when a load should start. False when nothing moved (the
     * equality guard) or a load for this dir is already in flight (the
     * bounded reaction).
     */
    public synchronized boolean shouldLoad(File current) {
        if (Objects.equals(current, bound)) {
            loading = null; // aimed back home before a racing load landed
            return false;
        }
        if (Objects.equals(current, loading)) {
            return false; // that load is already in flight
        }
        loading = current;
        return true;
    }

    /**
     * The load for {@code loadedDir} finished and the rack now aims at
     * {@code currentNow}: true when the result may bind — and then it is
     * bound. A stale load (the aim moved on meanwhile) never applies.
     */
    public synchronized boolean shouldApply(File loadedDir, File currentNow) {
        if (Objects.equals(loadedDir, loading)) {
            loading = null;
        }
        if (!Objects.equals(loadedDir, currentNow) || Objects.equals(loadedDir, bound)) {
            return false;
        }
        bound = loadedDir;
        return true;
    }
}
