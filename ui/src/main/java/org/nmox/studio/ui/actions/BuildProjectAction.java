package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.nmox.studio.tools.build.*;
import org.nmox.studio.tools.build.ui.BuildOutputTopComponent;
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
    "MSG_SelectProject=Select a project directory",
    "MSG_BuildStarted=Build started for: ",
    "MSG_BuildFailed=Build failed: "
})
public final class BuildProjectAction extends AbstractAction {
    
    private final BuildTaskManager taskManager;
    private final BuildToolService buildService;
    
    public BuildProjectAction() {
        super(NbBundle.getMessage(BuildProjectAction.class, "CTL_BuildProjectAction"));
        this.taskManager = BuildTaskManager.getInstance();
        this.buildService = BuildToolService.getInstance();
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
        
        // Open build output window
        BuildOutputTopComponent output = BuildOutputTopComponent.getInstance();
        output.open();
        output.requestActive();
        
        // Detect build tool
        BuildToolService.BuildToolType toolType = buildService.detectBuildTool(projectDir);
        
        // Create build configuration based on detected tool
        BuildConfiguration config = BuildConfiguration.builder()
            .mode(BuildConfiguration.BuildMode.PRODUCTION)
            .minify(true)
            .sourceMaps(false)
            .build();
        
        // Start build task
        BuildTaskManager.BuildTask task = taskManager.createBuildTask(projectDir, config);
        
        // Show notification
        output.appendInfo(NbBundle.getMessage(BuildProjectAction.class, "MSG_BuildStarted") + 
            projectDir.getName() + " using " + toolType);
    }
    
    /**
     * Creates a submenu with build options.
     */
    public static JMenu createBuildMenu() {
        JMenu menu = new JMenu("Build");
        
        // Build for production
        JMenuItem buildProd = new JMenuItem("Build (Production)");
        buildProd.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            File projectDir = getCurrentProjectDirectory();
            if (projectDir != null) {
                BuildConfiguration config = BuildConfiguration.builder()
                    .mode(BuildConfiguration.BuildMode.PRODUCTION)
                    .minify(true)
                    .build();
                manager.createBuildTask(projectDir, config);
            }
        });
        menu.add(buildProd);
        
        // Build for development
        JMenuItem buildDev = new JMenuItem("Build (Development)");
        buildDev.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            File projectDir = getCurrentProjectDirectory();
            if (projectDir != null) {
                BuildConfiguration config = BuildConfiguration.builder()
                    .mode(BuildConfiguration.BuildMode.DEVELOPMENT)
                    .sourceMaps(true)
                    .build();
                manager.createBuildTask(projectDir, config);
            }
        });
        menu.add(buildDev);
        
        menu.addSeparator();
        
        // Start dev server
        JMenuItem serve = new JMenuItem("Start Dev Server");
        serve.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            File projectDir = getCurrentProjectDirectory();
            if (projectDir != null) {
                BuildConfiguration config = BuildConfiguration.builder()
                    .mode(BuildConfiguration.BuildMode.DEVELOPMENT)
                    .watch(true)
                    .port(3000)
                    .open(true)
                    .build();
                manager.createServeTask(projectDir, config);
            }
        });
        menu.add(serve);
        
        menu.addSeparator();
        
        // Run tests
        JMenuItem test = new JMenuItem("Run Tests");
        test.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            File projectDir = getCurrentProjectDirectory();
            if (projectDir != null) {
                BuildConfiguration config = BuildConfiguration.builder()
                    .mode(BuildConfiguration.BuildMode.TEST)
                    .build();
                manager.createTestTask(projectDir, config);
            }
        });
        menu.add(test);
        
        // Run lint
        JMenuItem lint = new JMenuItem("Run Lint");
        lint.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            File projectDir = getCurrentProjectDirectory();
            if (projectDir != null) {
                BuildConfiguration config = BuildConfiguration.builder().build();
                manager.createLintTask(projectDir, config);
            }
        });
        menu.add(lint);
        
        menu.addSeparator();
        
        // Stop all builds
        JMenuItem stopAll = new JMenuItem("Stop All Builds");
        stopAll.addActionListener(e -> {
            BuildTaskManager manager = BuildTaskManager.getInstance();
            manager.cancelAllTasks();
        });
        menu.add(stopAll);
        
        return menu;
    }
    
    private static File getCurrentProjectDirectory() {
        // This would normally get the current project from the IDE context
        // For now, return a default or prompt the user
        String userHome = System.getProperty("user.home");
        File projectsDir = new File(userHome, "WebProjects");
        if (projectsDir.exists() && projectsDir.isDirectory()) {
            File[] projects = projectsDir.listFiles(File::isDirectory);
            if (projects != null && projects.length > 0) {
                return projects[0]; // Return first project for simplicity
            }
        }
        return null;
    }
}