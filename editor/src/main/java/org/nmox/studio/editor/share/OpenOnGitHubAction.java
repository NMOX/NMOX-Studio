package org.nmox.studio.editor.share;

import org.nmox.studio.rack.service.GitHubLinks;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Edit ▸ Open on GitHub, and right-click ▸ the same (3.2.0): the caret's
 * line, or the selection's lines, on GitHub in the user's own browser —
 * where they are signed in, so blame and review comments work.
 * {@link GitHubLinkAction} holds the editor's refusals; the ladder is
 * {@link GitHubLinks}'.
 */
@ActionID(category = "Edit", id = "org.nmox.studio.editor.share.OpenOnGitHubAction")
@ActionRegistration(displayName = "#CTL_OpenOnGitHub", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/Popup", position = 1962),
    @ActionReference(path = "Menu/Edit", position = 1372)
})
@Messages("CTL_OpenOnGitHub=Open on GitHub")
public final class OpenOnGitHubAction extends GitHubLinkAction {

    @Override
    GitHubLinks.Gesture gesture() {
        return GitHubLinks.Gesture.OPEN;
    }
}
