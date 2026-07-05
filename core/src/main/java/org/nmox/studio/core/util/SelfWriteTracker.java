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
}
