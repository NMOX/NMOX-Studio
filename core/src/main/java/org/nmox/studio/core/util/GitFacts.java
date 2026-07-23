package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The git facts a status chip can learn WITHOUT spawning a process:
 * repository membership and the current branch are plain file reads
 * (.git discovery, HEAD parsing), so they are safe on any aim event —
 * the v1.38.0 boot law stays intact because nothing here forks. The one
 * fact that genuinely needs the git binary (the dirty count) is kept as
 * a pure parser over output someone ELSE obtained; the caller owns the
 * decision of when a process is allowed to run.
 *
 * <p>Every reader returns null on unreadable or unrecognized state:
 * a chip built on these facts hides rather than lies.
 */
public final class GitFacts {

    private GitFacts() {
    }

    /**
     * Nearest ancestor (including {@code dir} itself) that contains a
     * {@code .git} entry — a directory in a normal checkout, a FILE in
     * worktrees and submodules (it holds a {@code gitdir:} pointer, and
     * either shape marks the working-tree root). Null when no ancestor
     * qualifies.
     */
    public static File repoRoot(File dir) {
        for (File d = dir; d != null; d = d.getParentFile()) {
            File dotGit = new File(d, ".git");
            if (dotGit.isDirectory() || dotGit.isFile()) {
                return d;
            }
        }
        return null;
    }

    /**
     * The checked-out branch name from {@code .git/HEAD}, no process:
     * {@code ref: refs/heads/<name>} yields the name (which may itself
     * contain slashes — {@code feature/x}), a bare commit sha (detached
     * HEAD) yields its first 7 characters, and anything unreadable or
     * unrecognized yields null.
     */
    public static String branch(File repoRoot) {
        if (repoRoot == null) {
            return null;
        }
        File gitDir = resolveGitDir(new File(repoRoot, ".git"));
        if (gitDir == null) {
            return null;
        }
        String head = readFirstLine(new File(gitDir, "HEAD"));
        if (head == null) {
            return null;
        }
        String refPrefix = "ref: refs/heads/";
        if (head.startsWith(refPrefix)) {
            String name = head.substring(refPrefix.length()).trim();
            return name.isEmpty() ? null : name;
        }
        if (isCommitSha(head)) {
            return head.substring(0, 7);
        }
        return null;
    }

    /**
     * Lines of {@code git status --porcelain} output = changed paths;
     * blank lines don't count (the trailing newline must not inflate a
     * clean tree into a dirty one).
     */
    public static int changeCount(String porcelain) {
        if (porcelain == null || porcelain.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : porcelain.split("\n", -1)) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Worktrees and submodules park their real git dir elsewhere and
     * leave a one-line {@code gitdir: <path>} file behind; follow it
     * (relative paths resolve against the pointer file's directory).
     */
    private static File resolveGitDir(File dotGit) {
        if (dotGit.isDirectory()) {
            return dotGit;
        }
        if (!dotGit.isFile()) {
            return null;
        }
        String line = readFirstLine(dotGit);
        String pointerPrefix = "gitdir:";
        if (line == null || !line.startsWith(pointerPrefix)) {
            return null;
        }
        String path = line.substring(pointerPrefix.length()).trim();
        if (path.isEmpty()) {
            return null;
        }
        File target = new File(path);
        if (!target.isAbsolute()) {
            target = new File(dotGit.getParentFile(), path);
        }
        if (!target.isDirectory()) {
            return null;
        }
        // Confinement (ledger 43): a legitimate `gitdir:` pointer always lands
        // inside a real .git directory — a linked worktree points into
        // <main>/.git/worktrees/<name>, a submodule into
        // <super>/.git/modules/<name>. A crafted .git FILE could otherwise
        // aim this at any directory (/etc, ~/.ssh) and turn the branch chip
        // into a narrow "does this dir's HEAD start with ref: refs/heads/"
        // oracle. Canonicalize (killing ../ and symlink games), then require
        // a ".git" element on the resolved path; anything else is not a git
        // dir and is refused.
        File canonical;
        try {
            canonical = target.getCanonicalFile();
        } catch (IOException unresolved) {
            return null;
        }
        for (File p = canonical; p != null; p = p.getParentFile()) {
            if (".git".equals(p.getName())) {
                return canonical;
            }
        }
        return null;
    }

    /** Bytes read from HEAD / a gitdir pointer — a real one is tens of bytes. */
    static final int FIRST_LINE_CAP = 4_096;

    private static String readFirstLine(File file) {
        try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
            // bounded prefix, never a full slurp: this class already treats
            // a crafted .git FILE as adversarial (the gitdir confinement) —
            // a deliberately huge one must cost 4 KB, not its whole size
            // (ledger 59)
            byte[] prefix = in.readNBytes(FIRST_LINE_CAP);
            String content = new String(prefix, StandardCharsets.UTF_8);
            int nl = content.indexOf('\n');
            if (nl < 0 && prefix.length == FIRST_LINE_CAP) {
                // the cap filled without a line break: the true first line is
                // longer than any honest HEAD/gitdir pointer — reporting a
                // truncated prefix as the line would show a corrupted branch
                // name on the chip; hiding beats lying
                return null;
            }
            String line = (nl >= 0 ? content.substring(0, nl) : content).trim();
            return line.isEmpty() ? null : line;
        } catch (IOException | RuntimeException ex) {
            return null; // unreadable state: the caller hides rather than lies
        }
    }

    private static boolean isCommitSha(String s) {
        // 40 = SHA-1, 64 = the SHA-256 object format git also supports
        if (s.length() != 40 && s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
