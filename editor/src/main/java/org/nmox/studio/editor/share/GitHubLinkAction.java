package org.nmox.studio.editor.share;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.rack.service.GitHubLinks;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * The editor half of Open on GitHub and Copy GitHub Link (3.2.0): the
 * caret's line, or the selection's line range, of the file in the focused
 * editor, as the GitHub page that shows it. What a developer reaches for
 * many times a day — to share a line, to read its blame where the
 * reviewers are, to start a review comment — and simpler than the
 * Markdown block beside it.
 *
 * <p>The editor refuses what only the editor can see: no focused editor,
 * a buffer with no file, and unsaved changes (the link would show lines
 * that differ from the buffer). Every other rung — not a repository, no
 * origin, not GitHub, no HEAD — is {@link GitHubLinks}', shared with Copy
 * as Markdown with Link and with Project Studio's tree, off the EDT.
 */
@Messages({
    "GitHubLinkAction_noFocus=no editor has focus",
    "GitHubLinkAction_noFile=the buffer has no file on disk to link",
    "# {0} - the file name",
    "GitHubLinkAction_unsaved={0} has unsaved changes — save first, so the link shows the lines you see"
})
abstract class GitHubLinkAction implements ActionListener {

    /** What this gesture does with the link. */
    abstract GitHubLinks.Gesture gesture();

    @Override
    public final void actionPerformed(ActionEvent e) {
        GitHubLinks.Gesture g = gesture();
        JTextComponent editor = CopyAsMarkdownAction.focusedEditor();
        if (editor == null) {
            refuse(g, Bundle.GitHubLinkAction_noFocus());
            return;
        }
        Document doc = editor.getDocument();
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        org.openide.filesystems.FileObject edited = EditedFile.of(doc);
        File file = edited == null ? null : FileUtil.toFile(edited);
        if (file == null) {
            refuse(g, Bundle.GitHubLinkAction_noFile());
            return;
        }
        if (CopyAsMarkdownWithLinkAction.unsaved(sd)) {
            refuse(g, Bundle.GitHubLinkAction_unsaved(file.getName()));
            return;
        }
        int[] lines = CopyAsMarkdown.linkLines(doc, editor.getSelectionStart(), editor.getSelectionEnd(),
                editor.getCaretPosition());
        // the git reads are disk and the browser can block: the shared lane takes it from here
        GitHubLinks.perform(g, file, lines[0], lines[1]);
    }

    private static void refuse(GitHubLinks.Gesture g, String reason) {
        StatusDisplayer.getDefault().setStatusText(PlainStatus.text(GitHubLinks.refused(g, reason)));
    }
}
