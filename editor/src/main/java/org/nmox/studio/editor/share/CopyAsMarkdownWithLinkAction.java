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
 * without its promised link is the wrong clipboard.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyAsMarkdownWithLinkAction")
@ActionRegistration(displayName = "#CTL_CopyAsMarkdownWithLink", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1961),
    @ActionReference(path = "Menu/Edit", position = 1371)
})
@Messages("CTL_CopyAsMarkdownWithLink=Copy as Markdown with Link")
public final class CopyAsMarkdownWithLinkAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-share-link", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = CopyAsMarkdownAction.focusedEditor();
        if (editor == null) {
            StatusDisplayer.getDefault().setStatusText("Copy as Markdown with Link: no editor has focus");
            return;
        }
        Document doc = editor.getDocument();
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        File file = sd instanceof DataObject dob ? FileUtil.toFile(dob.getPrimaryFile()) : null;
        if (file == null) {
            StatusDisplayer.getDefault().setStatusText("Copy as Markdown with Link: the buffer has no file on disk to link");
            return;
        }
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        String code;
        try {
            code = selEnd > selStart ? doc.getText(selStart, selEnd - selStart) : doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            StatusDisplayer.getDefault().setStatusText("Copy as Markdown with Link: could not read the buffer");
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
                    StatusDisplayer.getDefault().setStatusText(PlainStatus.text("Copy as Markdown with Link: " + out.refusal));
                    return;
                }
                String text = block + "\n" + GitLink.linkLine(out.relPath, lines[0], lines[1], out.url) + "\n";
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text("Copied "
                        + (lines[0] == 0 ? "the whole of " + name : "the selection") + " as Markdown with a GitHub link — "
                        + Plural.of(CopyAsMarkdown.lineCount(code), "line") + " in a ```" + CopyAsMarkdown.fence(mime, name)
                        + " block, " + out.slug + "@" + out.ref));
            });
        });
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
            return Outcome.refuse(file.getName() + " is not inside a git repository");
        }
        String origin = GitFacts.originUrl(root);
        if (origin == null) {
            return Outcome.refuse("the repository has no origin remote");
        }
        GitLink.Remote remote = GitLink.parseRemote(origin);
        if (remote == null) {
            return Outcome.refuse("origin is not a GitHub remote (" + origin + ")");
        }
        String ref = GitFacts.branch(root);
        if (ref == null) {
            return Outcome.refuse("HEAD could not be read");
        }
        String rel;
        try {
            rel = root.toPath().toRealPath().relativize(file.toPath().toRealPath()).toString().replace(File.separatorChar, '/');
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return Outcome.refuse("the file's path inside the repository could not be resolved");
        }
        if (rel.isEmpty() || rel.startsWith("..")) {
            return Outcome.refuse("the file is not inside the repository");
        }
        return new Outcome(GitLink.blobUrl(remote, ref, rel, startLine, endLine), rel, remote.slug(), ref, null);
    }
}
