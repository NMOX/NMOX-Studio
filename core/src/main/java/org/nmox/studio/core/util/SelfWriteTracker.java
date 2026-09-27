package org.nmox.studio.core.util;

import java.io.File;

/**
 * Tells a studio's own workspace saves apart from foreign edits by
 * remembering the file stamp (mtime + size) of the last write or load
 * the studio itself performed. A stamp that differs from the last
 * self-sync is a foreign change — hand-edit, git checkout, another
 * tool — and only those trigger a reload.
 *
 * <p>The one shared copy: Contract Studio (.nmoxweb3.json) and API
 * Studio (.nmoxapi.json) both discriminate this way. DB Studio's
 * {@code ExternalEdits} stays deliberately separate — its verdicts
 * (NONE/RELOAD/DEFER) carry different semantics.
 *
 * <p>Thread-safe: the pulse thread asks {@link #isForeign} while the
 * EDT records syncs. The EDT re-asks just before reloading, so a save
 * racing the pulse's tick never masquerades as a foreign edit.
 */
public final class SelfWriteTracker {

    private long mtime = -1;
    private long size = -1;

    /** Records the file's current stamp as "ours" — call after save or load. */
    public synchronized void noteSync(File file) {
        if (file.isFile()) {
            noteSync(file.lastModified(), file.length());
        } else {
            noteSync(-1, -1);
        }
    }

    public synchronized void noteSync(long mtime, long size) {
        this.mtime = mtime;
        this.size = size;
    }

    /** True when the stamp differs from the last self-sync. */
    public synchronized boolean isForeign(long mtime, long size) {
        return mtime != this.mtime || size != this.size;
    }

    /** What {@link #beforeWrite} found on disk. */
    public enum OnDisk {
        /** The bytes this studio last read or wrote, or nothing at all: the write may go ahead. */
        OURS,
        /** Somebody else's bytes — a pull, a checkout, a teammate's edit, another tool. */
        CHANGED,
        /** Somebody else's bytes, holding git's unresolved merge conflict. */
        CONFLICTED
    }

    /**
     * Asked on the save lane immediately before a studio writes over
     * {@code file} (3.4, question 1). A studio checked for a conflict only
     * when it LOADED; a {@code git pull} with the IDE open then left a
     * writable studio over a conflicted file, and the next Send, Run or
     * edit replaced both people's work with one side's (the 3.4 review,
     * in six studios). Anything but {@link OnDisk#OURS} means: write
     * nothing, reload, and say why — the Task Board's rule since v2.7.0,
     * now everyone's.
     *
     * <p>The conflict read happens only when the stamp is foreign, bounded
     * by {@code maxBytes}; a file too big to read is simply CHANGED.
     */
    public OnDisk beforeWrite(File file, long maxBytes) {
        if (!file.exists()) {
            // nothing there (never written, or deleted): a write loses nobody's bytes
            return OnDisk.OURS;
        }
        if (!isForeign(file.lastModified(), file.length())) {
            return OnDisk.OURS;
        }
        try {
            return MergeConflicts.hasMarkers(BoundedReads.read(file, maxBytes))
                    ? OnDisk.CONFLICTED : OnDisk.CHANGED;
        } catch (java.io.IOException unreadable) {
            return OnDisk.CHANGED;
        }
    }
}
