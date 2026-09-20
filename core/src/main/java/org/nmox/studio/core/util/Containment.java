package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

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
 * <h2>How the answer is resolved, and why not by one canonicalize</h2>
 *
 * The obvious spelling — {@code new File(root, relative).getCanonicalFile()}
 * — asks the PLATFORM to resolve a path whose last components usually do
 * not exist yet (three of the four callers are about to CREATE the file),
 * and the platforms disagree about that exactly. On POSIX,
 * {@code canonicalize_md.c} falls back to the longest prefix
 * {@code realpath()} accepts and appends the remainder, so an ancestor
 * symlink is resolved even when the leaf is absent. On Windows the same
 * call resolved the link when the whole path existed and did NOT when the
 * final component was missing — measured, as a windows-latest CI failure
 * on exactly the absent-leaf case while the present-leaf case beside it
 * was refused correctly. That asymmetry is a containment HOLE, and the
 * sharp end is a write: {@code DockerRecipes} names a file that does not
 * exist yet, so a recipe naming a not-yet-existing file behind a
 * symlinked directory would have been CREATED outside the project.
 *
 * <p>So the resolution is done here rather than asked for: walk up to the
 * deepest ancestor that actually exists, canonicalize THAT (a path whose
 * every component is real, which every platform resolves), then re-append
 * the components that were popped and {@link Path#normalize()}. This is
 * the POSIX fallback written in Java, so it behaves the same everywhere.
 *
 * <p>The normalize is only sound because of what it runs over: the prefix
 * is canonical and therefore link-free, and every popped component failed
 * an existence test and therefore is not a symlink either — so folding
 * {@code ..} lexically cannot step over a link and mean a different file
 * than an {@code open()} would. A {@code ..} that folds its way out of
 * the root is then caught by the ordinary containment check below.
 *
 * <p>The honest ceiling, unchanged by this and true of every spelling: a
 * BROKEN symlink cannot be resolved by any platform — canonicalization
 * hands back the link's own path — so a path through one is judged on
 * that spelling. It stays contained if the link's name is inside the
 * root, which is what the callers already had.
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
            // joined against the CANONICAL root, so the walk below always
            // starts from an absolute path and terminates at a real
            // filesystem root; the join itself is unchanged, including
            // File's own rule that an absolute-looking child is appended
            Path target = canonicalize(new File(base.toFile(), relative).toPath());
            // startsWith is segment-wise, so /a/bc never "starts with"
            // /a/b — the string-prefix spelling needed a trailing
            // separator to be correct, and one of the four forgot it
            if (target == null || !target.startsWith(base) || target.equals(base)) {
                return null;
            }
            return target.toFile();
        } catch (IOException | RuntimeException cannotProve) {
            // RuntimeException is deliberate and is the javadoc above made
            // true: InvalidPathException from toPath() is a guard that
            // cannot answer, and a guard that cannot answer must refuse
            // rather than throw into a caller that asked a yes/no question.
            // Measured on macOS, getCanonicalFile() already refuses the
            // characters that would reach it; a platform whose
            // canonicalization is more permissive must not be the one
            // platform where this guard throws.
            return null;
        }
    }

    /**
     * The canonical form of {@code raw} without asking the platform to
     * canonicalize components that do not exist: the deepest existing
     * ancestor is canonicalized, the popped names are re-appended, and
     * the result is normalized. Null when nothing along the path exists
     * at all, because a guard that cannot answer must refuse.
     *
     * <p>Existence is asked with {@link LinkOption#NOFOLLOW_LINKS}, so
     * the question is "is there an entry here" rather than "does this
     * lead somewhere". That stops the walk at a broken symlink instead
     * of popping it into the tail; both readings produce the same answer
     * for a broken link — canonicalizing one is a no-op either way — and
     * this one stops at the deepest thing the filesystem knows about,
     * which is what the next line wants to canonicalize.
     */
    private static Path canonicalize(Path raw) throws IOException {
        Deque<Path> popped = new ArrayDeque<>();
        Path probe = raw;
        while (probe != null && !Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
            Path name = probe.getFileName();
            if (name == null) {
                return null;
            }
            popped.addFirst(name);
            probe = probe.getParent();
        }
        if (probe == null) {
            return null;
        }
        Path resolved = probe.toFile().getCanonicalFile().toPath();
        for (Path name : popped) {
            resolved = resolved.resolve(name);
        }
        return resolved.normalize();
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
