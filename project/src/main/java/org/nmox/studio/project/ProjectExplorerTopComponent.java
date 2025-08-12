package org.nmox.studio.project;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;

/**
 * Project Explorer window for NMOX Studio.
 * Provides navigation and management of project files and resources.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.project//ProjectExplorer//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "ProjectExplorerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
@ActionID(category = "Window", id = "org.nmox.studio.project.ProjectExplorerTopComponent")
@ActionReference(path = "Menu/Window", position = 200)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProjectExplorerAction",
        preferredID = "ProjectExplorerTopComponent"
)
@Messages({
    "CTL_ProjectExplorerAction=Project Explorer",
    "CTL_ProjectExplorerTopComponent=Project Explorer",
    "HINT_ProjectExplorerTopComponent=Project Explorer window for navigating files and resources"
})
public final class ProjectExplorerTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager explorerManager = new ExplorerManager();
    private BeanTreeView treeView;

    public ProjectExplorerTopComponent() {
        initComponents();
        setName(NbBundle.getMessage(ProjectExplorerTopComponent.class, "CTL_ProjectExplorerTopComponent"));
        setToolTipText(NbBundle.getMessage(ProjectExplorerTopComponent.class, "HINT_ProjectExplorerTopComponent"));
        
        // Initialize with root node
        explorerManager.setRootContext(createRootNode());
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create tree view for project navigation
        treeView = new BeanTreeView();
        treeView.setRootVisible(true);
        add(new JScrollPane(treeView), BorderLayout.CENTER);
    }

    private Node createRootNode() {
        return new AbstractNode(Children.create(new ProjectChildFactory(), true)) {
            @Override
            public String getDisplayName() {
                return "Projects";
            }
            
            @Override
            public String getShortDescription() {
                return "Project workspace root";
            }
        };
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public void componentOpened() {
        // Refresh project list when component opens
    }

    @Override
    public void componentClosed() {
        // Cleanup when component closes
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
}