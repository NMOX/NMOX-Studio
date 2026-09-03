package org.nmox.studio.ui.whatsnew;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/** Help ▸ What's New… — the bundled release notes for the running version. */
@ActionID(category = "Help", id = "org.nmox.studio.ui.whatsnew.WhatsNewAction")
@ActionRegistration(displayName = "#CTL_WhatsNewAction", lazy = true)
@ActionReference(path = "Menu/Help", position = 226)
@Messages("CTL_WhatsNewAction=What's New…")
public final class WhatsNewAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        WhatsNew.showCurrent();
    }
}
