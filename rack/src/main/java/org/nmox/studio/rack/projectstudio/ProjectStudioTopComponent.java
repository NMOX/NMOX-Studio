package org.nmox.studio.rack.projectstudio;

import org.nmox.studio.core.util.PlainText;
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
            // selection follows the aim only while showing; componentShowing
            // catches up on whatever moved while hidden (ledger 29)
            if (aimNodeShowing) {
                aimPublisher.publish(rack.getProjectDir());
            }
        }
    };

    /**
     * Ledger 29 (v1.45.0, file-selection remainder v1.48.0): the tree's
     * selected file — or the aimed directory when nothing is selected —
     * becomes this window's activated nodes, so the global selection
     * (and the platform's context actions: Team menu, git Annotate on
     * a FILE) sees what the studio is showing whenever it is active.
     * The default TopComponent lookup proxies activated nodes.
     */
    private final org.nmox.studio.rack.service.AimNodePublisher aimPublisher =
            new org.nmox.studio.rack.service.AimNodePublisher(node ->
                    setActivatedNodes(new org.openide.nodes.Node[]{node}));

    /**
     * True between componentShowing and componentHidden/Closed: a hidden
     * default-open tab must resolve nothing at boot (the v1.38.0 law).
     * Volatile — the rack listener fires off-EDT on async switches.
     */
    private volatile boolean aimNodeShowing;

    /** Off-EDT lane for the "Open as project" manifest scan (disk IO). */
    private static final org.openide.util.RequestProcessor OPEN_AS_RP =
            new org.openide.util.RequestProcessor("nmox-open-as-project", 1);

    public ProjectStudioTopComponent() {
        setName(org.openide.util.NbBundle.getMessage(ProjectStudioTopComponent.class, "CTL_ProjectStudioTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(ProjectStudioTopComponent.class, "HINT_ProjectStudioTopComponent"));
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        add(treePanel, BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(statusLabel, BorderLayout.SOUTH);
        // Ledger 29 remainder (v1.48.0): tree selection drives the published
        // context. Fires on the EDT with the in-memory File only; the
        // publisher resolves the DataObject node off-EDT and equality-guards,
        // so a held arrow key is a stream of cheap compares, not disk walks.
        treePanel.setSelectionListener(this::selectionChanged);
        // v1.285.0: the tree's context menu has offered Cut/Copy/Delete
        // since v1.64.0, but those are CallbackSystemActions — without
        // these bindings in the ActionMap this TopComponent's default
        // lookup exposes, every one of them was permanently disabled.
        treePanel.installExplorerActions(getActionMap());
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
                item.setToolTipText(PlainText.plain(dir.getAbsolutePath()));
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
                        org.nmox.studio.core.util.PlainDialogs.plain("Could not open the configuration: " + ex.getMessage(), "Message"),
                        NotifyDescriptor.WARNING_MESSAGE));
            }
        });
        bar.add(configure);
        bar.addSeparator();

        JButton asProject = new JButton("Projects Tab");
        asProject.setToolTipText("Open this folder as a platform project: git colors, history,"
                + " diff and search light up in the Projects view");
        asProject.addActionListener(e -> {
            java.io.File projectDir = rack.getProjectDir();
            // findProject scans the folder for a manifest (disk IO) — off the
            // EDT, the same rule RackService's bridge follows; a cold cache or
            // network mount must not freeze the UI on this click
            OPEN_AS_RP.post(() -> {
                try {
                    // toFileObject requires a normalized file; a project dir
                    // picked via dialog or persisted state may carry '..' or a symlink
                    org.openide.filesystems.FileObject dir = org.openide.filesystems.FileUtil
                            .toFileObject(org.openide.filesystems.FileUtil
                                    .normalizeFile(projectDir));
                    org.netbeans.api.project.Project project = dir == null ? null
                            : org.netbeans.api.project.ProjectManager.getDefault().findProject(dir);
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
        });
        bar.add(asProject);

        JButton terminal = new JButton("Terminal");
        terminal.setToolTipText("Open a terminal — in the project directory when the platform supports it");
        terminal.addActionListener(e -> openTerminal());
        bar.add(terminal);
        return bar;
    }

    private void syncToRack() {
        File dir = rack.getProjectDir();
        treePanel.setRootDirectory(dir);
        statusLabel.setText(PlainText.plain(dir.getAbsolutePath()));
        // the kind walk stats the root and one level of children — off the
        // EDT (v1.33.1 law), newest aim wins; the suffix lands a beat later
        KIND_RP.post(() -> {
            org.nmox.studio.rack.devices.ProjectInspector.ProjectKind kind =
                    org.nmox.studio.rack.devices.ProjectInspector.detectKind(dir);
            SwingUtilities.invokeLater(() -> {
                if (dir.equals(rack.getProjectDir())) {
                    statusLabel.setText(PlainText.plain(dir.getAbsolutePath() + aimSuffix(kind)));
                }
            });
        });
    }

    private static final org.openide.util.RequestProcessor KIND_RP =
            new org.openide.util.RequestProcessor("nmox-projectstudio-kind", 1, true);

    /**
     * What the footer says after the aimed path. It said "(no
     * package.json)" for EVERY non-Node aim since v0.x — a Cargo project,
     * a Go module, a learning space — as if the polyglot IDE still
     * measured projects by one manifest (v2.85.0). Node stays bare (the
     * common case, no noise); the detected manifest names itself; the
     * two last resorts and the learning kind speak plainly.
     */
    static String aimSuffix(org.nmox.studio.rack.devices.ProjectInspector.ProjectKind kind) {
        switch (kind) {
            case NODE:
                return "";
            case NONE:
                return "  (no manifest yet)";
            case STATIC:
                return "  (static site)";
            case LEARN:
                return "  (learning space)";
            default:
                String manifest = kind.manifest();
                return "  (" + (manifest.isEmpty()
                        ? kind.name().toLowerCase(java.util.Locale.ROOT) : manifest) + ")";
        }
    }

    @Override
    public void componentOpened() {
        rack.addListener(rackListener);
        syncToRack();
    }

    @Override
    protected void componentActivated() {
        treePanel.activateExplorerActions(true);
    }

    @Override
    protected void componentDeactivated() {
        treePanel.activateExplorerActions(false);
    }

    @Override
    protected void componentShowing() {
        aimNodeShowing = true;
        // equality-guarded: re-shows of an unchanged selection cost one
        // compare. A selection that moved while hidden catches up here.
        selectionChanged(treePanel.selectedFile());
    }

    /**
     * EDT (tree selection events and componentShowing both are): publishes
     * the selected file, falling back to the aim directory when nothing is
     * selected — the selection refines the v1.45.0 aim node, it never
     * empties it out. Hidden tabs publish nothing (the v1.38.0 boot law);
     * componentShowing replays the current selection on first show.
     */
    void selectionChanged(File selected) {
        if (!aimNodeShowing) {
            return;
        }
        aimPublisher.publish(selected != null ? selected : rack.getProjectDir());
    }

    @Override
    protected void componentHidden() {
        aimNodeShowing = false;
    }

    @Override
    public void componentClosed() {
        rack.removeListener(rackListener);
        treePanel.dispose();
        aimNodeShowing = false;
        aimPublisher.reset(); // reopen re-resolves even for the same aim
        setActivatedNodes(new org.openide.nodes.Node[0]); // don't pin the DataObject
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }

    /**
     * Context-aware attempt: hand the terminal action the aimed project's
     * folder node so it opens THERE. Returns false when the action is
     * absent or declines, so the caller can fall back.
     */
    private boolean openTerminalInProject() {
        java.io.File dir = org.nmox.studio.rack.service.RackService.getDefault()
                .getRack().getProjectDir();
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        org.openide.filesystems.FileObject fo =
                org.openide.filesystems.FileUtil.toFileObject(
                        org.openide.filesystems.FileUtil.normalizeFile(dir));
        if (fo == null) {
            return false;
        }
        try {
            org.openide.nodes.Node node =
                    org.openide.loaders.DataObject.find(fo).getNodeDelegate();
            javax.swing.Action action = org.openide.awt.Actions.forID(
                    "Tools", "org.netbeans.modules.terminal.nodes.OpenInTerminalAction");
            if (action == null) {
                return false;
            }
            if (action instanceof org.openide.util.ContextAwareAction ctx) {
                action = ctx.createContextAwareInstance(
                        org.openide.util.lookup.Lookups.singleton(node));
            }
            if (!action.isEnabled()) {
                return false;
            }
            action.actionPerformed(new java.awt.event.ActionEvent(this, 0, "open"));
            return true;
        } catch (org.openide.loaders.DataObjectNotFoundException | RuntimeException notUsable) {
            return false;
        }
    }

    /**
     * Opens a terminal, in the project directory when the platform lets
     * us say where.
     *
     * <p>The button's tooltip used to promise "in the project directory"
     * and not deliver: {@code LocalTerminalAction} passes a null working
     * directory, so you landed in {@code $HOME} and had to cd yourself
     * (v1.212.0 finding). The terminal module also ships
     * {@code OpenInTerminalAction}, a context-aware action that reads a
     * directory out of the supplied Lookup — so try that first with the
     * aimed project's node, and fall back to the plain action (and the
     * old behaviour) when the module isn't present or refuses the
     * context. The tooltip now describes what actually happens either
     * way rather than the best case.
     */
    private void openTerminal() {
        if (openTerminalInProject()) {
            return;
        }
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
                if (child.getName().toLowerCase(java.util.Locale.ROOT).contains("terminal")) {
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
                .setStatusText("Terminal: Window ▸ IDE Tools ▸ Terminal");
    }
}
