package org.nmox.studio.rack.engine;

import org.nmox.studio.core.util.GitFacts;
import org.openide.util.NbBundle.Messages;

/**
 * Whether a pull-request checkout may proceed, decided from
 * {@code git status --porcelain}: a tree with modified or staged files
 * REFUSES (a checkout that carries or clobbers uncommitted work is the
 * never-clobber law's failure mode), while untracked files alone are
 * allowed — git itself leaves them in place. The verdict speaks its
 * reason so the refusal is never silent.
 */
@Messages({
    "GitCheckoutGuard_midMerge=a merge is in progress \u2014 finish it or abort it first (git merge --continue, or git merge --abort)",
    "GitCheckoutGuard_midRebase=a rebase is in progress \u2014 finish it or abort it first (git rebase --continue, or git rebase --abort)",
    "GitCheckoutGuard_midCherryPick=a cherry-pick is in progress \u2014 finish it or abort it first (git cherry-pick --continue, or git cherry-pick --abort)",
    "GitCheckoutGuard_midRevert=a revert is in progress \u2014 finish it or abort it first (git revert --continue, or git revert --abort)"
})
public final class GitCheckoutGuard {

    /** The decision and the sentence the user sees. */
    public record Verdict(boolean allowed, String reason) {
    }

    private GitCheckoutGuard() {
    }

    /**
     * After a FAILED checkout attempt: whether the tool left tracked
     * changes behind that the tree did not have before (gh pr checkout on
     * a shallow clone stages the PR's files and then dies on tracking
     * setup — measured v2.62.0). Only tracked leftovers count; untracked
     * files are the user's and were allowed through the guard.
     */
    public static boolean leftoversToRestore(String porcelainBefore, String porcelainAfter) {
        return trackedCount(porcelainBefore) == 0 && trackedCount(porcelainAfter) > 0;
    }

    private static int trackedCount(String porcelain) {
        if (porcelain == null || porcelain.isBlank()) {
            return 0;
        }
        int n = 0;
        for (String line : porcelain.split("\n")) {
            if (!line.isBlank() && !line.startsWith("??")) {
                n++;
            }
        }
        return n;
    }

    /**
     * Judges the tree AND what git is in the middle of (3.4). A rebase
     * stopped at an edit step has a clean tree and passed the porcelain
     * check, so a checkout ran through the middle of it; a checkout during
     * any stopped operation is refused by name before the tree is read.
     */
    public static Verdict judge(String porcelain, GitFacts.InProgress inProgress) {
        if (inProgress != null) {
            return new Verdict(false, switch (inProgress.operation()) {
                case MERGE -> Bundle.GitCheckoutGuard_midMerge();
                case REBASE -> Bundle.GitCheckoutGuard_midRebase();
                case CHERRY_PICK -> Bundle.GitCheckoutGuard_midCherryPick();
                case REVERT -> Bundle.GitCheckoutGuard_midRevert();
            });
        }
        return judge(porcelain);
    }

    /** Judges porcelain output; null or blank is a clean tree. */
    public static Verdict judge(String porcelain) {
        if (porcelain == null || porcelain.isBlank()) {
            return new Verdict(true, "");
        }
        int tracked = 0;
        int untracked = 0;
        for (String line : porcelain.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("??")) {
                untracked++;
            } else {
                tracked++;
            }
        }
        if (tracked > 0) {
            return new Verdict(false, tracked + " uncommitted change" + (tracked == 1 ? "" : "s")
                    + " in the working tree — commit or stash first; a checkout never carries "
                    + "or clobbers your work.");
        }
        return new Verdict(true, untracked > 0
                ? untracked + " untracked file" + (untracked == 1 ? "" : "s") + " stay in place."
                : "");
    }
}
