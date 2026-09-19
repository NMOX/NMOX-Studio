package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The one containment guard: resolve a caller-supplied relative path
 * against a root and answer ONLY when the result really lives inside
 * that root.
 *
 * <p>Four surfaces used to spell this themselves — a learning space's
 * sample files, the Dockerize writer, the bundled site server and the
 * Browser's page-to-source resolver — and three of the four javadocs
 * called themselves the canonical one while disagreeing about what
 * containment means (ledger 111). One home now, with both policies
 * decided once and written down here, because a guard whose rule
 * lives in four places is four rules.
 *
 * <h2>The symlink policy: the answer is the RESOLVED path</h2>
 *
 * Both sides are canonicalized, and the path returned is the
 * canonical one — never the path as the caller spelled it. This is
 * not a detail. {@code LearningSpace.resolveInside} canonicalized
 * both sides to make its decision and then returned
 * {@code new File(dir, path)}, so a caller that PASSED the check went
 * on to open a path the check had never looked at: if a segment was a
 * symlink, the guard judged the link's target and the caller followed
 * the link. The check and the read must name the same file, so the
 * guard hands back the file it actually judged.
 *
 * <p>A symlink pointing back INSIDE the root is therefore fine — it
 * resolves to a contained file and is accepted. Only a link that
 * leaves the root is refused, which is the whole point.
 *
 * <h2>The root-equals-root policy: the root itself is REFUSED</h2>
 *
 * Every caller of this guard is asking "which FILE inside the root?"
 * — three test {@code isFile()} on the answer and the fourth writes
 * bytes to it. The root directory is never a valid answer to that
 * question, so accepting it would only move the refusal one step
 * later: into a silent {@code isFile()} false, or — on the two write
 * paths — into a raw "Is a directory" from the OS, a refusal that no
 * longer names containment and no longer belongs to the surface that
 * should be speaking.
 *
 * <p>The case that would argue the other way does not exist here: a
 * served docroot's own index is {@code root/index.html}, not
 * {@code root}. {@code SiteServer} appends {@code index.html} before
 * it ever calls, so a directory request already asks for a file.
 * {@code SiteServer} and {@code DockerRecipes} used to accept
 * root-equals-root and now refuse it; {@code LearningSpace} and
 * {@code PageSourceResolver} already refused it, each in its own
 * spelling.
 *
 * <h2>What this guard does NOT decide</h2>
 *
 * The refusal message. Each surface speaks for itself — a 404 for the
 * site server, a named {@code IOException} for the Dockerize writer, a
 * skipped sample file for a drop-in catalog — and those sentences
 * differ legitimately, so they stay where they are said.
 *
 * <p>Note that an absolute-looking relative (say {@code /etc/passwd})
 * is JOINED by {@link File#File(File, String)} rather than escaping:
 * it lands at {@code root/etc/passwd} and is contained. That is the
 * platform's own rule, it is the behaviour every caller already had,
 * and it is safe — so it is kept, and pinned by a test.
 */
public final class Containment {

    private Containment() {
    }

    /**
     * The canonical file at {@code relative} inside {@code root}, or
     * null when it is not inside — including when containment cannot
     * be PROVEN, because a guard that cannot answer must refuse.
     *
     * @param root the directory the answer must stay within
     * @param relative the caller-supplied path, resolved against it
     * @return the canonical contained file, or null if refused
     */
    public static File resolve(File root, String relative) {
        if (root == null || relative == null || relative.isBlank()) {
            return null;
        }
        try {
            Path base = root.getCanonicalFile().toPath();
            Path target = new File(root, relative).getCanonicalFile().toPath();
            // startsWith is segment-wise, so /a/bc never "starts with"
            // /a/b — the string-prefix spelling needed a trailing
            // separator to be correct, and one of the four forgot it
            if (!target.startsWith(base) || target.equals(base)) {
                return null;
            }
            return target.toFile();
        } catch (IOException | SecurityException cannotProve) {
            return null;
        }
    }

    /**
     * {@link #resolve} for callers that speak {@code java.nio} — the
     * same decision and the same canonical answer, shaped as a
     * {@link Path}; null when refused.
     */
    public static Path resolvePath(File root, String relative) {
        File resolved = resolve(root, relative);
        return resolved == null ? null : resolved.toPath();
    }
}
