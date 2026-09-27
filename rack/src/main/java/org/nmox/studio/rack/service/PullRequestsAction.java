package org.nmox.studio.rack.service;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

/**
 * Team ▸ Pull Requests… (3.4): the git chip's open-pull-requests list, with
 * its Review Threads and Checkout, from the menu bar. The chip sits on the
 * status line where no Tab reaches, so until now a keyboard could not open
 * this at all. It runs the chip's own path — the user's own {@code gh}, the
 * same boot guard, the same refusals ({@link GitStatusLine#fromTeamMenu}).
 */
@ActionID(category = "Team", id = "org.nmox.studio.rack.service.PullRequestsAction")
@ActionRegistration(displayName = "#CTL_PullRequestsAction", lazy = true)
@ActionReference(path = "Menu/Versioning", position = 1988)
// the chip's own row name (GitStatusLine_pullRequests), so the two doors read alike
@org.openide.util.NbBundle.Messages("CTL_PullRequestsAction=Pull Requests…")
public final class PullRequestsAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        GitStatusLine.fromTeamMenu(true);
    }
}
