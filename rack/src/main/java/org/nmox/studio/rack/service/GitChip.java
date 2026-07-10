package org.nmox.studio.rack.service;

import java.io.File;
import java.util.Objects;
import org.nmox.studio.core.util.GitFacts;

/**
 * The git status chip's decisions, separated from its Swing half so
 * they test headlessly: what the aimed directory makes visible, what
 * the label says, and — the part the v1.38.0 boot law cares about —
 * whether a {@code git status} process may run at all. Fresh launches
 * aim the rack at ~/NMOX, which is not a repository, so the boot path
 * through here never clears {@link #mayRunProcess()} and stays
 * processless; only an aim landing inside a real repo arms it.
 *
 * <p>All facts come from {@link GitFacts} file reads (cheap, no forks);
 * the dirty count arrives from outside via {@link #porcelain} because
 * only the panel — after the guard — is allowed to spawn.
 */
final class GitChip {

    /** The dirty count starts UNKNOWN — the label never shows a fake ±0. */
    private static final int UNKNOWN = -1;

    // volatile: mutated on the chip's single RequestProcessor lane, but the
    // EDT reads visible()/repoRoot() when the user clicks the chip
    private volatile File aimedDir;
    private volatile File repoRoot;
    private volatile String branch;
    private volatile int changeCount = UNKNOWN;

    /**
     * An aim event landed. Equality-guarded: the same directory again
     * resolves nothing and returns false, so listener storms cost only
     * a compare. A real change re-reads repo root + branch (file I/O —
     * callers stay off the EDT) and forgets the old dirty count.
     *
     * @return true when the aim actually changed
     */
    boolean aim(File dir) {
        if (Objects.equals(dir, aimedDir)) {
            return false;
        }
        aimedDir = dir;
        repoRoot = dir == null ? null : GitFacts.repoRoot(dir);
        branch = repoRoot == null ? null : GitFacts.branch(repoRoot);
        changeCount = UNKNOWN;
        return true;
    }

    /** Branches move under a live IDE (checkout in a terminal); re-read cheaply. */
    void refreshBranch() {
        if (repoRoot != null) {
            branch = GitFacts.branch(repoRoot);
        }
    }

    /** The chip shows only when the aim is inside a repo with a readable HEAD. */
    boolean visible() {
        return repoRoot != null && branch != null;
    }

    /**
     * THE boot guard: a {@code git status} spawn is legal only once an
     * aim event landed on a visible repo. Identical to {@link #visible()}
     * today, but named for what it gates so the source-gate test and the
     * panel say what they mean.
     */
    boolean mayRunProcess() {
        return visible();
    }

    File repoRoot() {
        return repoRoot;
    }

    /** Porcelain output arrived; the count is now known (0 is honest here). */
    void porcelain(String porcelainOutput) {
        changeCount = GitFacts.changeCount(porcelainOutput);
    }

    /** "⎇ main", then "⎇ main ±3" once a count is known; null = hidden. */
    String label() {
        if (!visible()) {
            return null;
        }
        return "⎇ " + branch + (changeCount == UNKNOWN ? "" : " ±" + changeCount);
    }
}
