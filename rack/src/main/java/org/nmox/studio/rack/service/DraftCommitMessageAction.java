package org.nmox.studio.rack.service;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

/**
 * Team ▸ Draft Commit Message with KVASIR… (3.4): the git chip's draft of the
 * staged diff, from the menu bar, so a keyboard reaches it. The same path as
 * the chip's row ({@link GitStatusLine#fromTeamMenu}): the boot guard, the
 * keychain-only key, its own {@code git.diff} consent kind, and a draft that
 * lands in an editable dialog — nothing here commits.
 */
@ActionID(category = "Team", id = "org.nmox.studio.rack.service.DraftCommitMessageAction")
@ActionRegistration(displayName = "#CTL_DraftCommitMessageAction", lazy = true)
@ActionReference(path = "Menu/Versioning", position = 1989)
// the chip's own row name (GitStatusLine_draftCommit), so the two doors read alike
@org.openide.util.NbBundle.Messages("CTL_DraftCommitMessageAction=Draft Commit Message with KVASIR…")
public final class DraftCommitMessageAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        GitStatusLine.fromTeamMenu(false);
    }
}
