package org.nmox.studio.tools.npm;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.json.JSONObject;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.loaders.DataObject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * NPM Explorer window showing package.json scripts and dependencies.
 * Provides quick access to run npm commands.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.tools.npm//NpmExplorer//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "NpmExplorerTopComponent",
        iconBase = "org/nmox/studio/tools/npm/npm.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
@ActionID(category = "Window", id = "org.nmox.studio.tools.npm.NpmExplorerTopComponent")
@ActionReference(path = "Menu/Window", position = 333)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_NpmExplorerAction",
        preferredID = "NpmExplorerTopComponent"
)
@Messages({
    "CTL_NpmExplorerAction=NPM Explorer",
    "CTL_NpmExplorerTopComponent=NPM Explorer",
    "HINT_NpmExplorerTopComponent=Shows NPM scripts and dependencies"
})
public final class NpmExplorerTopComponent extends TopComponent {

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private NpmService npmService;
    private File currentProjectDir;

    public NpmExplorerTopComponent() {
        initComponents();
        setName(org.openide.util.NbBundle.getMessage(NpmExplorerTopComponent.class, "CTL_NpmExplorerTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(NpmExplorerTopComponent.class, "HINT_NpmExplorerTopComponent"));
        
        npmService = Lookup.getDefault().lookup(NpmService.class);
        if (npmService == null) {
            npmService = new NpmService(); // Fallback
        }
        
        refreshProjectView();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshProjectView());
        toolbar.add(refreshButton);
        
        JButton installButton = new JButton("Install");
        installButton.addActionListener(e -> runNpmCommand("install"));
        toolbar.add(installButton);
        
        add(toolbar, BorderLayout.NORTH);
        
        // Create tree
        rootNode = new DefaultMutableTreeNode("NPM Project");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        
        // Add double-click handler
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        handleNodeDoubleClick(node);
                    }
                }
            }
        });
        
        // Add popup menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem runItem = new JMenuItem("Run Script");
        runItem.addActionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null) {
                handleNodeDoubleClick(node);
            }
        });
        popup.add(runItem);
        
        tree.setComponentPopupMenu(popup);
        
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void refreshProjectView() {
        // Try to find current project directory
        FileObject projectDir = findProjectDirectory();
        if (projectDir == null) {
            rootNode.removeAllChildren();
            rootNode.setUserObject("No package.json found");
            treeModel.reload();
            return;
        }
        
        File dir = FileUtil.toFile(projectDir);
        currentProjectDir = dir;
        
        // Load package.json
        File packageJson = new File(dir, "package.json");
        if (!packageJson.exists()) {
            rootNode.removeAllChildren();
            rootNode.setUserObject("No package.json found");
            treeModel.reload();
            return;
        }
        
        try {
            String content = Files.readString(packageJson.toPath());
            JSONObject json = new JSONObject(content);
            
            rootNode.removeAllChildren();
            rootNode.setUserObject(json.optString("name", "NPM Project"));
            
            // Add scripts node
            if (json.has("scripts")) {
                DefaultMutableTreeNode scriptsNode = new DefaultMutableTreeNode("Scripts");
                JSONObject scripts = json.getJSONObject("scripts");
                for (String key : scripts.keySet()) {
                    DefaultMutableTreeNode scriptNode = new DefaultMutableTreeNode(key);
                    scriptNode.setUserObject(new ScriptInfo(key, scripts.getString(key)));
                    scriptsNode.add(scriptNode);
                }
                rootNode.add(scriptsNode);
            }
            
            // Add dependencies node
            if (json.has("dependencies")) {
                DefaultMutableTreeNode depsNode = new DefaultMutableTreeNode("Dependencies");
                JSONObject deps = json.getJSONObject("dependencies");
                for (String key : deps.keySet()) {
                    DefaultMutableTreeNode depNode = new DefaultMutableTreeNode(key + " " + deps.getString(key));
                    depsNode.add(depNode);
                }
                rootNode.add(depsNode);
            }
            
            // Add devDependencies node
            if (json.has("devDependencies")) {
                DefaultMutableTreeNode devDepsNode = new DefaultMutableTreeNode("Dev Dependencies");
                JSONObject devDeps = json.getJSONObject("devDependencies");
                for (String key : devDeps.keySet()) {
                    DefaultMutableTreeNode depNode = new DefaultMutableTreeNode(key + " " + devDeps.getString(key));
                    devDepsNode.add(depNode);
                }
                rootNode.add(devDepsNode);
            }
            
            treeModel.reload();
            
            // Expand all nodes
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
            
        } catch (IOException | org.json.JSONException ex) {
            Exceptions.printStackTrace(ex);
            rootNode.setUserObject("Error reading package.json");
            treeModel.reload();
        }
    }

    private FileObject findProjectDirectory() {
        // Try to get from current context
        Lookup.Result<DataObject> result = Utilities.actionsGlobalContext().lookupResult(DataObject.class);
        for (DataObject dobj : result.allInstances()) {
            FileObject file = dobj.getPrimaryFile();
            // Walk up to find package.json
            while (file != null) {
                if (file.getFileObject("package.json") != null) {
                    return file;
                }
                file = file.getParent();
            }
        }
        
        // Fallback: look in user.dir
        File userDir = new File(System.getProperty("user.dir"));
        if (new File(userDir, "package.json").exists()) {
            return FileUtil.toFileObject(userDir);
        }
        
        return null;
    }

    private void handleNodeDoubleClick(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof ScriptInfo) {
            ScriptInfo script = (ScriptInfo) userObject;
            runNpmCommand("run " + script.name);
        }
    }

    private void runNpmCommand(String command) {
        if (currentProjectDir == null) {
            JOptionPane.showMessageDialog(this, "No project directory found");
            return;
        }
        
        if (npmService != null) {
            SwingUtilities.invokeLater(() -> {
                npmService.runCommand(currentProjectDir, command);
            });
        }
    }

    @Override
    public void componentOpened() {
        refreshProjectView();
    }

    @Override
    public void componentClosed() {
        // Clean up if needed
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    /**
     * Helper class to store script information
     */
    private static class ScriptInfo {
        final String name;
        final String command;

        ScriptInfo(String name, String command) {
            this.name = name;
            this.command = command;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}