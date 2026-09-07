package org.nmox.studio.rack.docker;

import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.PlainTables;
import java.awt.BorderLayout;
import java.io.IOException;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.docker.DockerClient.ContainerInfo;
import org.nmox.studio.rack.docker.DockerClient.DfRow;
import org.nmox.studio.rack.docker.DockerClient.ImageInfo;
import org.nmox.studio.rack.docker.DockerClient.NetworkInfo;
import org.nmox.studio.rack.docker.DockerClient.Result;
import org.nmox.studio.rack.docker.DockerClient.StatRow;
import org.nmox.studio.rack.docker.DockerClient.VolumeInfo;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.windows.TopComponent;

/**
 * The Docker Panel: the control room HARBOR's PANEL button opens.
 * More power than the CLI hands you raw: the disk ledger knows what
 * every category would reclaim and frees it in one click, containers
 * carry live CPU/MEM with their ports clickable into the browser,
 * bulk operations work across selections, and the Dockerize tab
 * writes a production multi-stage Dockerfile from what the rack
 * already knows about your project.
 */
@TopComponent.Description(
        preferredID = "DockerPanelTopComponent",
        // ALWAYS, like every other suite tab: with PERSISTENCE_NEVER the
        // window system forgot the user's close, so openAtStartup forced
        // this tab back open on every launch — the only tab that ignored
        // being closed. Only open/closed state and position persist; the
        // content is rebuilt from the live Docker daemon on show anyway.
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 400)
@org.openide.awt.ActionID(category = "Window",
        id = "org.nmox.studio.rack.docker.DockerPanelTopComponent")
