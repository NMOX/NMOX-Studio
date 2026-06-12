package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.nmox.studio.tools.build.*;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

/**
 * Action to build web projects.
 */
@ActionID(
    category = "Build",
    id = "org.nmox.studio.ui.actions.BuildProjectAction"
)
@ActionRegistration(
    displayName = "#CTL_BuildProjectAction",
    iconBase = "org/nmox/studio/ui/icons/build.png"
)
@ActionReference(path = "Menu/Build", position = 100)
@Messages({
    "CTL_BuildProjectAction=Build Project",
    "MSG_SelectProject=Select a project directory"
})
public final class BuildProjectAction extends AbstractAction {

    private final BuildTaskManager taskManager;

    public BuildProjectAction() {
        super(NbBundle.getMessage(BuildProjectAction.class, "CTL_BuildProjectAction"));
        this.taskManager = BuildTaskManager.getInstance();
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        // Get current project directory (simplified for now)
        File projectDir = getCurrentProjectDirectory();
        if (projectDir == null) {
            JOptionPane.showMessageDialog(
                WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(BuildProjectAction.class, "MSG_SelectProject"),
                "Build",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Create build configuration
        BuildConfiguration config = BuildConfiguration.builder()
            .mode(BuildConfiguration.BuildMode.PRODUCTION)
            .minify(true)
            .sourceMaps(false)
            .build();

        // Start build task; progress reports into the Output window's Build tab
        taskManager.createBuildTask(projectDir, config);
    }

    private static File getCurrentProjectDirectory() {
        try {
            // the project the IDE is aimed at - the same source the
            // rack, studio, and workbench all share
            File dir = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        } catch (RuntimeException | LinkageError ex) {
            // rack unavailable (stripped platform); no project to build
        }
        return null;
    }
}