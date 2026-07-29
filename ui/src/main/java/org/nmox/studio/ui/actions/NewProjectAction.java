package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * File ▸ New Project… (⇧⌘N, and the toolbar button): opens the rack's
 * {@code NewProjectDialog} — template picker, name, location — and, when
 * a project was actually created, surfaces Project Studio for step two.
 * The dialog itself aims the rack at the new project; this action only
 * front-and-centers the studio window afterwards.
 *
 * <p>A stateless {@link ActionListener} the platform instantiates from
 * the {@code @ActionID}/{@code @ActionReference} registrations (menu +
 * toolbar entries in the generated layer). It runs on the EDT by
 * definition — the dialog is modal and does its own off-EDT scaffolding.
 */
@ActionID(
        category = "File",
        id = "org.nmox.studio.ui.actions.NewProjectAction"
)
@ActionRegistration(
        iconBase = "org/nmox/studio/ui/resources/new-project.png",
        displayName = "#CTL_NewProjectAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 110),
    @ActionReference(path = "Toolbars/File", position = 95),
    // no keyboard shortcut: D-N belongs to New File
    @ActionReference(path = "Shortcuts", name = "DS-N")
})
@Messages("CTL_NewProjectAction=New Project...")
public final class NewProjectAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        org.nmox.studio.rack.projectstudio.NewProjectDialog dialog =
                new org.nmox.studio.rack.projectstudio.NewProjectDialog(
                        WindowManager.getDefault().getMainWindow());
        dialog.setVisible(true);
        if (dialog.getCreatedProject() != null) {
            // the dialog aimed the rack; surface the studio for step two
            TopComponent studio = WindowManager.getDefault()
                    .findTopComponent("ProjectStudioTopComponent");
            if (studio != null) {
                studio.open();
                studio.requestActive();
            }
        }
    }
}
