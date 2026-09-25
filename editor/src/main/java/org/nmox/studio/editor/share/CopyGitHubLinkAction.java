package org.nmox.studio.editor.share;

import org.nmox.studio.rack.service.GitHubLinks;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Edit ▸ Copy GitHub Link, and right-click ▸ the same (3.2.0): the GitHub
 * URL of the caret's line, or the selection's lines, onto the clipboard —
 * the link a chat message wants, without the Markdown block around it.
 * {@link GitHubLinkAction} holds the editor's refusals; the ladder is
 * {@link GitHubLinks}'.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyGitHubLinkAction")
@ActionRegistration(displayName = "#CTL_CopyGitHubLink", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1963),
    @ActionReference(path = "Menu/Edit", position = 1373)
})
@Messages("CTL_CopyGitHubLink=Copy GitHub Link")
public final class CopyGitHubLinkAction extends GitHubLinkAction {

    @Override
    GitHubLinks.Gesture gesture() {
        return GitHubLinks.Gesture.COPY;
    }
}
