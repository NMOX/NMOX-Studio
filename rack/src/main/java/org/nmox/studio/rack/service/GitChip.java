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
    /** Commits to push and to pull, or null: no upstream, or not known yet. */
    private volatile int[] aheadBehind;

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
        aheadBehind = null;
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

    /**
     * {@code git status --porcelain=v2 --branch} arrived: the dirty count
     * is known (0 is honest here), and so is where the branch stands
     * against its upstream when it has one.
     */
    void porcelain(String porcelainV2Output) {
        changeCount = GitFacts.changeCountV2(porcelainV2Output);
        aheadBehind = GitFacts.aheadBehind(porcelainV2Output);
    }

    /**
     * "⎇ main", then "⎇ main ±3" once a count is known, then "↑2 ↓1" for
     * commits to push and to pull (3.2.0; VS Code's status bar sync
     * counts) — each only when not zero, so an up-to-date branch reads as
     * before. Null = hidden.
     */
    String label() {
        if (!visible()) {
            return null;
        }
        StringBuilder b = new StringBuilder("⎇ ").append(branch);
        if (changeCount != UNKNOWN) {
            b.append(" ±").append(changeCount);
        }
        int[] ab = aheadBehind;
        if (ab != null && ab[0] > 0) {
            b.append(" ↑").append(ab[0]);
        }
        if (ab != null && ab[1] > 0) {
            b.append(" ↓").append(ab[1]);
        }
        return b.toString();
    }
}
