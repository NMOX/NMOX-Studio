package org.nmox.studio.editor.share;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nmox.studio.core.util.GitFacts;
import org.nmox.studio.core.util.GitLink;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.Plural;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Edit ▸ Copy as Markdown with Link, and right-click ▸ the same
 * (v2.87.0): the fenced block Copy as Markdown makes, followed by the
 * GitHub link to the same lines — {@code [src/App.jsx#L3-L14](https://github.com/o/r/blob/main/src/App.jsx#L3-L14)}
 * — for the issue, the review comment or the post that wants "see the
 * whole file here". The ref is the checked-out branch (a detached HEAD
 * links by sha): the branch is what a reader can open, and a local sha
 * that was never pushed is a 404 dressed as a permalink. Everything is
 * read from disk, no process, off the EDT: the repo root, the origin
 * remote (worktree-aware), HEAD. Every step that cannot vouch for a link
 * refuses out loud — not in a repo, no origin, an origin that is not
 * GitHub, an unsaved buffer — and copies nothing, because a block
 * without its promised link is the wrong clipboard. Written limit:
 * {@code url.<base>.insteadOf} rewrites in the git config are not
 * applied, so an aliased origin ({@code gh:o/r}) refuses as "not a
 * GitHub remote" rather than guessing the alias.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyAsMarkdownWithLinkAction")
@ActionRegistration(displayName = "#CTL_CopyAsMarkdownWithLink", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1961),
    @ActionReference(path = "Menu/Edit", position = 1371)
})
@Messages({
    "CTL_CopyAsMarkdownWithLink=Copy as Markdown with Link",
    "CopyAsMarkdownWithLinkAction_noFocus=Copy as Markdown with Link: no editor has focus",
    "CopyAsMarkdownWithLinkAction_noFile=Copy as Markdown with Link: the buffer has no file on disk to link",
    "CopyAsMarkdownWithLinkAction_unsaved=Copy as Markdown with Link: {0} has unsaved changes — save first, so the block matches what the link shows",
    "CopyAsMarkdownWithLinkAction_couldNotRead=Copy as Markdown with Link: could not read the buffer",
    "CopyAsMarkdownWithLinkAction_refused=Copy as Markdown with Link: {0}",
    "CopyAsMarkdownWithLinkAction_copied=Copied {0} as Markdown with a GitHub link — {1} in a ```{2} block, {3} (the link shows the branch as pushed)",
    "CopyAsMarkdownWithLinkAction_wholeOf=the whole of {0}",
    "CopyAsMarkdownWithLinkAction_theSelection=the selection",
    "CopyAsMarkdownWithLinkAction_notInRepo={0} is not inside a git repository",
    "CopyAsMarkdownWithLinkAction_noOrigin=the repository has no origin remote",
    "CopyAsMarkdownWithLinkAction_notGitHub=origin is not a GitHub remote ({0})",
    "CopyAsMarkdownWithLinkAction_noHead=HEAD could not be read",
    "CopyAsMarkdownWithLinkAction_pathUnresolved=the file's path inside the repository could not be resolved",
    "CopyAsMarkdownWithLinkAction_outsideRepo=the file is not inside the repository"
})
public final class CopyAsMarkdownWithLinkAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-share-link", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = CopyAsMarkdownAction.focusedEditor();
        if (editor == null) {
            StatusDisplayer.getDefault().setStatusText(Bundle.CopyAsMarkdownWithLinkAction_noFocus());
            return;
        }
        Document doc = editor.getDocument();
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        File file = sd instanceof DataObject dob ? FileUtil.toFile(dob.getPrimaryFile()) : null;
        if (file == null) {
            StatusDisplayer.getDefault().setStatusText(Bundle.CopyAsMarkdownWithLinkAction_noFile());
            return;
        }
        if (unsaved(sd)) {
            // the block would be the BUFFER while the link names the file as committed: a block
            // that does not match its link is a lie, so the gesture waits for a save (the
            // review's find; the disk-vs-remote gap it cannot see is named in the status)
            StatusDisplayer.getDefault().setStatusText(Bundle.CopyAsMarkdownWithLinkAction_unsaved(file.getName()));
            return;
        }
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String code;
        try {
            code = selEnd > selStart ? doc.getText(selStart, selEnd - selStart) : doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            StatusDisplayer.getDefault().setStatusText(Bundle.CopyAsMarkdownWithLinkAction_couldNotRead());
            return;
        }
        int[] lines = CopyAsMarkdown.lineRange(doc, selStart, selEnd);
        String mime = CopyAsMarkdownAction.mimeOf(doc);
        String name = file.getName();
        String block = CopyAsMarkdown.block(code, mime, name);
        // the git reads are disk: off the EDT, then back for the clipboard and the status line
        RP.post(() -> {
            Outcome out = resolve(file, lines[0], lines[1]);
            SwingUtilities.invokeLater(() -> {
                if (out.refusal != null) {
                    StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.CopyAsMarkdownWithLinkAction_refused(out.refusal)));
                    return;
                }
                String text = block + "\n" + GitLink.linkLine(out.relPath, lines[0], lines[1], out.url) + "\n";
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.CopyAsMarkdownWithLinkAction_copied(
                        lines[0] == 0 ? Bundle.CopyAsMarkdownWithLinkAction_wholeOf(name) : Bundle.CopyAsMarkdownWithLinkAction_theSelection(),
                        Plural.of(CopyAsMarkdown.lineCount(code), "line"), CopyAsMarkdown.fence(mime, name),
                        out.slug + "@" + out.ref)));
            });
        });
    }

    /** The buffer-vs-disk gap the product can see: a modified DataObject means the block would not match the link. */
    static boolean unsaved(Object streamDescription) {
        return streamDescription instanceof DataObject dob && dob.isModified();
    }

    /** What the link needs, or the one reason it cannot be made. */
    record Outcome(String url, String relPath, String slug, String ref, String refusal) {
        static Outcome refuse(String why) {
            return new Outcome(null, null, null, null, why);
        }
    }

    /** Off the EDT: repo root → origin → GitHub remote → HEAD → the link. Pure over the disk facts. */
    static Outcome resolve(File file, int startLine, int endLine) {
        File root = GitFacts.repoRoot(file.getParentFile());
        if (root == null) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_notInRepo(file.getName()));
        }
        String origin = GitFacts.originUrl(root);
        if (origin == null) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_noOrigin());
        }
        GitLink.Remote remote = GitLink.parseRemote(origin);
        if (remote == null) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_notGitHub(origin));
        }
        String ref = GitFacts.branch(root);
        if (ref == null) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_noHead());
        }
        String rel;
        try {
            rel = root.toPath().toRealPath().relativize(file.toPath().toRealPath()).toString().replace(File.separatorChar, '/');
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_pathUnresolved());
        }
        if (rel.isEmpty() || rel.startsWith("..")) {
            return Outcome.refuse(Bundle.CopyAsMarkdownWithLinkAction_outsideRepo());
        }
        return new Outcome(GitLink.blobUrl(remote, ref, rel, startLine, endLine), rel, remote.slug(), ref, null);
    }
}
