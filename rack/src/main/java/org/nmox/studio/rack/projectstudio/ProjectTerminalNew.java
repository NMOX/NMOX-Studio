package org.nmox.studio.rack.projectstudio;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Always a new shell, in the aimed project when it can be (after 3.2.0):
 * VS Code's <em>Terminal: Create New Terminal</em> starts one every time,
 * while ⌃` ({@link ProjectTerminal}) brings an open terminal forward. Quick
 * Search's row for that title fired ⌃`'s action until now, so the command
 * named "Create New" created nothing when a terminal was open. The rule is
 * {@link ProjectTerminal#openNew}, the one Project Studio's Terminal button
 * and the Workbench already use.
 */
@ActionID(category = "Window", id = "org.nmox.studio.rack.projectstudio.ProjectTerminalNewAction")
@ActionRegistration(displayName = "#CTL_ProjectTerminalNewAction")
@Messages("CTL_ProjectTerminalNewAction=New Terminal in Project")
public final class ProjectTerminalNew implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectTerminal.openNew(e.getSource());
    }
}
