package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;
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

        syncToRack();
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
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Project Configuration", JOptionPane.WARNING_MESSAGE);
            }
        });
        bar.add(configure);
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
}
