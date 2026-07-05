package org.nmox.studio.infra.model;

import java.io.File;

/**
 * Self-write vs foreign-write discrimination for
 * {@code .nmoxinfra.json}: the designer records the (mtime, size)
 * stamp of every version it wrote or loaded itself; a different stamp
 * on disk is someone else's edit (a git pull, a hand edit) and earns
 * exactly one reaction. Pure — no Swing, no timers; the conflict
 * matrix is pinned by tests.
 *
 * <p>The matrix:
 * <table>
 *   <caption>verdict per (who wrote it, local edit state)</caption>
 *   <tr><th>on disk</th><th>canvas clean</th><th>canvas dirty</th></tr>
 *   <tr><td>own version</td><td>NONE</td><td>NONE</td></tr>
 *   <tr><td>foreign version</td><td>RELOAD</td><td>CONFLICT</td></tr>
 *   <tr><td>missing file</td><td>NONE</td><td>NONE</td></tr>
 *   <tr><td>foreign, already reacted</td><td>NONE</td><td>NONE</td></tr>
 * </table>
 *
 * <p>A foreign version is consumed on first sight — one reload (or one
 * conflict balloon) per version, never a 2-second drumbeat (the
 * bounded-reaction law). A NEW foreign version after that reacts
 * again.
 */
public final class DesignSync {

    /** What a check says to do. */
    public enum Verdict { NONE, RELOAD, CONFLICT }

    /**
     * One file version: modification time + size. Equality of both is
     * the "same version" test.
     */
    public record Stamp(long mtime, long size) {

        /** The file's current stamp, or null when it doesn't exist. */
        public static Stamp of(File file) {
            return file != null && file.isFile()
                    ? new Stamp(file.lastModified(), file.length())
                    : null;
        }
    }

    /** The last version the designer wrote or loaded; null = none/missing. */
    private Stamp known;

    /**
     * Records a version as our own — call after every save AND every
     * load. Null is fine: a missing file means any future version is
     * foreign.
     */
    public synchronized void recordOwn(Stamp stamp) {
        known = stamp;
    }

    /**
     * The verdict for what's on disk given whether the canvas holds
     * unsaved edits (a pending debounced save). A foreign version is
     * consumed either way — RELOAD acts now, CONFLICT asks once.
     */
    public synchronized Verdict check(Stamp onDisk, boolean dirty) {
        if (onDisk == null || onDisk.equals(known)) {
            return Verdict.NONE;
        }
        known = onDisk; // consumed: one reaction per foreign version
        return dirty ? Verdict.CONFLICT : Verdict.RELOAD;
    }
}
