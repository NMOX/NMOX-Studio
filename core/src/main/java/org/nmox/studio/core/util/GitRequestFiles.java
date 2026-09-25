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
     * Whether {@code file} carries one of the names above; pure, no disk.
     * A caller on the EDT asks this first and leaves {@link #isRequestFile}
     * to a lane.
     */
    public static boolean hasRequestName(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        return MESSAGES.contains(name) || REBASE_TODO.equals(name) || PATCHES.contains(name);
    }

    /**
     * Whether {@code file} is one git wrote for its editor: one of the names
     * above, inside a git folder (a worktree's and a submodule's are beneath
     * the main one), so a file of the same name in the project is not one.
     * A folder named {@code .git} is one by name; any other folder is one
     * when it has git's own shape (after 3.2.0: {@code --separate-git-dir}
     * and bare repositories keep theirs under other names) — a {@code HEAD}
     * file beside {@code objects} and {@code refs} folders, or a linked
     * worktree's {@code HEAD} beside its {@code commondir}. Reads the disk
     * for a file that has the name; call it off the EDT.
     */
    public static boolean isRequestFile(File file) {
        if (!hasRequestName(file)) {
            return false;
        }
        for (File d = file.getParentFile(); d != null; d = d.getParentFile()) {
            if (".git".equals(d.getName()) || isGitDir(d)) {
                return true;
            }
        }
        return false;
    }

    /** Git's own test for a git directory, as {@code setup.c}'s {@code is_git_directory} makes it. */
    static boolean isGitDir(File d) {
        if (!new File(d, "HEAD").isFile()) {
            return false;
        }
        return new File(d, "commondir").isFile()
                || (new File(d, "objects").isDirectory() && new File(d, "refs").isDirectory());
    }
}