@org.openide.awt.ActionReferences({
    @org.openide.awt.ActionReference(path = "Menu/Window", position = 255),
    @org.openide.awt.ActionReference(path = "Shortcuts", name = "D-8")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_DockerPanelAction",
        preferredID = "DockerPanelTopComponent"
)
@org.openide.util.NbBundle.Messages({
    "CTL_DockerPanelAction=Docker Panel",
    "DockerPanelTopComponent_engineChecking=ENGINE: checking…",
    "DockerPanelTopComponent_autoRefresh=Auto-refresh 15s",
    "DockerPanelTopComponent_colName=NAME",
    "DockerPanelTopComponent_colImage=IMAGE",
    "DockerPanelTopComponent_colStatus=STATUS",
    "DockerPanelTopComponent_colPorts=PORTS",
    "DockerPanelTopComponent_colCpu=CPU",
    "DockerPanelTopComponent_colMem=MEM",
    "DockerPanelTopComponent_colReference=REFERENCE",
    "DockerPanelTopComponent_colId=ID",
    "DockerPanelTopComponent_colSize=SIZE",
    "DockerPanelTopComponent_colCreated=CREATED",
    "DockerPanelTopComponent_colDriver=DRIVER",
    "DockerPanelTopComponent_colScope=SCOPE",
    "DockerPanelTopComponent_containersTable=Containers",
    "DockerPanelTopComponent_imagesTable=Images",
    "DockerPanelTopComponent_volumesTable=Volumes",
    "DockerPanelTopComponent_networksTable=Networks",
    "DockerPanelTopComponent_dockerfilePreview=Dockerfile preview",
    "DockerPanelTopComponent_ignorePreview=.dockerignore preview",
    "DockerPanelTopComponent_composePreview=compose file preview",
    "DockerPanelTopComponent_windowName=Docker Panel",
    "DockerPanelTopComponent_windowTooltip=Containers, images, volumes, networks, and one-click dockerize",
    "DockerPanelTopComponent_tabEngine=Engine",
    "DockerPanelTopComponent_tabContainers=Containers",
    "DockerPanelTopComponent_tabImages=Images",
    "DockerPanelTopComponent_tabVolumes=Volumes",
    "DockerPanelTopComponent_tabNetworks=Networks",
    "DockerPanelTopComponent_tabDockerize=Dockerize",
    "DockerPanelTopComponent_refreshAll=Refresh All",
    "DockerPanelTopComponent_verbRunning={0}…",
    "DockerPanelTopComponent_verbFailed=Could not {0}: {1}",
    "DockerPanelTopComponent_dockerExited=docker exited {0}",
    "DockerPanelTopComponent_engineDown=ENGINE: DOWN — start Docker Desktop / colima, then Refresh",
    "DockerPanelTopComponent_engineUp=ENGINE: UP · v{0}",
    "DockerPanelTopComponent_refreshedAt=refreshed {0}",
    "DockerPanelTopComponent_colCategory=CATEGORY",
    "DockerPanelTopComponent_colCount=COUNT",
    "DockerPanelTopComponent_colActive=ACTIVE",
    "DockerPanelTopComponent_colReclaimable=RECLAIMABLE",
    "DockerPanelTopComponent_reclaim=RECLAIM",
    "DockerPanelTopComponent_confirmPruneVolumes=Remove ALL unused volumes? Their data is gone for good.",
    "DockerPanelTopComponent_verbPrune=prune {0}",
    "DockerPanelTopComponent_deepClean=DEEP CLEAN — remove ALL unused images (not just dangling)",
    "DockerPanelTopComponent_confirmDeepClean=Remove every image not used by a container? Re-pulls may be slow.",
    "DockerPanelTopComponent_verbDeepImagePrune=deep image prune",
    "DockerPanelTopComponent_confirmTitle=Docker Panel",
    "DockerPanelTopComponent_start=Start",
    "DockerPanelTopComponent_stop=Stop",
    "DockerPanelTopComponent_restart=Restart",
    "DockerPanelTopComponent_remove=Remove",
    "DockerPanelTopComponent_logs=Logs",
    "DockerPanelTopComponent_inspect=Inspect",
    "DockerPanelTopComponent_openInBrowser=Open in Browser",
    "DockerPanelTopComponent_verbStart=start {0}",
    "DockerPanelTopComponent_verbStop=stop {0}",
    "DockerPanelTopComponent_verbRestart=restart {0}",
    "DockerPanelTopComponent_verbRemove=remove {0}",
    "DockerPanelTopComponent_confirmRemoveContainers=Force-remove selected container(s)?",
    "DockerPanelTopComponent_inspectTitle=inspect {0}",
    "DockerPanelTopComponent_logsTitle=logs {0}  (last 500 lines)",
    "DockerPanelTopComponent_browserRefused=could not open a browser for {0} (port {1})",
    "DockerPanelTopComponent_noHostPorts={0} publishes no host ports",
    "DockerPanelTopComponent_noRecognizablePort=no recognizable host port in \"{0}\" for {1}",
    "DockerPanelTopComponent_pullFieldName=Image reference to pull",
    "DockerPanelTopComponent_pullFieldTooltip=image reference, e.g. nginx:alpine",
    "DockerPanelTopComponent_pull=Pull",
    "DockerPanelTopComponent_verbPull=pull {0}",
    "DockerPanelTopComponent_runEllipsis=Run…",
    "DockerPanelTopComponent_tagEllipsis=Tag…",
    "DockerPanelTopComponent_newTagPrompt=New tag for {0}:",
    "DockerPanelTopComponent_tagImageTitle=Tag Image",
    "DockerPanelTopComponent_verbTag=tag",
    "DockerPanelTopComponent_layers=Layers",
    "DockerPanelTopComponent_layersTitle=layers {0}",
    "DockerPanelTopComponent_confirmRemoveImages=Remove selected image(s)?",
    "DockerPanelTopComponent_verbRmi=rmi {0}",
    "DockerPanelTopComponent_removeAllDangling=Remove all dangling",
    "DockerPanelTopComponent_verbPruneDangling=prune dangling images",
    "DockerPanelTopComponent_containerNameField=Container name (blank = auto)",
    "DockerPanelTopComponent_portsField=Ports host:container (space-separated)",
    "DockerPanelTopComponent_envField=Env KEY=VAL (space-separated)",
    "DockerPanelTopComponent_containerNameLabel=Name (blank = auto):",
    "DockerPanelTopComponent_portsLabel=Ports host:container (space-separated):",
    "DockerPanelTopComponent_envLabel=Env KEY=VAL (space-separated):",
    "DockerPanelTopComponent_runTitle=Run {0}",
    "DockerPanelTopComponent_runFailed=Could not run {0}: {1}",
    "DockerPanelTopComponent_danglingRef=<dangling>",
    "DockerPanelTopComponent_danglingFlag=DANGLING",
    "DockerPanelTopComponent_confirmRemoveVolumes=Remove selected volume(s)? Their data is gone for good.",
    "DockerPanelTopComponent_verbRemoveVolume=remove volume",
    "DockerPanelTopComponent_pruneUnused=Prune unused",
    "DockerPanelTopComponent_verbPruneVolumes=prune volumes",
    "DockerPanelTopComponent_verbRemoveNetwork=remove network",
    "DockerPanelTopComponent_verbPruneNetworks=prune networks",
    "DockerPanelTopComponent_recipeTooltip=Detected toolchain, or a recipe from ~/.nmox/dockerize.d",
    "DockerPanelTopComponent_regenerate=Regenerate",
    "DockerPanelTopComponent_writeFiles=Write files into project",
    "DockerPanelTopComponent_buildImage=Build image",
    "DockerPanelTopComponent_runContainer=Run container",
    "DockerPanelTopComponent_sourceRecipe=recipe: {0} · yours",
    "DockerPanelTopComponent_sourceDetected=detected toolchain: {0}",
    "DockerPanelTopComponent_sourceStaticSuffix= (static bundle → nginx)",
    "DockerPanelTopComponent_dockerizeInfo=Project: {0}   ·   {1}   ·   image: {2}   ·   port: {3}",
    "DockerPanelTopComponent_recipeSkipped=Recipe {0} skipped: {1}",
    "DockerPanelTopComponent_detectedItem=Detected ({0})",
    "DockerPanelTopComponent_confirmOverwrite=Overwrite existing {0}?",
    "DockerPanelTopComponent_wroteFiles=wrote {0} into {1}",
    "DockerPanelTopComponent_writeFailed=Could not write the Docker files: {0}",
    "DockerPanelTopComponent_dockerOk=docker {0} OK",
    "DockerPanelTopComponent_dockerFailed=docker {0} failed [{1}]"
})
public final class DockerPanelTopComponent extends TopComponent {

    private static final Color BG = new Color(25, 26, 29);
    private static final Color TEXT = new Color(206, 208, 212);
    private static final Color DIM = new Color(140, 142, 148);
    private static final Color UP = new Color(80, 235, 100);
    private static final Color DOWN = new Color(255, 90, 80);
    private static final Color ACCENT = new Color(36, 150, 237);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Fallback only — used when the window system cannot supply the panel. */
    private static DockerPanelTopComponent fallbackInstance;

    private final DockerClient client = DockerClient.getDefault();
    private final JLabel engineLabel = new JLabel(Bundle.DockerPanelTopComponent_engineChecking());
    private final JLabel statusLabel = new JLabel(" ");
    private final javax.swing.Timer autoTimer = new javax.swing.Timer(15_000, e -> refreshAll());
    private final JCheckBox autoBox = new JCheckBox(Bundle.DockerPanelTopComponent_autoRefresh(), false);

    private final JPanel enginePanel = new JPanel(new GridBagLayout());
    private final DefaultTableModel containersModel = model("", Bundle.DockerPanelTopComponent_colName(), Bundle.DockerPanelTopComponent_colImage(), Bundle.DockerPanelTopComponent_colStatus(), Bundle.DockerPanelTopComponent_colPorts(), Bundle.DockerPanelTopComponent_colCpu(), Bundle.DockerPanelTopComponent_colMem());
    private final JTable containersTable = table(containersModel, Bundle.DockerPanelTopComponent_containersTable());
    private final DefaultTableModel imagesModel = model(Bundle.DockerPanelTopComponent_colReference(), Bundle.DockerPanelTopComponent_colId(), Bundle.DockerPanelTopComponent_colSize(), Bundle.DockerPanelTopComponent_colCreated(), "");
    private final JTable imagesTable = table(imagesModel, Bundle.DockerPanelTopComponent_imagesTable());
    private final DefaultTableModel volumesModel = model(Bundle.DockerPanelTopComponent_colName(), Bundle.DockerPanelTopComponent_colDriver());
    private final JTable volumesTable = table(volumesModel, Bundle.DockerPanelTopComponent_volumesTable());
    private final DefaultTableModel networksModel = model(Bundle.DockerPanelTopComponent_colName(), Bundle.DockerPanelTopComponent_colDriver(), Bundle.DockerPanelTopComponent_colScope(), Bundle.DockerPanelTopComponent_colId());
    private final JTable networksTable = table(networksModel, Bundle.DockerPanelTopComponent_networksTable());

    private List<ContainerInfo> containers = List.of();
    private List<ImageInfo> images = List.of();
    private List<VolumeInfo> volumes = List.of();
    private List<NetworkInfo> networks = List.of();

    private final JTextArea dockerfilePreview = preview(Bundle.DockerPanelTopComponent_dockerfilePreview());
    private final JTextArea ignorePreview = preview(Bundle.DockerPanelTopComponent_ignorePreview());
    private final JTextArea composePreview = preview(Bundle.DockerPanelTopComponent_composePreview());
    private final JLabel dockerizeInfo = new JLabel(" ");
    private Map<String, String> dockerizeFiles = Map.of();
    /**
     * Detected-toolchain generator plus any ~/.nmox/dockerize.d recipes
     * (v1.301.0). Item 0 is always the built-in; DockerRecipes.Recipe
     * entries follow. Selecting re-previews; Write writes what previews.
     */
    private final javax.swing.JComboBox<Object> recipeCombo = new javax.swing.JComboBox<>();

    public DockerPanelTopComponent() {
        setName(Bundle.DockerPanelTopComponent_windowName());
        setToolTipText(Bundle.DockerPanelTopComponent_windowTooltip());
        setLayout(new BorderLayout());
        setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.addTab(Bundle.DockerPanelTopComponent_tabEngine(), wrap(enginePanel));
        tabs.addTab(Bundle.DockerPanelTopComponent_tabContainers(), buildContainersTab());
        tabs.addTab(Bundle.DockerPanelTopComponent_tabImages(), buildImagesTab());
        tabs.addTab(Bundle.DockerPanelTopComponent_tabVolumes(), buildVolumesTab());
        tabs.addTab(Bundle.DockerPanelTopComponent_tabNetworks(), buildNetworksTab());
        tabs.addTab(Bundle.DockerPanelTopComponent_tabDockerize(), buildDockerizeTab());
        add(tabs, BorderLayout.CENTER);

        statusLabel.setForeground(DIM);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * HARBOR's PANEL button lands here. Routed through the window system so
     * this and the Window-menu registration yield the SAME instance — two
     * paths must never materialize two panels (and two refresh timers).
     */
    public static void openPanel() {
        SwingUtilities.invokeLater(() -> {
            DockerPanelTopComponent panel = null;
            try {
                if (org.openide.windows.WindowManager.getDefault()
                        .findTopComponent("DockerPanelTopComponent")
                        instanceof DockerPanelTopComponent registered) {
                    panel = registered;
                }
            } catch (RuntimeException ex) {
                // window system unavailable (tests, stripped platform)
            }
            if (panel == null) {
                if (fallbackInstance == null) {
                    fallbackInstance = new DockerPanelTopComponent();
                }
                panel = fallbackInstance;
            }
            panel.open();
            panel.requestActive();
            panel.refreshAll();
        });
    }

    @Override
    public void componentOpened() {
        // the checkbox survives a close; a re-open must make it honest again —
        // "Auto-refresh 15s" checked with a stopped timer is a silent lie
        if (autoBox.isSelected()) {
            autoTimer.start();
        }
    }

    @Override
    public void componentClosed() {
        autoTimer.stop();
        aimFollower.closed();
    }

    // ---- header ----

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        header.setBackground(BG);
        engineLabel.setForeground(TEXT);
        engineLabel.setFont(engineLabel.getFont().deriveFont(Font.BOLD));
        header.add(engineLabel);
        JButton refresh = new JButton(Bundle.DockerPanelTopComponent_refreshAll());
        refresh.addActionListener(e -> refreshAll());
        header.add(refresh);
        autoBox.setBackground(BG);
        autoBox.setForeground(DIM);
        autoBox.addActionListener(e -> {
            if (autoBox.isSelected()) {
                autoTimer.start();
            } else {
                autoTimer.stop();
            }
        });
        header.add(autoBox);
        return header;
    }

    // ---- shared widgets ----

    private static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    private static JTable table(DefaultTableModel m, String accessibleName) {
        JTable t = new JTable(m);
        t.getAccessibleContext().setAccessibleName(accessibleName);
        t.setBackground(BG);
        t.setForeground(TEXT);
        t.setGridColor(new Color(45, 46, 50));
        t.setRowHeight(24);
        t.setFont(MONO);
        t.getTableHeader().setBackground(new Color(35, 36, 40));
        t.getTableHeader().setForeground(DIM);
        t.setSelectionBackground(new Color(40, 70, 110));
        t.setSelectionForeground(Color.WHITE);
        // container names/images/status come from `docker` — keep markup literal
        t.setDefaultRenderer(Object.class, org.nmox.studio.core.util.PlainTables.plain(
                new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                String s = String.valueOf(v);
                if (!sel) {
                    c.setForeground("●".equals(s) ? UP : "○".equals(s) ? DIM
                            : "◐".equals(s) ? new Color(255, 190, 60)
                            : Bundle.DockerPanelTopComponent_danglingFlag().equals(s) ? new Color(255, 190, 60) : TEXT);
                }
                return c;
            }
        }));
        return t;
    }

    private static JTextArea preview(String accessibleName) {
        JTextArea a = new JTextArea();
        a.getAccessibleContext().setAccessibleName(accessibleName);
        a.setEditable(false);
        a.setFont(MONO);
        a.setBackground(new Color(18, 19, 21));
        a.setForeground(new Color(96, 235, 120));
        return a;
    }

    private static JScrollPane wrap(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG);
        return sp;
    }

    private JButton btn(String label, Runnable action) {
        JButton b = new JButton(PlainText.plain(label));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void status(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(PlainText.plain(s)));
    }

    /** Runs a verb, surfaces failure, refreshes the panel after. */
    private void verbThenRefresh(java.util.concurrent.CompletableFuture<Result> f, String what) {
        status(Bundle.DockerPanelTopComponent_verbRunning(what));
        f.whenComplete((r, ex) -> SwingUtilities.invokeLater(() -> {
            if (r != null && !r.ok()) {
                error(Bundle.DockerPanelTopComponent_verbFailed(what,
                        r.stderr().isBlank() ? Bundle.DockerPanelTopComponent_dockerExited(String.valueOf(r.exit())) : r.stderr().strip()));
            }
            refreshAll();
        }));
    }

    // ---- refresh ----

    private void refreshAll() {
        client.engineVersion().thenAccept(v -> SwingUtilities.invokeLater(() -> {
            if (v == null) {
                engineLabel.setText(Bundle.DockerPanelTopComponent_engineDown());
                engineLabel.setForeground(DOWN);
            } else {
                engineLabel.setText(Bundle.DockerPanelTopComponent_engineUp(v));
                engineLabel.setForeground(UP);
            }
        }));
        refreshEngineTab();
        refreshContainers();
        refreshImages();
        refreshVolumesNetworks();
        status(Bundle.DockerPanelTopComponent_refreshedAt(java.time.LocalTime.now().withNano(0)));
    }

    private void refreshEngineTab() {
        client.systemDf().thenAccept(rows -> SwingUtilities.invokeLater(() -> {
            enginePanel.removeAll();
            enginePanel.setBackground(BG);
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new java.awt.Insets(6, 12, 6, 12);
            g.anchor = GridBagConstraints.WEST;
            g.gridy = 0;
            for (String h : new String[]{Bundle.DockerPanelTopComponent_colCategory(), Bundle.DockerPanelTopComponent_colCount(), Bundle.DockerPanelTopComponent_colActive(), Bundle.DockerPanelTopComponent_colSize(), Bundle.DockerPanelTopComponent_colReclaimable(), ""}) {
                g.gridx = enginePanel.getComponentCount() % 6;
                JLabel l = new JLabel(PlainText.plain(h));
                l.setForeground(DIM);
                enginePanel.add(l, g);
            }
            for (DfRow row : rows) {
                g.gridy++;
                g.gridx = 0;
                enginePanel.add(label(row.type(), TEXT, Font.BOLD), g);
                g.gridx = 1;
                enginePanel.add(label(row.totalCount(), TEXT, Font.PLAIN), g);
                g.gridx = 2;
                enginePanel.add(label(row.active(), TEXT, Font.PLAIN), g);
                g.gridx = 3;
                enginePanel.add(label(row.size(), TEXT, Font.PLAIN), g);
                g.gridx = 4;
                enginePanel.add(label(row.reclaimable(), ACCENT, Font.BOLD), g);
                g.gridx = 5;
                String kind = pruneKind(row.type());
                if (kind != null) {
                    boolean volumes = "volume".equals(kind);
                    enginePanel.add(btn(Bundle.DockerPanelTopComponent_reclaim(), () -> {
                        if (!volumes || confirm(Bundle.DockerPanelTopComponent_confirmPruneVolumes())) {
                            verbThenRefresh(client.prune(kind, false), Bundle.DockerPanelTopComponent_verbPrune(kind));
                        }
                    }), g);
                }
            }
            g.gridy++;
            g.gridx = 0;
            g.gridwidth = 6;
            enginePanel.add(btn(Bundle.DockerPanelTopComponent_deepClean(), () -> {
                if (confirm(Bundle.DockerPanelTopComponent_confirmDeepClean())) {
                    verbThenRefresh(client.prune("image", true), Bundle.DockerPanelTopComponent_verbDeepImagePrune());
                }
            }), g);
            enginePanel.revalidate();
            enginePanel.repaint();
        }));
    }

    private static String pruneKind(String dfType) {
        String t = dfType.toLowerCase(java.util.Locale.ROOT);
        if (t.startsWith("image")) {
            return "image";
        }
        if (t.startsWith("container")) {
            return "container";
        }
        if (t.contains("volume")) {
            return "volume";
        }
        if (t.contains("build")) {
            return "builder";
        }
        return null;
    }

    private static JLabel label(String s, Color c, int style) {
        JLabel l = new JLabel(PlainText.plain(s == null ? "" : s));
        l.setForeground(c);
        l.setFont(l.getFont().deriveFont(style));
        return l;
    }

    private boolean confirm(String message) {
        NotifyDescriptor d = new NotifyDescriptor.Confirmation(org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), Bundle.DockerPanelTopComponent_confirmTitle(),
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
        return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION;
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), NotifyDescriptor.ERROR_MESSAGE));
    }

    // ---- containers ----

    private Component buildContainersTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(wrap(containersTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn(Bundle.DockerPanelTopComponent_start(), () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("start", c.id()), Bundle.DockerPanelTopComponent_verbStart(c.name())))));
        actions.add(btn(Bundle.DockerPanelTopComponent_stop(), () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("stop", c.id()), Bundle.DockerPanelTopComponent_verbStop(c.name())))));
        actions.add(btn(Bundle.DockerPanelTopComponent_restart(), () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("restart", c.id()), Bundle.DockerPanelTopComponent_verbRestart(c.name())))));
        actions.add(btn(Bundle.DockerPanelTopComponent_remove(), () -> {
            if (confirm(Bundle.DockerPanelTopComponent_confirmRemoveContainers())) {
                eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("rm", c.id()), Bundle.DockerPanelTopComponent_verbRemove(c.name())));
            }
        }));
        actions.add(btn(Bundle.DockerPanelTopComponent_logs(), () -> eachSelectedContainer(this::showLogs)));
        actions.add(btn(Bundle.DockerPanelTopComponent_inspect(), () -> eachSelectedContainer(c ->
                client.inspect(c.id()).thenAccept(json -> textDialog(Bundle.DockerPanelTopComponent_inspectTitle(c.name()), json)))));
        actions.add(btn(Bundle.DockerPanelTopComponent_openInBrowser(), () -> eachSelectedContainer(this::openPorts)));
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void eachSelectedContainer(Consumer<ContainerInfo> action) {
        for (int row : containersTable.getSelectedRows()) {
            if (row >= 0 && row < containers.size()) {
                action.accept(containers.get(row));
            }
        }
    }

    private void showLogs(ContainerInfo c) {
        client.logs(c.id(), 500).thenAccept(text ->
                textDialog(Bundle.DockerPanelTopComponent_logsTitle(c.name()), text));
    }

    private void openPorts(ContainerInfo c) {
        // a published port opens in the product's own Browser, the system
        // browser as the fallback (v2.70.0, the ⇄ chip's opener)
        for (Integer port : c.hostPorts()) {
            if (org.nmox.studio.rack.service.ServingLinks.open("http://localhost:" + port)) {
                return;
            }
        }
        // three different truths, told apart: no ports at all, ports we could
        // not recognize a host mapping in, and ports the browser refused
        if (!c.hostPorts().isEmpty()) {
            status(Bundle.DockerPanelTopComponent_browserRefused(c.name(), String.valueOf(c.hostPorts().get(0))));
        } else if (c.ports() == null || c.ports().isBlank()) {
            status(Bundle.DockerPanelTopComponent_noHostPorts(c.name()));
        } else {
            status(Bundle.DockerPanelTopComponent_noRecognizablePort(c.ports(), c.name()));
        }
    }

    private void textDialog(String title, String text) {
        SwingUtilities.invokeLater(() -> {
            JTextArea area = preview(title);
            area.setText(text);
            area.setCaretPosition(0);
            JScrollPane sp = new JScrollPane(area);
            sp.setPreferredSize(new java.awt.Dimension(820, 520));
            DialogDisplayer.getDefault().notify(new DialogDescriptor(sp, title));
        });
    }

    private void refreshContainers() {
        client.containers().thenCombine(client.statsSnapshot(), (cs, stats) -> {
            Map<String, StatRow> byId = new HashMap<>();
            for (StatRow s : stats) {
                byId.put(s.id(), s);
            }
            return Map.entry(cs, byId);
        }).thenAccept(e -> SwingUtilities.invokeLater(() -> {
            containers = e.getKey();
            containersModel.setRowCount(0);
            for (ContainerInfo c : containers) {
                StatRow s = e.getValue().get(c.id().substring(0, Math.min(12, c.id().length())));
                containersModel.addRow(new Object[]{
                    c.running() ? "●" : c.state().contains("paus") ? "◐" : "○",
                    c.name(), c.image(), c.status(), c.ports(),
                    s == null ? "" : s.cpu(), s == null ? "" : s.mem()});
            }
        }));
    }

    // ---- images ----

    private Component buildImagesTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(wrap(imagesTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        JTextField pullField = new JTextField(22);
        pullField.getAccessibleContext().setAccessibleName(Bundle.DockerPanelTopComponent_pullFieldName());
        pullField.setToolTipText(Bundle.DockerPanelTopComponent_pullFieldTooltip());
        actions.add(pullField);
        actions.add(btn(Bundle.DockerPanelTopComponent_pull(), () -> {
            String ref = pullField.getText().trim();
            if (!ref.isEmpty()) {
                verbThenRefresh(client.pull(ref), Bundle.DockerPanelTopComponent_verbPull(ref));
            }
        }));
        actions.add(btn(Bundle.DockerPanelTopComponent_runEllipsis(), () -> eachSelectedImage(this::quickRun)));
        actions.add(btn(Bundle.DockerPanelTopComponent_tagEllipsis(), () -> eachSelectedImage(img -> {
            NotifyDescriptor.InputLine line =
                    new NotifyDescriptor.InputLine(Bundle.DockerPanelTopComponent_newTagPrompt(img.ref()), Bundle.DockerPanelTopComponent_tagImageTitle());
            line.setInputText(img.ref());
            if (DialogDisplayer.getDefault().notify(line) == NotifyDescriptor.OK_OPTION
                    && !line.getInputText().isBlank()) {
                verbThenRefresh(client.tag(img.ref(), line.getInputText().trim()), Bundle.DockerPanelTopComponent_verbTag());
            }
        })));
        actions.add(btn(Bundle.DockerPanelTopComponent_layers(), () -> eachSelectedImage(img ->
                client.history(img.ref()).thenAccept(h -> textDialog(Bundle.DockerPanelTopComponent_layersTitle(img.ref()), h)))));
        actions.add(btn(Bundle.DockerPanelTopComponent_remove(), () -> {
            if (confirm(Bundle.DockerPanelTopComponent_confirmRemoveImages())) {
                eachSelectedImage(img -> verbThenRefresh(client.removeImage(img.ref(), true), Bundle.DockerPanelTopComponent_verbRmi(img.ref())));
            }
        }));
        actions.add(btn(Bundle.DockerPanelTopComponent_removeAllDangling(), () ->
                verbThenRefresh(client.prune("image", false), Bundle.DockerPanelTopComponent_verbPruneDangling())));
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void eachSelectedImage(Consumer<ImageInfo> action) {
        for (int row : imagesTable.getSelectedRows()) {
            if (row >= 0 && row < images.size()) {
                action.accept(images.get(row));
            }
        }
    }

    /** A run dialog with the three things you always need: name, ports, env. */
    private void quickRun(ImageInfo img) {
        JTextField name = new JTextField(16);
        name.getAccessibleContext().setAccessibleName(Bundle.DockerPanelTopComponent_containerNameField());
        JTextField ports = new JTextField("8080:80", 16);
        ports.getAccessibleContext().setAccessibleName(Bundle.DockerPanelTopComponent_portsField());
        JTextField env = new JTextField(16);
        env.getAccessibleContext().setAccessibleName(Bundle.DockerPanelTopComponent_envField());
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new java.awt.Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = 0;
        g.gridx = 0;
        form.add(new JLabel(Bundle.DockerPanelTopComponent_containerNameLabel()), g);
        g.gridx = 1;
        form.add(name, g);
        g.gridy = 1;
        g.gridx = 0;
        form.add(new JLabel(Bundle.DockerPanelTopComponent_portsLabel()), g);
        g.gridx = 1;
        form.add(ports, g);
        g.gridy = 2;
        g.gridx = 0;
        form.add(new JLabel(Bundle.DockerPanelTopComponent_envLabel()), g);
        g.gridx = 1;
        form.add(env, g);
        DialogDescriptor dd = new DialogDescriptor(form, Bundle.DockerPanelTopComponent_runTitle(img.ref()));
        if (DialogDisplayer.getDefault().notify(dd) != DialogDescriptor.OK_OPTION) {
            return;
        }
        List<String> args = new ArrayList<>(List.of("run", "-d"));
        if (!name.getText().isBlank()) {
            args.addAll(List.of("--name", name.getText().trim()));
        }
        for (String pm : ports.getText().trim().split("\\s+")) {
            if (pm.contains(":")) {
                args.addAll(List.of("-p", pm));
            }
        }
        for (String ev : env.getText().trim().split("\\s+")) {
            if (ev.contains("=")) {
                args.addAll(List.of("-e", ev));
            }
        }
        args.add(img.ref());
        status("docker " + String.join(" ", args));
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> client.run(120, args.toArray(String[]::new)))
                .thenAccept(r -> {
                    if (!r.ok()) {
                        SwingUtilities.invokeLater(() -> error(
                                Bundle.DockerPanelTopComponent_runFailed(img.ref(), r.stderr().strip())));
                    }
                    refreshAll();
                });
    }

    private void refreshImages() {
        client.images().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            images = list;
            imagesModel.setRowCount(0);
            for (ImageInfo i : images) {
                imagesModel.addRow(new Object[]{
                    i.dangling() ? Bundle.DockerPanelTopComponent_danglingRef() : i.ref(),
                    i.id().length() > 12 ? i.id().substring(0, 12) : i.id(),
                    i.size(), i.created(), i.dangling() ? Bundle.DockerPanelTopComponent_danglingFlag() : ""});
            }
        }));
    }

    // ---- volumes & networks ----

    private Component buildVolumesTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(wrap(volumesTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn(Bundle.DockerPanelTopComponent_remove(), () -> {
            if (confirm(Bundle.DockerPanelTopComponent_confirmRemoveVolumes())) {
                for (int row : volumesTable.getSelectedRows()) {
                    if (row < volumes.size()) {
                        verbThenRefresh(client.removeVolume(volumes.get(row).name()), Bundle.DockerPanelTopComponent_verbRemoveVolume());
                    }
                }
            }
        }));
        actions.add(btn(Bundle.DockerPanelTopComponent_pruneUnused(), () -> {
            if (confirm(Bundle.DockerPanelTopComponent_confirmPruneVolumes())) {
                verbThenRefresh(client.prune("volume", false), Bundle.DockerPanelTopComponent_verbPruneVolumes());
            }
        }));
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private Component buildNetworksTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(wrap(networksTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn(Bundle.DockerPanelTopComponent_remove(), () -> {
            for (int row : networksTable.getSelectedRows()) {
                if (row < networks.size()) {
                    verbThenRefresh(client.removeNetwork(networks.get(row).id()), Bundle.DockerPanelTopComponent_verbRemoveNetwork());
                }
            }
        }));
        actions.add(btn(Bundle.DockerPanelTopComponent_pruneUnused(), () ->
                verbThenRefresh(client.prune("network", false), Bundle.DockerPanelTopComponent_verbPruneNetworks())));
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void refreshVolumesNetworks() {
        client.volumes().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            volumes = list;
            volumesModel.setRowCount(0);
            for (VolumeInfo v : volumes) {
                volumesModel.addRow(new Object[]{v.name(), v.driver()});
            }
        }));
        client.networks().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            networks = list;
            networksModel.setRowCount(0);
            for (NetworkInfo n : networks) {
                networksModel.addRow(new Object[]{n.name(), n.driver(), n.scope(), n.id()});
            }
        }));
    }

    // ---- dockerize ----

    private Component buildDockerizeTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        dockerizeInfo.setForeground(TEXT);
        dockerizeInfo.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(BG);
        north.add(dockerizeInfo, BorderLayout.CENTER);
        recipeCombo.setToolTipText(Bundle.DockerPanelTopComponent_recipeTooltip());
        // a drop-in recipe's name is external; a list renderer would paint an
        // <html>-led one as markup, so the combo html-disables its renderer
        recipeCombo.setRenderer(PlainTables.plain(new javax.swing.DefaultListCellRenderer()));
        recipeCombo.addActionListener(e -> regenerateDockerize());
        JPanel comboHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        comboHolder.setBackground(BG);
        comboHolder.add(recipeCombo);
        north.add(comboHolder, BorderLayout.EAST);
        p.add(north, BorderLayout.NORTH);

        JTabbedPane previews = new JTabbedPane();
        previews.addTab("Dockerfile", wrap(dockerfilePreview));
        previews.addTab(".dockerignore", wrap(ignorePreview));
        previews.addTab("compose.yaml", wrap(composePreview));
        p.add(previews, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn(Bundle.DockerPanelTopComponent_regenerate(), this::regenerateDockerize));
        actions.add(btn(Bundle.DockerPanelTopComponent_writeFiles(), this::writeDockerizeFiles));
        actions.add(btn(Bundle.DockerPanelTopComponent_buildImage(), () -> dockerizeCommand("build", "-t", imageName(), ".")));
        actions.add(btn(Bundle.DockerPanelTopComponent_runContainer(), () -> {
            int port = currentPort();
            dockerizeCommand("run", "-d", "--name", imageName() + "-dev",
                    "-p", port + ":" + port, imageName());
        }));
        p.add(actions, BorderLayout.SOUTH);
        // No regenerate here: this panel opens at startup, so construction
        // happens behind the selected tab and the detect walk would run for
        // previews nobody can see. First show generates them (the DB Studio
        // Docker-offer idiom); Regenerate stays for re-runs.
        dockerizePending = true;
        return p;
    }

    /** A dockerize preview is owed but the panel is hidden; served on show. */
    private boolean dockerizePending;

    /** v1.235.0: the aim is this window's ambient selection (ledger 29). */
    private final org.nmox.studio.rack.service.AimFollower aimFollower =
            new org.nmox.studio.rack.service.AimFollower(n ->
                    setActivatedNodes(new org.openide.nodes.Node[]{n}));

    @Override
    protected void componentShowing() {
        aimFollower.showing();
        if (dockerizePending) {
            dockerizePending = false;
            regenerateDockerize();
        }
        // the first SHOW serves the deferred work (the v1.38.0 law, its
        // missing half): a panel reached by its tab or the Welcome's door
        // read "ENGINE: checking…" over an empty pane until Refresh All —
        // refreshAll ran only from the open-action, the verbs and the timer
        // (the v2.85.0 Docker walk). Once: the timer and the button own the rest
        if (!refreshedOnShow) {
            refreshedOnShow = true;
            refreshAll();
        }
    }

    /** First-show refresh done — never at construction (the zero-boot-spawns law: a hidden default-open tab is not showing). */
    private boolean refreshedOnShow;

    @Override
    protected void componentHidden() {
        aimFollower.hidden();
    }

    private File projectDir() {
        return RackService.getDefault().getRack().getProjectDir();
    }

    private String imageName() {
        return projectDir().getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    private ProjectInspector.ProjectKind currentKind() {
        return ProjectInspector.detectKind(projectDir());
    }

    private int currentPort() {
        return DockerizeGenerator.defaultPort(currentKind(),
                DockerizeGenerator.buildsStatic(projectDir()));
    }

    /** Detection walks the project directory; keep it off the EDT. */
    private static final org.openide.util.RequestProcessor DOCKERIZE_RP =
            new org.openide.util.RequestProcessor("nmox-dockerize", 1, true);

    private void regenerateDockerize() {
        // detectKind + buildsStatic walk the project directory; on a $HOME aim
        // that would touch the TCC-protected folders on the EDT during startup
        // (this panel opens at startup). Detect on the background thread and
        // apply the previews on the EDT. The drop-in recipe scan rides the
        // same lane (the v1.33.1 law).
        File dir = projectDir();
        Object selected = recipeCombo.getSelectedItem();
        DOCKERIZE_RP.post(() -> {
            ProjectInspector.ProjectKind kind = ProjectInspector.detectKind(dir);
            boolean statics = DockerizeGenerator.buildsStatic(dir);
            String image = dir.getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
            int port = DockerizeGenerator.defaultPort(kind, statics);
            DockerRecipes.Loaded loaded = DockerRecipes.load();
            Map<String, String> files;
            String source;
            java.util.Optional<DockerRecipes.Recipe> fresh =
                    selected instanceof DockerRecipes.Recipe recipe
                            ? DockerRecipes.findByName(loaded.recipes(), recipe.name())
                            : java.util.Optional.empty();
            if (fresh.isPresent()) {
                // ALWAYS the fresh on-disk parse: Regenerate must show edits,
                // and a deleted recipe falls through to the detected branch
                files = DockerRecipes.materialize(fresh.get(), image);
                source = Bundle.DockerPanelTopComponent_sourceRecipe(fresh.get().name());
            } else {
                files = DockerizeGenerator.generate(kind, image, statics);
                source = Bundle.DockerPanelTopComponent_sourceDetected(kind)
                        + (statics ? Bundle.DockerPanelTopComponent_sourceStaticSuffix() : "");
            }
            SwingUtilities.invokeLater(() -> {
                dockerizeFiles = files;
                refillRecipeCombo(kind, loaded, selected);
                dockerizeInfo.setText(Bundle.DockerPanelTopComponent_dockerizeInfo(dir.getName(), source, image, String.valueOf(port)));
                dockerfilePreview.setText(files.getOrDefault("Dockerfile", ""));
                ignorePreview.setText(files.getOrDefault(".dockerignore", ""));
                composePreview.setText(files.getOrDefault("compose.yaml", ""));
                for (DockerRecipes.Skipped skip : loaded.skipped()) {
                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                            Bundle.DockerPanelTopComponent_recipeSkipped(skip.file(), skip.reason()));
                }
            });
        });
    }

    /**
     * Rebuilds the combo (built-in first, recipes after) without firing
     * the selection listener back into {@link #regenerateDockerize} — a
     * refill is a repaint, not a choice.
     */
    private void refillRecipeCombo(ProjectInspector.ProjectKind kind,
            DockerRecipes.Loaded loaded, Object keepSelected) {
        java.awt.event.ActionListener[] listeners = recipeCombo.getActionListeners();
        for (java.awt.event.ActionListener l : listeners) {
            recipeCombo.removeActionListener(l);
        }
        recipeCombo.removeAllItems();
        recipeCombo.addItem(Bundle.DockerPanelTopComponent_detectedItem(kind));
        for (DockerRecipes.Recipe r : loaded.recipes()) {
            recipeCombo.addItem(r);
        }
        if (keepSelected instanceof DockerRecipes.Recipe recipe) {
            for (DockerRecipes.Recipe r : loaded.recipes()) {
                if (r.name().equals(recipe.name())) {
                    recipeCombo.setSelectedItem(r);
                    break;
                }
            }
        }
        for (java.awt.event.ActionListener l : listeners) {
            recipeCombo.addActionListener(l);
        }
    }

    private void writeDockerizeFiles() {
        File dir = projectDir();
        List<String> existing = new ArrayList<>();
        for (String name : dockerizeFiles.keySet()) {
            if (new File(dir, name).exists()) {
                existing.add(name);
            }
        }
        if (!existing.isEmpty() && !confirm(Bundle.DockerPanelTopComponent_confirmOverwrite(String.join(", ", existing)))) {
            return;
        }
        try {
            for (Map.Entry<String, String> e : dockerizeFiles.entrySet()) {
                java.nio.file.Path target = DockerRecipes.resolveInside(dir, e.getKey());
                Files.createDirectories(target.getParent()); // PHP ships docker/nginx.conf
                Files.writeString(target, e.getValue(), java.nio.charset.StandardCharsets.UTF_8);
            }
            status(Bundle.DockerPanelTopComponent_wroteFiles(String.join(", ", dockerizeFiles.keySet()), dir.getName()));
        } catch (Exception ex) {
            error(Bundle.DockerPanelTopComponent_writeFailed(ex.getMessage()));
        }
    }

    /** Long docker runs stream into the HARBOR output tab like any rack tool. */
    private void dockerizeCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        java.util.Collections.addAll(cmd, args);
        status("docker " + String.join(" ", args));
        // LIVERUNS-EXEMPT: fixed docker verbs against the daemon (v1.224.0
        // blessing); the things that run long are CONTAINERS, and the panel's
        // own Stop stops those — a wedged daemon is the v1.36.0 timeout's job
        org.nmox.studio.rack.engine.CommandExecutor.run("HARBOR", projectDir(), Map.of(),
                cmd, line -> {
                }, code -> {
                    status(code == 0 ? Bundle.DockerPanelTopComponent_dockerOk(args[0]) : Bundle.DockerPanelTopComponent_dockerFailed(args[0], String.valueOf(code)));
                    refreshAll();
                });
        org.nmox.studio.rack.engine.CommandExecutor.showOutput("HARBOR");
    }
    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }
}
