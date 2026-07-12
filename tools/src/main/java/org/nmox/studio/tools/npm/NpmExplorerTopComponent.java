package org.nmox.studio.tools.npm;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.json.JSONObject;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.util.Lookup;
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
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 70)
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

    private static final Logger LOG = Logger.getLogger(NpmExplorerTopComponent.class.getName());

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JButton installButton;
    private NpmService npmService;
    private org.nmox.studio.core.spi.ProjectAim rackRef;
    private org.nmox.studio.core.spi.ProjectAim.Listener rackListener;
    private File currentProjectDir;
    /** A refresh is owed but the tab is hidden; served on componentShowing. */
    boolean refreshPending;

    public NpmExplorerTopComponent() {
        initComponents();
        setName(org.openide.util.NbBundle.getMessage(NpmExplorerTopComponent.class, "CTL_NpmExplorerTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(NpmExplorerTopComponent.class, "HINT_NpmExplorerTopComponent"));
        
        npmService = Lookup.getDefault().lookup(NpmService.class);
        if (npmService == null) {
            npmService = new NpmService(); // Fallback
        }

        // follow the aimed project: when the rack re-aims, this explorer
        // re-reads the new project's package.json automatically (the
        // listener is subscribed in componentOpened, unsubscribed in
        // componentClosed, so close/reopen cycles never stack copies).
        // Soft dependency by lookup (ledger 30): a null provider means the
        // rack is absent (plain tests) — manual Refresh still works.
        rackRef = org.nmox.studio.core.spi.ProjectAim.find();
        if (rackRef != null) {
            rackListener = new org.nmox.studio.core.spi.ProjectAim.Listener() {
                @Override
                public void projectChanged() {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        // hidden tabs take a note instead of refreshing: the
                        // no-project branch spawns npm, and a re-aim storm
                        // must not turn into a process storm behind a tab
                        // nobody can see
                        if (isShowing()) {
                            refreshProjectView();
                        } else {
                            refreshPending = true;
                        }
                    });
                }
            };
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshProjectView());
        toolbar.add(refreshButton);
        
        installButton = new JButton("Install");
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
            showGlobalPackages();
            return;
        }

        File dir = FileUtil.toFile(projectDir);
        currentProjectDir = dir;

        // Load package.json
        File packageJson = new File(dir, "package.json");
        if (!packageJson.exists()) {
            showGlobalPackages();
            return;
        }
        installButton.setEnabled(true);
        installButton.setToolTipText("npm install in the project");

        try {
            String content = Files.readString(packageJson.toPath(), java.nio.charset.StandardCharsets.UTF_8);
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
            // a malformed package.json is usually one the user is editing in
            // this very IDE right now — say why in the tree, never pop the
            // exception dialog over their typing
            LOG.log(Level.INFO, "package.json unreadable: {0}", ex.getMessage());
            rootNode.removeAllChildren();
            rootNode.setUserObject("Error reading package.json: " + ex.getMessage());
            treeModel.reload();
        }
    }

    /**
     * No project (or no package.json) is not a dead end: show what npm has
     * installed globally instead. Install is disabled — it means "npm install
     * in the project", and there is no project.
     */
    private void showGlobalPackages() {
        currentProjectDir = null;
        installButton.setEnabled(false);
        installButton.setToolTipText("Open a project to install its dependencies");
        rootNode.removeAllChildren();
        rootNode.setUserObject("Global packages (npm -g)");
        rootNode.add(new DefaultMutableTreeNode("Loading…"));
        treeModel.reload();
        npmService.listGlobalPackages().whenComplete((packages, error) ->
            SwingUtilities.invokeLater(() -> {
                // a newer refresh may have found a project meanwhile; don't
                // clobber its tree with our stale global listing
                if (currentProjectDir != null) {
                    return;
                }
                rootNode.removeAllChildren();
                if (error != null) {
                    rootNode.setUserObject("Global packages (npm -g)");
                    rootNode.add(new DefaultMutableTreeNode(
                            "npm not found — install Node.js (brew install node)"));
                } else {
                    rootNode.setUserObject("Global packages (npm -g) — "
                            + packages.size());
                    for (NpmService.GlobalPackage pkg : packages) {
                        rootNode.add(new DefaultMutableTreeNode(
                                pkg.name() + "  " + pkg.version()));
                    }
                    if (packages.isEmpty()) {
                        rootNode.add(new DefaultMutableTreeNode("(none installed)"));
                    }
                }
                treeModel.reload();
                tree.expandPath(new TreePath(rootNode.getPath()));
            }));
    }

    private FileObject findProjectDirectory() {
        // First choice: the project the IDE is aimed at (rack/workbench),
        // using the Node manifest directory in mixed monorepos. Soft aim
        // lookup (ledger 30): no provider (plain tests) falls through to
        // context detection.
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            File aimed = org.nmox.studio.rack.devices.ProjectInspector.kindDir(
                    aim.projectDir(),
                    org.nmox.studio.rack.devices.ProjectInspector.ProjectKind.NODE);
            if (aimed != null && new File(aimed, "package.json").isFile()) {
                return FileUtil.toFileObject(aimed);
            }
        }

        // Fall back to the window system's current selection. The registry
        // keeps the last real selection even while this explorer itself is
        // the activated component (it never sets activated nodes of its
        // own) — actionsGlobalContext() would read THIS component's empty
        // lookup the moment the user clicks Refresh here.
        for (Node n : TopComponent.getRegistry().getActivatedNodes()) {
            DataObject dobj = n.getLookup().lookup(DataObject.class);
            if (dobj == null) {
                continue;
            }
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
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "No project directory found", NotifyDescriptor.INFORMATION_MESSAGE));
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
        if (rackRef != null && rackListener != null) {
            rackRef.addListener(rackListener);
        }
        // No refresh here: open-at-startup tabs get componentOpened during
        // window-system load while hidden behind the selected tab, and the
        // no-project fallback spawns `npm ls -g` — a process this IDE has no
        // business running for a tab nobody is looking at. The refresh waits
        // for componentShowing (the DB Studio Docker-offer idiom), which the
        // window system also fires at startup for the tab that IS selected.
        refreshPending = true;
    }

    @Override
    protected void componentShowing() {
        if (refreshPending) {
            refreshPending = false;
            refreshProjectView();
        }
    }

    @Override
    public void componentClosed() {
        if (rackRef != null && rackListener != null) {
            rackRef.removeListener(rackListener);
        }
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