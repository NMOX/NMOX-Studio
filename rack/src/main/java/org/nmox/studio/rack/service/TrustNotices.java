package org.nmox.studio.rack.service;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One notice per folder per session that something is waiting for
 * Workspace Trust (3.5.10).
 *
 * <p>Two things wait: git, in a repository nobody has trusted (3.5.7), and a
 * language server that runs the project's code (3.5.6). Each says so in a
 * notification whose click is the trust question. A repository and the
 * project inside it are one decision for the person looking at them, so the
 * second notice for a folder, or for a folder of the same repository inside
 * one already announced, is not shown: the first one's click covers both,
 * because a grant covers subfolders.
 *
 * <p>A folder announced earlier does not silence its PARENT: trusting
 * {@code repo/packages/app} would not let git run in {@code repo}.
 */
public final class TrustNotices {

    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    private TrustNotices() {
    }

    /**
     * True the first time this session for a folder that is not inside one
     * already announced IN THE SAME REPOSITORY.
     *
     * <p>A folder with a {@code .git} of its own between it and the
     * announced one is another repository and another decision (3.5.13):
     * a clone opened under a folder that was announced and left untrusted
     * (a home folder kept in git, a {@code ~/src} that is itself a
     * repository) had been silent, for git and for its language servers.
     */
    public static synchronized boolean firstFor(File folder) {
        if (folder == null) {
            return false;
        }
        String path = folder.getAbsolutePath();
        for (String said : SAID) {
            // a path boundary, as WorkspaceTrust matches: /a/foo does not cover /a/foobar
            if (path.equals(said)
                    || (path.startsWith(said + File.separator) && sameRepository(folder, new File(said)))) {
                return false;
            }
        }
        return SAID.add(path);
    }

    /** Whether no {@code .git} lies between {@code folder} and the {@code above} it sits under. */
    private static boolean sameRepository(File folder, File above) {
        for (File d = folder; d != null && !d.equals(above); d = d.getParentFile()) {
            if (new File(d, ".git").exists()) {
                return false;
            }
        }
        return true;
    }

    /** Test hook: a fresh session. */
    public static void forgetForTest() {
        SAID.clear();
    }
}
