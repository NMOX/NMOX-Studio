package org.nmox.studio.dbstudio.io;

import java.io.File;

/**
 * Self-write vs foreign-write discrimination for {@code .nmoxdb.json}:
 * the studio records the (mtime, size) stamp of every version it wrote
 * or loaded itself; a different stamp on disk is someone else's edit
 * (a git pull, a hand edit) and earns exactly one reaction. The pure
 * core behind the studio's external-reload flow — no Swing, no
 * threads, fully pinned by tests.
 *
 * <p>Verdicts:
 * <ul>
 *   <li>{@link Verdict#NONE} — the file is ours (or missing, or a
 *       version already reacted to). Deletion is deliberately NONE:
 *       reloading over a vanished file would clobber in-memory state
 *       the next self-save is about to restore.</li>
 *   <li>{@link Verdict#RELOAD} — a foreign version, and the studio is
 *       free: reload now. The version is consumed — the same stamp
 *       never triggers twice (the bounded-reaction law).</li>
 *   <li>{@link Verdict#DEFER} — a foreign version, but the studio is
 *       busy (a modal connection dialog, a run in flight). NOT
 *       consumed: the caller re-checks when free and gets RELOAD.</li>
 * </ul>
 */
public final class ExternalEdits {

    /** What a check says to do. */
    public enum Verdict { NONE, RELOAD, DEFER }

    /**
     * One file version: modification time + size. Equality of both is
     * the "same version" test — cheap, honest for every editor and VCS
     * that actually rewrites the file.
     */
    public record Stamp(long mtime, long size) {

        /** The file's current stamp, or null when it doesn't exist. */
        public static Stamp of(File file) {
            return file != null && file.isFile()
                    ? new Stamp(file.lastModified(), file.length())
                    : null;
        }
    }

    /** The last version this studio wrote or loaded; null = none/missing. */
    private Stamp known;

    /**
     * Records a version as our own — call after every save AND every
     * load (a loaded version is "ours" in the only sense that matters:
     * the in-memory state already reflects it). Null is fine: a
     * missing file means any future version is foreign.
     */
    public synchronized void recordOwn(Stamp stamp) {
        known = stamp;
    }

    /**
     * The verdict for what's on disk. {@code busy} defers without
     * consuming, so the post-busy re-check still fires.
     */
    public synchronized Verdict check(Stamp onDisk, boolean busy) {
        if (onDisk == null || onDisk.equals(known)) {
            return Verdict.NONE;
        }
        if (busy) {
            return Verdict.DEFER;
        }
        known = onDisk; // consumed: one reaction per foreign version
        return Verdict.RELOAD;
    }
}
