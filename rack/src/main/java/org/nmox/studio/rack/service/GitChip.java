package org.nmox.studio.rack.service;

import java.io.File;
import java.util.Objects;
import org.nmox.studio.core.util.GitFacts;
import org.openide.util.NbBundle.Messages;

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
@Messages({
    "# the chip names an operation git stopped in the middle of (3.4)",
    "GitChip_merging=merging",
    "GitChip_cherryPicking=cherry-picking",
    "GitChip_reverting=reverting",
    "GitChip_applyingPatches=applying patches",
    "GitChip_bisecting=bisecting",
    "# {0} = the branch being rebased, or a short commit id",
    "GitChip_rebasing=rebasing {0}",
    "GitChip_conflicts={0,choice,1#{0} conflict|1<{0} conflicts}"
})
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
    /** The operation git stopped in the middle of, or null (3.4). */
    private volatile GitFacts.InProgress inProgress;
    /** Unmerged paths from the last status, 0 until one arrives. */
    private volatile int conflicts;

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
        inProgress = repoRoot == null ? null : GitFacts.inProgress(repoRoot);
        changeCount = UNKNOWN;
        aheadBehind = null;
        conflicts = 0;
        return true;
    }

    /** Branches move under a live IDE (checkout in a terminal); re-read cheaply. */
    void refreshBranch() {
        if (repoRoot != null) {
            branch = GitFacts.branch(repoRoot);
            inProgress = GitFacts.inProgress(repoRoot); // a merge started in a terminal
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

    /** The operation in progress, or null — for the checkout guard (3.4). */
    GitFacts.InProgress inProgress() {
        return inProgress;
    }

    /**
     * {@code git status --porcelain=v2 --branch} arrived: the dirty count
     * is known (0 is honest here), and so is where the branch stands
     * against its upstream when it has one.
     */
    void porcelain(String porcelainV2Output) {
        changeCount = GitFacts.changeCountV2(porcelainV2Output);
        conflicts = GitFacts.conflictCountV2(porcelainV2Output);
        aheadBehind = GitFacts.aheadBehind(porcelainV2Output);
    }

    /**
     * "⎇ main", then "⎇ main ±3" once a count is known, then "↑2 ↓1" for
     * commits to push and to pull (3.2.0; VS Code's status bar sync
     * counts) — each only when not zero, so an up-to-date branch reads as
     * before. Null = hidden.
     *
     * <p>An operation git stopped in the middle of leads (3.4): a
     * conflicted merge used to read "⎇ main ±2", like two edits, and a
     * rebase as a bare commit id. Now "⎇ main · merging · 2 conflicts" and
     * "⎇ rebasing feature · 1 conflict"; the ordinary count shows during an
     * operation only when there is one to show.
     */
    String label() {
        if (!visible()) {
            return null;
        }
        GitFacts.InProgress op = inProgress;
        StringBuilder b = new StringBuilder("⎇ ");
        if (op != null && op.operation() == GitFacts.Operation.REBASE) {
            b.append(Bundle.GitChip_rebasing(op.rebasedBranch() == null ? branch : op.rebasedBranch()));
        } else {
            b.append(branch);
        }
        if (changeCount != UNKNOWN && (op == null || changeCount > 0)) {
            b.append(" ±").append(changeCount);
        }
        if (op != null && op.operation() != GitFacts.Operation.REBASE) {
            b.append(" · ").append(operationWords(op));
        }
        if (conflicts > 0) {
            b.append(" · ").append(Bundle.GitChip_conflicts(conflicts));
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

    /** The words for an operation in progress, as the label and the spoken name both say them. */
    private String operationWords(GitFacts.InProgress op) {
        return switch (op.operation()) {
            case MERGE -> Bundle.GitChip_merging();
            case CHERRY_PICK -> Bundle.GitChip_cherryPicking();
            case REBASE -> Bundle.GitChip_rebasing(op.rebasedBranch() == null ? branch : op.rebasedBranch());
            case REVERT -> Bundle.GitChip_reverting();
            case APPLYING_PATCHES -> Bundle.GitChip_applyingPatches();
            case BISECT -> Bundle.GitChip_bisecting();
        };
    }

    /**
     * What a screen reader says for the chip (3.4): the label's facts in
     * words — "Git: branch main, 2 changed, 2 ahead, 1 behind" — where the
     * label is read as its glyphs ("branch sign main plus-minus 2 up arrow
     * 2"). The same clauses appear and disappear as the label's do. Null =
     * hidden.
     */
    String spokenName() {
        if (!visible()) {
            return null;
        }
        int[] ab = aheadBehind;
        GitFacts.InProgress op = inProgress;
        String spoken = Bundle.GitStatusLine_a11yName(branch,
                changeCount != UNKNOWN && (op == null || changeCount > 0)
                        ? Bundle.GitStatusLine_a11yChanged(String.valueOf(changeCount)) : "",
                ab != null && ab[0] > 0 ? Bundle.GitStatusLine_a11yAhead(String.valueOf(ab[0])) : "",
                ab != null && ab[1] > 0 ? Bundle.GitStatusLine_a11yBehind(String.valueOf(ab[1])) : "");
        // the operation git stopped in and its conflicts, which the label
        // leads with: a screen reader must hear "merging, 2 conflicts" too
        // (the 3.4 fold — the label and the spoken name were built by two
        // hands that could not see each other)
        if (op != null) {
            spoken += Bundle.GitStatusLine_a11yClause(operationWords(op));
        }
        if (conflicts > 0) {
            spoken += Bundle.GitStatusLine_a11yClause(Bundle.GitChip_conflicts(conflicts));
        }
        return spoken;
    }
}
