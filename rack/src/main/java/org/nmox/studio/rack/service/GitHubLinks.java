package org.nmox.studio.rack.service;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.GitFacts;
import org.nmox.studio.core.util.GitLink;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * The one way a file, a line range or a folder becomes a GitHub link
 * (3.2.0): Open on GitHub and Copy GitHub Link in the editor and on
 * Project Studio's tree, and the link under Copy as Markdown with Link
 * (v2.87.0, whose resolver this is, moved here so three surfaces share
 * ONE refusal ladder rather than three copies of it).
 *
 * <p>Everything is read from disk, no process: the repo root, the origin
 * remote (worktree-aware), HEAD. The ref is the checked-out branch — the
 * branch is what a reader can open, and a local sha that was never pushed
 * is a 404 dressed as a permalink — and a detached HEAD links by its short
 * sha. Every rung that cannot vouch for a link refuses out loud, in the
 * reader's language: not in a repository, no origin, an origin that is not
 * GitHub, an unreadable HEAD, a path that will not resolve inside the
 * repository.
 *
 * <p>A file links its {@code blob} (with {@code #L3-L14} when the caller
 * has lines); a folder links its {@code tree}, and the repository's root
 * folder links {@code tree/<ref>}. Opening goes to the user's own browser
 * ({@link ServingLinks#openInSystemBrowser}), where they are signed in —
 * not the in-app Browser, which is for the pages the project serves.
 */
@Messages({
    "GitHubLinks_open=Open on GitHub",
    "GitHubLinks_copy=Copy GitHub Link",
    "# {0} - the path and lines linked, {1} - owner/repo@branch",
    "GitHubLinks_opened=Opened {0} on GitHub at {1} (the page shows the branch as pushed)",
    "# {0} - the path and lines linked, {1} - owner/repo@branch, {2} - the URL",
    "GitHubLinks_copied=Copied the GitHub link to {0} at {1}: {2}",
    "# {0} - why no link could be made",
    "GitHubLinks_openRefused=Open on GitHub: {0}",
    "# {0} - why no link could be made",
    "GitHubLinks_copyRefused=Copy GitHub Link: {0}",
    "# {0} - the URL",
    "GitHubLinks_noBrowser=Open on GitHub: no browser could open {0} — Copy GitHub Link puts it on the clipboard instead",
    "# {0} - the file or folder",
    "GitHubLinks_notInRepo={0} is not inside a git repository",
    "GitHubLinks_noOrigin=the repository has no origin remote",
    "# {0} - the origin URL",
    "GitHubLinks_notGitHub=origin is not a GitHub remote ({0})",
    "GitHubLinks_noHead=HEAD could not be read",
    "GitHubLinks_pathUnresolved=the file's path inside the repository could not be resolved",
    "GitHubLinks_outsideRepo=the file is not inside the repository"
})
public final class GitHubLinks {

    /** What a gesture does with the link. */
    public enum Gesture { OPEN, COPY }

    private static final RequestProcessor RP = new RequestProcessor("nmox-github-link", 1, true);

    private GitHubLinks() {
    }

    /**
     * What a link needs, or the one reason it cannot be made. {@code url},
     * {@code relPath} (forward slashes, empty for a repository root),
     * {@code slug} ({@code owner/repo}) and {@code ref} are null exactly
     * when {@code refusal} is not.
     */
    public record Link(String url, String relPath, String slug, String ref, String refusal) {
        static Link refuse(String why) {
            return new Link(null, null, null, null, why);
        }
    }

    /** The row and menu name of each gesture, in the reader's language. */
    public static String label(Gesture g) {
        return g == Gesture.OPEN ? Bundle.GitHubLinks_open() : Bundle.GitHubLinks_copy();
    }

    /** "Open on GitHub: {reason}" / "Copy GitHub Link: {reason}" — how a gesture refuses. */
    public static String refused(Gesture g, String reason) {
        return g == Gesture.OPEN ? Bundle.GitHubLinks_openRefused(reason) : Bundle.GitHubLinks_copyRefused(reason);
    }

    /**
     * Off the EDT: repo root → origin → GitHub remote → HEAD → the link.
     * Pure over the disk facts. A directory {@code target} links its tree
     * (lines ignored); a file links its blob, with a fragment when
     * {@code startLine > 0}.
     */
    public static Link resolve(File target, int startLine, int endLine) {
        boolean folder = target.isDirectory();
        File root = GitFacts.repoRoot(folder ? target : target.getParentFile());
        if (root == null) {
            return Link.refuse(Bundle.GitHubLinks_notInRepo(target.getName()));
        }
        String origin = GitFacts.originUrl(root);
        if (origin == null) {
            return Link.refuse(Bundle.GitHubLinks_noOrigin());
        }
        GitLink.Remote remote = GitLink.parseRemote(origin);
        if (remote == null) {
            return Link.refuse(Bundle.GitHubLinks_notGitHub(origin));
        }
        String ref = GitFacts.branch(root);
        if (ref == null) {
            return Link.refuse(Bundle.GitHubLinks_noHead());
        }
        String rel;
        try {
            rel = root.toPath().toRealPath().relativize(target.toPath().toRealPath()).toString()
                    .replace(File.separatorChar, '/');
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return Link.refuse(Bundle.GitHubLinks_pathUnresolved());
        }
        // a folder may BE the root (tree/<ref>); a file never can
        if ((rel.isEmpty() && !folder) || rel.startsWith("..")) {
            return Link.refuse(Bundle.GitHubLinks_outsideRepo());
        }
        String url = folder
                ? GitLink.treeUrl(remote, ref, rel)
                : GitLink.blobUrl(remote, ref, rel, startLine, endLine);
        return new Link(url, rel, remote.slug(), ref, null);
    }

    /**
     * What the status line calls the thing linked: the path with its line
     * fragment ({@code src/App.jsx#L3-L14}), or {@code owner/repo} for a
     * repository root.
     */
    static String what(Link link, int startLine, int endLine) {
        if (link.relPath().isEmpty()) {
            return link.slug();
        }
        String label = link.relPath();
        if (startLine > 0) {
            label += "#L" + startLine + (endLine > startLine ? "-L" + endLine : "");
        }
        return label;
    }

    /**
     * The gesture, end to end, on this class's lane: resolve (disk), then
     * open in the user's browser (which may block on the desktop) or put
     * the URL on the clipboard, and say what happened either way.
     */
    public static void perform(Gesture g, File target, int startLine, int endLine) {
        RP.post(() -> act(g, target, startLine, endLine,
                ServingLinks::openInSystemBrowser,
                url -> SwingUtilities.invokeLater(() -> Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(url), null)),
                text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text))));
    }

    /** The seam: {@link #perform} with the browser, the clipboard and the status line handed in. */
    static void act(Gesture g, File target, int startLine, int endLine,
            Predicate<String> browser, Consumer<String> clipboard, Consumer<String> status) {
        Link link = resolve(target, startLine, endLine);
        if (link.refusal() != null) {
            status.accept(refused(g, link.refusal()));
            return;
        }
        String what = what(link, startLine, endLine);
        String at = link.slug() + "@" + link.ref();
        if (g == Gesture.COPY) {
            clipboard.accept(link.url());
            status.accept(Bundle.GitHubLinks_copied(what, at, link.url()));
        } else if (browser.test(link.url())) {
            status.accept(Bundle.GitHubLinks_opened(what, at));
        } else {
            status.accept(Bundle.GitHubLinks_noBrowser(link.url()));
        }
    }
}
