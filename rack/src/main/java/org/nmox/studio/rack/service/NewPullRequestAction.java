package org.nmox.studio.rack.service;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * Team ▸ New Pull Request on GitHub (3.2.0): GitHub's own compare page for
 * the aimed project's checked-out branch, in the user's own browser where
 * they are signed in — the step after a push, without leaving for the
 * browser to find the repository and the branch. The ladder (and its
 * refusals) is {@link GitHubLinks#resolvePullRequest}; the git chip offers
 * the same gesture beside Pull Requests….
 */
@ActionID(category = "Team", id = "org.nmox.studio.rack.service.NewPullRequestAction")
@ActionRegistration(displayName = "#CTL_NewPullRequestAction", lazy = true)
@ActionReference(path = "Menu/Versioning", position = 1987)
@Messages({
    "CTL_NewPullRequestAction=New Pull Request on GitHub",
    "NewPullRequestAction_noProject=New Pull Request: no project is aimed"
})
public final class NewPullRequestAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File dir = aimed();
        if (dir == null) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.NewPullRequestAction_noProject()));
            return;
        }
        GitHubLinks.openPullRequest(dir);
    }

    private static File aimed() {
        try {
            return RackService.getDefault().getRack().getProjectDir();
        } catch (RuntimeException noRack) {
            return null;
        }
    }
}
