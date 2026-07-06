package org.nmox.studio.rack.docker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.net.URI;
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
        persistenceType = TopComponent.PERSISTENCE_NEVER
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
@org.openide.util.NbBundle.Messages("CTL_DockerPanelAction=Docker Panel")
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
    private final JLabel engineLabel = new JLabel("ENGINE: checking…");
    private final JLabel statusLabel = new JLabel(" ");
    private final javax.swing.Timer autoTimer = new javax.swing.Timer(15_000, e -> refreshAll());
    private final JCheckBox autoBox = new JCheckBox("Auto-refresh 15s", false);

    private final JPanel enginePanel = new JPanel(new GridBagLayout());
    private final DefaultTableModel containersModel = model("", "NAME", "IMAGE", "STATUS", "PORTS", "CPU", "MEM");
    private final JTable containersTable = table(containersModel);
    private final DefaultTableModel imagesModel = model("REFERENCE", "ID", "SIZE", "CREATED", "");
    private final JTable imagesTable = table(imagesModel);
    private final DefaultTableModel volumesModel = model("NAME", "DRIVER");
    private final JTable volumesTable = table(volumesModel);
    private final DefaultTableModel networksModel = model("NAME", "DRIVER", "SCOPE", "ID");
    private final JTable networksTable = table(networksModel);

    private List<ContainerInfo> containers = List.of();
    private List<ImageInfo> images = List.of();
    private List<VolumeInfo> volumes = List.of();
    private List<NetworkInfo> networks = List.of();

    private final JTextArea dockerfilePreview = preview();
    private final JTextArea ignorePreview = preview();
    private final JTextArea composePreview = preview();
    private final JLabel dockerizeInfo = new JLabel(" ");
    private Map<String, String> dockerizeFiles = Map.of();

    public DockerPanelTopComponent() {
        setName("Docker Panel");
        setToolTipText("Containers, images, volumes, networks, and one-click dockerize");
        setLayout(new BorderLayout());
        setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.addTab("Engine", wrap(enginePanel));
        tabs.addTab("Containers", buildContainersTab());
        tabs.addTab("Images", buildImagesTab());
        tabs.addTab("Volumes", buildVolumesTab());
        tabs.addTab("Networks", buildNetworksTab());
        tabs.addTab("Dockerize", buildDockerizeTab());
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
    }

    // ---- header ----

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        header.setBackground(BG);
        engineLabel.setForeground(TEXT);
        engineLabel.setFont(engineLabel.getFont().deriveFont(Font.BOLD));
        header.add(engineLabel);
        JButton refresh = new JButton("Refresh All");
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

    private static JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setBackground(BG);
        t.setForeground(TEXT);
        t.setGridColor(new Color(45, 46, 50));
        t.setRowHeight(24);
        t.setFont(MONO);
        t.getTableHeader().setBackground(new Color(35, 36, 40));
        t.getTableHeader().setForeground(DIM);
        t.setSelectionBackground(new Color(40, 70, 110));
        t.setSelectionForeground(Color.WHITE);
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                String s = String.valueOf(v);
                if (!sel) {
                    c.setForeground("●".equals(s) ? UP : "○".equals(s) ? DIM
                            : "◐".equals(s) ? new Color(255, 190, 60)
                            : "DANGLING".equals(s) ? new Color(255, 190, 60) : TEXT);
                }
                return c;
            }
        });
        return t;
    }

    private static JTextArea preview() {
        JTextArea a = new JTextArea();
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
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void status(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(s));
    }

    /** Runs a verb, surfaces failure, refreshes the panel after. */
    private void verbThenRefresh(java.util.concurrent.CompletableFuture<Result> f, String what) {
        status(what + "…");
        f.whenComplete((r, ex) -> SwingUtilities.invokeLater(() -> {
            if (r != null && !r.ok()) {
                error("Could not " + what + ": "
                        + (r.stderr().isBlank() ? "docker exited " + r.exit() : r.stderr().strip()));
            }
            refreshAll();
        }));
    }

    // ---- refresh ----

    private void refreshAll() {
        client.engineVersion().thenAccept(v -> SwingUtilities.invokeLater(() -> {
            if (v == null) {
                engineLabel.setText("ENGINE: DOWN — start Docker Desktop / colima, then Refresh");
                engineLabel.setForeground(DOWN);
            } else {
                engineLabel.setText("ENGINE: UP · v" + v);
                engineLabel.setForeground(UP);
            }
        }));
        refreshEngineTab();
        refreshContainers();
        refreshImages();
        refreshVolumesNetworks();
        status("refreshed " + java.time.LocalTime.now().withNano(0));
    }

    private void refreshEngineTab() {
        client.systemDf().thenAccept(rows -> SwingUtilities.invokeLater(() -> {
            enginePanel.removeAll();
            enginePanel.setBackground(BG);
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new java.awt.Insets(6, 12, 6, 12);
            g.anchor = GridBagConstraints.WEST;
            g.gridy = 0;
            for (String h : new String[]{"CATEGORY", "COUNT", "ACTIVE", "SIZE", "RECLAIMABLE", ""}) {
                g.gridx = enginePanel.getComponentCount() % 6;
                JLabel l = new JLabel(h);
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
                    enginePanel.add(btn("RECLAIM", () -> {
                        if (!volumes || confirm("Remove ALL unused volumes? Their data is gone for good.")) {
                            verbThenRefresh(client.prune(kind, false), "prune " + kind);
                        }
                    }), g);
                }
            }
            g.gridy++;
            g.gridx = 0;
            g.gridwidth = 6;
            enginePanel.add(btn("DEEP CLEAN — remove ALL unused images (not just dangling)", () -> {
                if (confirm("Remove every image not used by a container? Re-pulls may be slow.")) {
                    verbThenRefresh(client.prune("image", true), "deep image prune");
                }
            }), g);
            enginePanel.revalidate();
            enginePanel.repaint();
        }));
    }

    private static String pruneKind(String dfType) {
        String t = dfType.toLowerCase();
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
        JLabel l = new JLabel(s == null ? "" : s);
        l.setForeground(c);
        l.setFont(l.getFont().deriveFont(style));
        return l;
    }

    private boolean confirm(String message) {
        NotifyDescriptor d = new NotifyDescriptor.Confirmation(message, "Docker Panel",
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
        return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION;
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.ERROR_MESSAGE));
    }

    // ---- containers ----

    private Component buildContainersTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(wrap(containersTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn("Start", () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("start", c.id()), "start " + c.name()))));
        actions.add(btn("Stop", () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("stop", c.id()), "stop " + c.name()))));
        actions.add(btn("Restart", () -> eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("restart", c.id()), "restart " + c.name()))));
        actions.add(btn("Remove", () -> {
            if (confirm("Force-remove selected container(s)?")) {
                eachSelectedContainer(c -> verbThenRefresh(client.lifecycle("rm", c.id()), "remove " + c.name()));
            }
        }));
        actions.add(btn("Logs", () -> eachSelectedContainer(this::showLogs)));
        actions.add(btn("Inspect", () -> eachSelectedContainer(c ->
                client.inspect(c.id()).thenAccept(json -> textDialog("inspect " + c.name(), json)))));
        actions.add(btn("Open in Browser", () -> eachSelectedContainer(this::openPorts)));
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
                textDialog("logs " + c.name() + "  (last 500 lines)", text));
    }

    private void openPorts(ContainerInfo c) {
        for (Integer port : c.hostPorts()) {
            try {
                Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
                return;
            } catch (Exception ignored) {
            }
        }
        // three different truths, told apart: no ports at all, ports we could
        // not recognize a host mapping in, and ports the browser refused
        if (!c.hostPorts().isEmpty()) {
            status("could not open a browser for " + c.name() + " (port "
                    + c.hostPorts().get(0) + ")");
        } else if (c.ports() == null || c.ports().isBlank()) {
            status(c.name() + " publishes no host ports");
        } else {
            status("no recognizable host port in \"" + c.ports() + "\" for " + c.name());
        }
    }

    private void textDialog(String title, String text) {
        SwingUtilities.invokeLater(() -> {
            JTextArea area = preview();
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
        pullField.setToolTipText("image reference, e.g. nginx:alpine");
        actions.add(pullField);
        actions.add(btn("Pull", () -> {
            String ref = pullField.getText().trim();
            if (!ref.isEmpty()) {
                verbThenRefresh(client.pull(ref), "pull " + ref);
            }
        }));
        actions.add(btn("Run…", () -> eachSelectedImage(this::quickRun)));
        actions.add(btn("Tag…", () -> eachSelectedImage(img -> {
            NotifyDescriptor.InputLine line =
                    new NotifyDescriptor.InputLine("New tag for " + img.ref() + ":", "Tag Image");
            line.setInputText(img.ref());
            if (DialogDisplayer.getDefault().notify(line) == NotifyDescriptor.OK_OPTION
                    && !line.getInputText().isBlank()) {
                verbThenRefresh(client.tag(img.ref(), line.getInputText().trim()), "tag");
            }
        })));
        actions.add(btn("Layers", () -> eachSelectedImage(img ->
                client.history(img.ref()).thenAccept(h -> textDialog("layers " + img.ref(), h)))));
        actions.add(btn("Remove", () -> {
            if (confirm("Remove selected image(s)?")) {
                eachSelectedImage(img -> verbThenRefresh(client.removeImage(img.ref(), true), "rmi " + img.ref()));
            }
        }));
        actions.add(btn("Remove all dangling", () ->
                verbThenRefresh(client.prune("image", false), "prune dangling images")));
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
        JTextField ports = new JTextField("8080:80", 16);
        JTextField env = new JTextField(16);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new java.awt.Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = 0;
        g.gridx = 0;
        form.add(new JLabel("Name (blank = auto):"), g);
        g.gridx = 1;
        form.add(name, g);
        g.gridy = 1;
        g.gridx = 0;
        form.add(new JLabel("Ports host:container (space-separated):"), g);
        g.gridx = 1;
        form.add(ports, g);
        g.gridy = 2;
        g.gridx = 0;
        form.add(new JLabel("Env KEY=VAL (space-separated):"), g);
        g.gridx = 1;
        form.add(env, g);
        DialogDescriptor dd = new DialogDescriptor(form, "Run " + img.ref());
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
                                "Could not run " + img.ref() + ": " + r.stderr().strip()));
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
                    i.dangling() ? "<dangling>" : i.ref(),
                    i.id().length() > 12 ? i.id().substring(0, 12) : i.id(),
                    i.size(), i.created(), i.dangling() ? "DANGLING" : ""});
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
        actions.add(btn("Remove", () -> {
            if (confirm("Remove selected volume(s)? Their data is gone for good.")) {
                for (int row : volumesTable.getSelectedRows()) {
                    if (row < volumes.size()) {
                        verbThenRefresh(client.removeVolume(volumes.get(row).name()), "remove volume");
                    }
                }
            }
        }));
        actions.add(btn("Prune unused", () -> {
            if (confirm("Remove ALL unused volumes? Their data is gone for good.")) {
                verbThenRefresh(client.prune("volume", false), "prune volumes");
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
        actions.add(btn("Remove", () -> {
            for (int row : networksTable.getSelectedRows()) {
                if (row < networks.size()) {
                    verbThenRefresh(client.removeNetwork(networks.get(row).id()), "remove network");
                }
            }
        }));
        actions.add(btn("Prune unused", () ->
                verbThenRefresh(client.prune("network", false), "prune networks")));
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
        p.add(dockerizeInfo, BorderLayout.NORTH);

        JTabbedPane previews = new JTabbedPane();
        previews.addTab("Dockerfile", wrap(dockerfilePreview));
        previews.addTab(".dockerignore", wrap(ignorePreview));
        previews.addTab("compose.yaml", wrap(composePreview));
        p.add(previews, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setBackground(BG);
        actions.add(btn("Regenerate", this::regenerateDockerize));
        actions.add(btn("Write files into project", this::writeDockerizeFiles));
        actions.add(btn("Build image", () -> dockerizeCommand("build", "-t", imageName(), ".")));
        actions.add(btn("Run container", () -> {
            int port = currentPort();
            dockerizeCommand("run", "-d", "--name", imageName() + "-dev",
                    "-p", port + ":" + port, imageName());
        }));
        p.add(actions, BorderLayout.SOUTH);
        regenerateDockerize();
        return p;
    }

    private File projectDir() {
        return RackService.getDefault().getRack().getProjectDir();
    }

    private String imageName() {
        return projectDir().getName().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
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
        // apply the previews on the EDT.
        File dir = projectDir();
        DOCKERIZE_RP.post(() -> {
            ProjectInspector.ProjectKind kind = ProjectInspector.detectKind(dir);
            boolean statics = DockerizeGenerator.buildsStatic(dir);
            String image = dir.getName().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
            int port = DockerizeGenerator.defaultPort(kind, statics);
            Map<String, String> files = DockerizeGenerator.generate(kind, image, statics);
            SwingUtilities.invokeLater(() -> {
                dockerizeFiles = files;
                dockerizeInfo.setText("Project: " + dir.getName()
                        + "   ·   detected toolchain: " + kind
                        + (statics ? " (static bundle → nginx)" : "")
                        + "   ·   image: " + image + "   ·   port: " + port);
                dockerfilePreview.setText(files.getOrDefault("Dockerfile", ""));
                ignorePreview.setText(files.getOrDefault(".dockerignore", ""));
                composePreview.setText(files.getOrDefault("compose.yaml", ""));
            });
        });
    }

    private void writeDockerizeFiles() {
        File dir = projectDir();
        List<String> existing = new ArrayList<>();
        for (String name : dockerizeFiles.keySet()) {
            if (new File(dir, name).exists()) {
                existing.add(name);
            }
        }
        if (!existing.isEmpty() && !confirm("Overwrite existing " + String.join(", ", existing) + "?")) {
            return;
        }
        try {
            for (Map.Entry<String, String> e : dockerizeFiles.entrySet()) {
                java.nio.file.Path target = new File(dir, e.getKey()).toPath();
                Files.createDirectories(target.getParent()); // PHP ships docker/nginx.conf
                Files.writeString(target, e.getValue(), java.nio.charset.StandardCharsets.UTF_8);
            }
            status("wrote " + String.join(", ", dockerizeFiles.keySet()) + " into " + dir.getName());
        } catch (Exception ex) {
            error("Could not write the Docker files: " + ex.getMessage());
        }
    }

    /** Long docker runs stream into the HARBOR output tab like any rack tool. */
    private void dockerizeCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        java.util.Collections.addAll(cmd, args);
        status("docker " + String.join(" ", args));
        org.nmox.studio.rack.engine.CommandExecutor.run("HARBOR", projectDir(), Map.of(),
                cmd, line -> {
                }, code -> {
                    status("docker " + args[0] + (code == 0 ? " OK" : " failed [" + code + "]"));
                    refreshAll();
                });
        org.nmox.studio.rack.engine.CommandExecutor.showOutput("HARBOR");
    }
}
