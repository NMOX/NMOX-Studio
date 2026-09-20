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
 * <h2>The broken-link rule: judge the link's own recorded target</h2>
 *
 * A BROKEN symlink — an entry that is there but leads nowhere — cannot
 * be resolved by any platform: canonicalization hands back the link's
 * own path. Every earlier spelling of this guard therefore judged such
 * a path on that SPELLING, and ledger 117 recorded it as an honest
 * ceiling.
 *
 * <p>It was not honest, because the recorded reading of it was wrong in
 * both directions, and both were measured on the shipped code:
 *
 * <ul>
 *   <li>The hazard was written down as a LATER one — a link whose target
 *       "would be followed out if that target were later created". That
 *       is the case the guard already handled: once the target exists
 *       the link RESOLVES, the walk below canonicalizes it, and an
 *       escape is refused. Measured: the verdict for {@code pub/x.txt}
 *       behind a dangling {@code pub} goes from contained to null the
 *       moment the target appears.</li>
 *   <li>The live hazard was the opposite of that, and immediate. A
 *       broken link as the FINAL component was answered as contained,
 *       and {@link Files#writeString} opens with {@code CREATE} — which
 *       follows a dangling link and creates its target. Measured: with
 *       {@code proj/Dockerfile} a link to an absent
 *       {@code OUTSIDE/pwned.txt}, this guard said contained and the
 *       write created {@code OUTSIDE/pwned.txt} holding the caller's
 *       bytes. No waiting, and no link ever resolving.</li>
 * </ul>
 *
 * <p>So the answer for a broken link is read from the link itself:
 * {@link Files#readSymbolicLink} gives the target it records, that is
 * resolved against the link's own directory, and the whole question is
 * asked again of THAT path. An escape is refused; a dangling link whose
 * target is inside the root is still accepted, which keeps the symlink
 * policy above true rather than carving an exception into it.
 *
 * <p>This is deliberately NOT the rule ledger 117 proposed — "refuse
 * when the canonicalised ancestor is ITSELF still a symlink" — because
 * that also refuses a dangling link pointing back INSIDE the root,
 * which is contained and which the policy two sections up promises to
 * accept.
 *
 * <p>A chain of broken links is followed, bounded; a cycle exhausts the
 * bound and is refused, because a guard that cannot answer must refuse.
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

    /**
     * How many broken links the resolution will follow before refusing.
     * Real filesystems nest a handful at most; the bound is here so a
     * cycle of dangling links cannot spin, not to express a budget.
     */
    private static final int MAX_BROKEN_LINK_HOPS = 16;

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
     * of popping it into the tail, which is what lets the broken-link
     * rule below see one at all: popped into the tail it would be
     * indistinguishable from a name that does not exist.
     *
     * <p>When that deepest entry IS a broken link, no canonicalization
     * can say where it points, so the link is asked directly and the
     * question restarts against its recorded target. Bounded, because a
     * cycle of dangling links would otherwise never terminate.
     */
    private static Path canonicalize(Path raw) throws IOException {
        Path current = raw;
        for (int hop = 0; hop <= MAX_BROKEN_LINK_HOPS; hop++) {
            Deque<Path> popped = new ArrayDeque<>();
            Path probe = current;
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
            // "an entry is here" AND "it leads nowhere" is exactly a broken
            // link — a link that resolves takes the canonicalize path below,
            // where it already behaved correctly and is pinned by tests
            if (Files.isSymbolicLink(probe) && !Files.exists(probe)) {
                Path recorded = Files.readSymbolicLink(probe);
                Path from = probe.getParent();
                // a relative link target is relative to the link's OWN
                // directory; resolve() returns an absolute target unchanged
                Path rebased = from == null ? recorded : from.resolve(recorded);
                for (Path name : popped) {
                    rebased = rebased.resolve(name);
                }
                current = rebased;
                continue;
            }
            Path resolved = probe.toFile().getCanonicalFile().toPath();
            for (Path name : popped) {
                resolved = resolved.resolve(name);
            }
            return resolved.normalize();
        }
        // a chain this long is a cycle or an attack; either way the guard
        // has no answer, and a guard that cannot answer must refuse
        return null;
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
