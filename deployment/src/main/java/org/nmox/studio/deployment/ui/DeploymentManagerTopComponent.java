package org.nmox.studio.deployment.ui;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import org.nmox.studio.deployment.services.DeploymentService;
import org.openide.util.Lookup;

/**
 * Deployment Manager window for managing cloud deployments and microservices.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.deployment//DeploymentManager//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "DeploymentManagerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = false)
@ActionID(category = "Window", id = "org.nmox.studio.deployment.ui.DeploymentManagerTopComponent")
@ActionReference(path = "Menu/Window", position = 150)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_DeploymentManagerAction",
        preferredID = "DeploymentManagerTopComponent"
)
@Messages({
    "CTL_DeploymentManagerAction=Deployment Manager",
    "CTL_DeploymentManagerTopComponent=Deployment Manager",
    "HINT_DeploymentManagerTopComponent=Manage cloud deployments and microservices"
})
public final class DeploymentManagerTopComponent extends TopComponent implements ExplorerManager.Provider {

    private final ExplorerManager explorerManager = new ExplorerManager();
    private DeploymentService deploymentService;
    private JSplitPane splitPane;
    private OutlineView deploymentView;
    private JPanel detailPanel;
    private JToolBar toolbar;

    public DeploymentManagerTopComponent() {
        initComponents();
        setName(Bundle.CTL_DeploymentManagerTopComponent());
        setToolTipText(Bundle.HINT_DeploymentManagerTopComponent());
        
        // Get deployment service
        deploymentService = Lookup.getDefault().lookup(DeploymentService.class);
        
        // Initialize with deployments node
        explorerManager.setRootContext(createRootNode());
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create toolbar
        toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        
        // Create deployment view
        deploymentView = new OutlineView("Deployment");
        deploymentView.getOutline().setRootVisible(false);
        deploymentView.addPropertyColumn("status", "Status");
        deploymentView.addPropertyColumn("provider", "Provider");
        deploymentView.addPropertyColumn("environment", "Environment");
        deploymentView.addPropertyColumn("lastModified", "Last Modified");
        
        // Create detail panel
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Deployment Details"));
        
        // Add deployment info panel
        JPanel infoPanel = createInfoPanel();
        detailPanel.add(infoPanel, BorderLayout.CENTER);
        
        // Create split pane
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(deploymentView);
        splitPane.setBottomComponent(detailPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.6);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Create status bar
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
    }
    
    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        // New Deployment button
        JButton newDeployBtn = new JButton("New Deployment");
        newDeployBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/deploy-new.png")));
        newDeployBtn.addActionListener(this::onNewDeployment);
        toolbar.add(newDeployBtn);
        
        toolbar.addSeparator();
        
        // Deploy button
        JButton deployBtn = new JButton("Deploy");
        deployBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/deploy.png")));
        deployBtn.addActionListener(this::onDeploy);
        toolbar.add(deployBtn);
        
        // Rollback button
        JButton rollbackBtn = new JButton("Rollback");
        rollbackBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/rollback.png")));
        rollbackBtn.addActionListener(this::onRollback);
        toolbar.add(rollbackBtn);
        
        // Stop button
        JButton stopBtn = new JButton("Stop");
        stopBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/stop.png")));
        stopBtn.addActionListener(this::onStop);
        toolbar.add(stopBtn);
        
        toolbar.addSeparator();
        
        // Scale button
        JButton scaleBtn = new JButton("Scale");
        scaleBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/scale.png")));
        scaleBtn.addActionListener(this::onScale);
        toolbar.add(scaleBtn);
        
        // Logs button
        JButton logsBtn = new JButton("View Logs");
        logsBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/logs.png")));
        logsBtn.addActionListener(this::onViewLogs);
        toolbar.add(logsBtn);
        
        // Metrics button
        JButton metricsBtn = new JButton("Metrics");
        metricsBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/metrics.png")));
        metricsBtn.addActionListener(this::onViewMetrics);
        toolbar.add(metricsBtn);
        
        toolbar.add(Box.createHorizontalGlue());
        
        // Refresh button
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setIcon(new ImageIcon(getClass().getResource("/org/nmox/studio/deployment/resources/refresh.png")));
        refreshBtn.addActionListener(this::onRefresh);
        toolbar.add(refreshBtn);
        
        return toolbar;
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        // Deployment info will be populated when selection changes
        JLabel placeholderLabel = new JLabel("Select a deployment to view details");
        placeholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(placeholderLabel);
        
        return panel;
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        
        JLabel statusLabel = new JLabel("Ready");
        statusBar.add(statusLabel);
        
        return statusBar;
    }

    private Node createRootNode() {
        return new AbstractNode(Children.create(new DeploymentChildFactory(), true)) {
            @Override
            public String getDisplayName() {
                return "Deployments";
            }
        };
    }
    
    // Action handlers
    private void onNewDeployment(ActionEvent e) {
        // Open new deployment wizard
        SwingUtilities.invokeLater(() -> {
            NewDeploymentWizard wizard = new NewDeploymentWizard();
            wizard.setVisible(true);
        });
    }
    
    private void onDeploy(ActionEvent e) {
        // Deploy selected application
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Trigger deployment
        }
    }
    
    private void onRollback(ActionEvent e) {
        // Rollback selected deployment
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Trigger rollback
        }
    }
    
    private void onStop(ActionEvent e) {
        // Stop selected deployment
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Stop deployment
        }
    }
    
    private void onScale(ActionEvent e) {
        // Scale selected deployment
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Open scale dialog
        }
    }
    
    private void onViewLogs(ActionEvent e) {
        // View logs for selected deployment
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Open logs viewer
        }
    }
    
    private void onViewMetrics(ActionEvent e) {
        // View metrics for selected deployment
        Node[] selected = explorerManager.getSelectedNodes();
        if (selected.length > 0) {
            // Open metrics viewer
        }
    }
    
    private void onRefresh(ActionEvent e) {
        // Refresh deployment list
        if (deploymentService != null) {
            deploymentService.refreshDeployments();
        }
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    @Override
    public void componentOpened() {
        // Refresh deployment list when component opens
        if (deploymentService != null) {
            deploymentService.refreshDeployments();
        }
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
    
    /**
     * Factory for creating deployment nodes
     */
    private static class DeploymentChildFactory extends Children.Keys<String> {
        @Override
        protected void addNotify() {
            // Load deployments
            setKeys(new String[]{"app1", "app2", "service1"});
        }
        
        @Override
        protected Node[] createNodes(String key) {
            return new Node[]{new DeploymentNode(key)};
        }
    }
    
    /**
     * Node representing a deployment
     */
    private static class DeploymentNode extends AbstractNode {
        public DeploymentNode(String name) {
            super(Children.LEAF);
            setDisplayName(name);
        }
    }
    
    /**
     * Wizard for creating new deployments
     */
    private static class NewDeploymentWizard extends JDialog {
        public NewDeploymentWizard() {
            super((Frame) null, "New Deployment", true);
            setSize(600, 400);
            setLocationRelativeTo(null);
            
            // Add wizard panels here
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Deployment wizard coming soon..."), BorderLayout.CENTER);
            add(panel);
        }
    }
}