package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

@ActionID(
        category = "File",
        id = "org.nmox.studio.ui.actions.NewProjectAction"
)
@ActionRegistration(
        iconBase = "org/nmox/studio/ui/resources/new-project.png",
        displayName = "#CTL_NewProjectAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 100),
    @ActionReference(path = "Toolbars/File", position = 100),
    @ActionReference(path = "Shortcuts", name = "D-N")
})
@Messages("CTL_NewProjectAction=New Project...")
public final class NewProjectAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
            "New Project wizard will be implemented here.",
            NotifyDescriptor.INFORMATION_MESSAGE
        );
        DialogDisplayer.getDefault().notify(msg);
    }
}