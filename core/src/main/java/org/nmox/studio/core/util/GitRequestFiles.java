package org.nmox.studio.core.util;

import java.io.File;
import java.util.Set;

/**
 * The files git hands its editor ({@code core.editor "nmox -w"}): the
 * messages, the rebase list and the patches {@code git add -e} and
 * {@code git add -p}'s edit write. One home for the names — the editor's
 * resolver gives them their languages, and the window system's restore
 * (3.2: a quit with a commit message open reopened that message, already
 * used, at every later launch) closes a left-over one.
 */
public final class GitRequestFiles {

    /** Messages: commit, merge, tag, squash, notes and a branch's description. */
    public static final Set<String> MESSAGES = Set.of("COMMIT_EDITMSG", "MERGE_MSG", "TAG_EDITMSG",
            "SQUASH_MSG", "NOTES_EDITMSG", "EDIT_DESCRIPTION");

    /** {@code git rebase -i}'s list. */
    public static final String REBASE_TODO = "git-rebase-todo";

    /** What {@code git add -e} and {@code git add -p}'s edit hand the editor. */
    public static final Set<String> PATCHES = Set.of("ADD_EDIT.patch", "addp-hunk-edit.diff");

    private GitRequestFiles() {
    }

    /**
     * Whether {@code file} is one git wrote for its editor: one of the names
     * above, inside a {@code .git} folder (a worktree's and a submodule's
     * are beneath the main one), so a file of the same name in the project
     * is not one.
     */
    public static boolean isRequestFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        if (!MESSAGES.contains(name) && !REBASE_TODO.equals(name) && !PATCHES.contains(name)) {
            return false;
        }
        for (File d = file.getParentFile(); d != null; d = d.getParentFile()) {
            if (".git".equals(d.getName())) {
                return true;
            }
        }
        return false;
    }
}
