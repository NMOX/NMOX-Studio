package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Project Studio: the project lifecycle tool. Start a project from a
 * template (sources + pre-wired rack patch), switch between recent
 * projects, CRUD the file tree, and edit package.json - all aimed at
 * the same rack the Task Rack window renders.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.rack//ProjectStudio//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "ProjectStudioTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 50)
@ActionID(category = "Window", id = "org.nmox.studio.rack.projectstudio.ProjectStudioTopComponent")
@ActionReference(path = "Menu/Window", position = 240)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProjectStudioAction",
        preferredID = "ProjectStudioTopComponent"
)
@Messages({
    "CTL_ProjectStudioAction=Project Studio",
    "CTL_ProjectStudioTopComponent=Project Studio",
    "HINT_ProjectStudioTopComponent=Start, configure and edit web projects wired to the Task Rack"
})
public final class ProjectStudioTopComponent extends TopComponent {

    private final Rack rack = RackService.getDefault().getRack();
    private final FileTreePanel treePanel = new FileTreePanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final Rack.Listener rackListener = new Rack.Listener() {
        @Override
        public void projectChanged() {
            SwingUtilities.invokeLater(ProjectStudioTopComponent.this::syncToRack);
        }
    };

    public ProjectStudioTopComponent() {
        setName(org.openide.util.NbBundle.getMessage(ProjectStudioTopComponent.class, "CTL_ProjectStudioTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(ProjectStudioTopComponent.class, "HINT_ProjectStudioTopComponent"));
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        add(treePanel, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(statusLabel, BorderLayout.SOUTH);
        // No syncToRack here: this tab is open-at-startup, so componentOpened
        // always follows construction and runs its own sync — a constructor
        // sync lists the project directory and spins up a FileWatcher twice
        // per boot.
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton newProject = new JButton("New…");
        newProject.setToolTipText("Create a project from a template, infra pre-wired in the rack");
        newProject.addActionListener(e -> {
            NewProjectDialog dialog = new NewProjectDialog(this);
            dialog.setVisible(true);
            // rack aiming happens inside the dialog; tree follows via listener
        });
        bar.add(newProject);

        JButton open = new JButton("Open…");
        open.setToolTipText("Aim the studio (and the rack) at an existing project");
        open.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(rack.getProjectDir());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Open Project Directory");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                RackService.getDefault().openProject(chooser.getSelectedFile());
            }
        });
        bar.add(open);

        JButton recent = new JButton("Recent ▾");
        recent.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            var projects = RackService.getDefault().getRecentProjects();
            if (projects.isEmpty()) {
                JMenuItem none = new JMenuItem("(no recent projects)");
                none.setEnabled(false);
                menu.add(none);
            }
            for (File dir : projects) {
                JMenuItem item = new JMenuItem(dir.getName());
                item.setToolTipText(dir.getAbsolutePath());
                item.addActionListener(a -> RackService.getDefault().openProject(dir));
                menu.add(item);
            }
            menu.show(recent, 0, recent.getHeight());
        });
        bar.add(recent);
        bar.addSeparator();

        JButton configure = new JButton("Configure…");
        configure.setToolTipText("Edit package.json: identity, scripts, dependencies");
        configure.addActionListener(e -> {
            try {
                new ProjectConfigDialog(this, rack.getProjectDir()).setVisible(true);
            } catch (IOException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        "Could not open the configuration: " + ex.getMessage(),
                        NotifyDescriptor.WARNING_MESSAGE));
            }
        });
        bar.add(configure);
        bar.addSeparator();

        JButton asProject = new JButton("Projects Tab");
        asProject.setToolTipText("Open this folder as a platform project: git colors, history,"
                + " diff and search light up in the Projects view");
        asProject.addActionListener(e -> {
            try {
                // toFileObject requires a normalized file; a project dir picked
                // via dialog or persisted state may carry '..' or a symlink
                org.openide.filesystems.FileObject dir = org.openide.filesystems.FileUtil
                        .toFileObject(org.openide.filesystems.FileUtil
                                .normalizeFile(rack.getProjectDir()));
                org.netbeans.api.project.Project project =
                        org.netbeans.api.project.ProjectManager.getDefault().findProject(dir);
                if (project != null) {
                    org.netbeans.api.project.ui.OpenProjects.getDefault()
                            .open(new org.netbeans.api.project.Project[]{project}, false, true);
                } else {
                    org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText("No recognized project manifest in this folder");
                }
            } catch (Exception ex) {
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText("Open as project failed: " + ex.getMessage());
            }
        });
        bar.add(asProject);

        JButton terminal = new JButton("Terminal");
        terminal.setToolTipText("Open the interactive terminal in the project directory");
        terminal.addActionListener(e -> openTerminal());
        bar.add(terminal);
        return bar;
    }

    private void syncToRack() {
        File dir = rack.getProjectDir();
        treePanel.setRootDirectory(dir);
        boolean isWebProject = new File(dir, "package.json").isFile();
        statusLabel.setText(dir.getAbsolutePath() + (isWebProject ? "" : "  (no package.json)"));
    }

    @Override
    public void componentOpened() {
        rack.addListener(rackListener);
        syncToRack();
    }

    @Override
    public void componentClosed() {
        rack.removeListener(rackListener);
        treePanel.dispose();
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }

    /** Finds the platform's terminal action wherever the module registered it. */
    private void openTerminal() {
        for (String id : new String[]{
                "org.netbeans.modules.dlight.terminal.action.LocalTerminalAction",
                "LocalTerminalAction"}) {
            javax.swing.Action action = org.openide.awt.Actions.forID("Window", id);
            if (action != null) {
                action.actionPerformed(new java.awt.event.ActionEvent(this, 0, "open"));
                return;
            }
        }
        // fallback: hunt the actions folder for anything terminal-flavored
        org.openide.filesystems.FileObject actions = org.openide.filesystems.FileUtil
                .getConfigFile("Actions/Window");
        if (actions != null) {
            for (org.openide.filesystems.FileObject child : actions.getChildren()) {
                if (child.getName().toLowerCase().contains("terminal")) {
                    try {
                        Object instance = org.openide.loaders.DataObject.find(child)
                                .getLookup().lookup(org.openide.cookies.InstanceCookie.class)
                                .instanceCreate();
                        if (instance instanceof javax.swing.Action action) {
                            action.actionPerformed(new java.awt.event.ActionEvent(this, 0, "open"));
                            return;
                        }
                    } catch (Exception ignored) {
                        // try the next candidate
                    }
                }
            }
        }
        org.openide.awt.StatusDisplayer.getDefault()
                .setStatusText("Terminal: Window → IDE Tools → Terminal");
    }
}
